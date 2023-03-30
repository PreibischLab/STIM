package io;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import com.google.gson.JsonElement;
import data.STData;
import filter.FilterFactory;
import gui.STDataExplorer;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineSet;
import net.imglib2.type.NativeType;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;

import gui.STDataAssembly;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import render.Render;

import static gui.RenderThread.max;
import static gui.RenderThread.maxRange;
import static gui.RenderThread.min;
import static gui.RenderThread.minRange;
import static io.AnnDataDetails.AnnDataFieldType;


public class AnnDataIO extends SpatialDataIO {

	protected static String locationPath = "/obsm/spatial";

	public AnnDataIO(String path, N5Reader reader) {
		super(path, reader);

		if (!(n5 instanceof N5HDF5Reader))
			throw new IllegalArgumentException("IO for AnnData currently only supports hdf5.");
	}

	public static void main( String[] args ) throws IOException
	{
		final String path = System.getProperty("user.dir") + "/data/human-lymph-node.h5ad";

		SpatialDataIO stio = new AnnDataIO(path, new N5HDF5Reader(path));
		STDataAssembly data = stio.readData();
		String gene = "IGKC";

		Interval interval = data.data().getRenderInterval();
		System.out.println( "render interval: " + Util.printInterval( interval ) );
		System.out.println( "exp value base matrix: " + Util.printInterval( data.data().getAllExprValues() ) );
		System.out.println("Available processors: " + Runtime.getRuntime().availableProcessors());

		List<FilterFactory<DoubleType, DoubleType>> filterFactories = new ArrayList<>();
		BdvOptions options = BdvOptions.options().is2D().numRenderingThreads(Runtime.getRuntime().availableProcessors());

		final RealRandomAccessible<DoubleType> renderRRA = Render.getRealRandomAccessible( data, gene, 0.2, filterFactories );
		BdvStackSource<DoubleType> bdv = BdvFunctions.show(renderRRA, interval, "", options);
//		RandomAccessibleInterval<DoubleType> exprValues = data.data().getAllExprValues();
//		BdvStackSource<DoubleType> bdv = BdvFunctions.show(exprValues, "", options);
		bdv.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.SINGLE );
		bdv.setDisplayRangeBounds( minRange, maxRange );
		bdv.setDisplayRange( min, max );

		new STDataExplorer(Arrays.asList(data));
	}

	@Override
	public STDataAssembly readData() throws IOException {
		if (!AnnDataDetails.isValidAnnData(n5))
			throw new IOException(path + " is not a valid AnnData file.");

		STDataAssembly stDataAssembly = super.readData();

		if (containsCellTypes()) {
			Img<IntType> celltypeIds = getCelltypeIds(n5);
			stDataAssembly.data().getMetaData().put("celltype", celltypeIds);
			System.out.println("Loading '" + "/obs/cell_type" + "' as label 'celltype'.");
		}

		return stDataAssembly;
	}

	@Override
	protected RandomAccessibleInterval<DoubleType> readLocations() {
		// transpose locations, since AnnData stores them as columns
		RandomAccessibleInterval<? extends RealType<?>> locations = Views.permute(
				(RandomAccessibleInterval<? extends RealType<?>>) AnnDataDetails.readArray(n5, locationPath), 0, 1);
		return Converters.convert(locations, (i, o) -> o.set(i.getRealDouble()), new DoubleType());
	}

	@Override
	protected RandomAccessibleInterval<DoubleType> readExpressionValues() {
		RandomAccessibleInterval<? extends RealType<?>> expressionVals = (RandomAccessibleInterval<? extends RealType<?>>) AnnDataDetails.readArray(n5, "/X");
		return Converters.convert(expressionVals, (i, o) -> o.set(i.getRealDouble()), new DoubleType());
	}

	@Override
	public Boolean containsCellTypes() {
		return n5.exists("/obs/cell_type");
	}

	public static ArrayImg<IntType, IntArray> getCelltypeIds(final N5Reader reader) {
		final List<String> cellTypes = AnnDataDetails.readStringAnnotation(reader, "/obs/cell_type");
		final HashMap<String, Integer> typeToIdMap = new HashMap<>();
		final int[] celltypeIds = new int[cellTypes.size()];

		// for categorical arrays, this is redundant -> use codes directly?
		for (int k = 0; k < cellTypes.size(); ++k) {
			final String type = cellTypes.get(k);
			Integer id = typeToIdMap.computeIfAbsent(type, k1 -> typeToIdMap.size() + 1);
			celltypeIds[k] = id;
		}

		return ArrayImgs.ints(celltypeIds, celltypeIds.length);
	}

	protected <T extends NativeType<T> & RealType<T>> void readAndSetTransformation(AffineSet transform, String name) {
		RandomAccessibleInterval<T> trafoValues = AnnDataDetails.readArray(n5, "/uns/" + name);
		RandomAccess<T> ra = trafoValues.randomAccess();
		int n = (int) trafoValues.dimension(0);
		double[] convertedValues = new double[n];

		for (int k = 0; k < n; k++)
			convertedValues[k] = ra.setPositionAndGet(k).getRealDouble();
		transform.set(convertedValues);
	}

	@Override
	public JsonElement readMetaData() {
		return null;
	}

	@Override
	protected List<String> readBarcodes() {
		return AnnDataDetails.readStringAnnotation(n5, "/obs/_index");
	}

	@Override
	protected List<String> readGeneNames() {
		return AnnDataDetails.readStringAnnotation(n5, "/var/_index");
	}

	@Override
	protected List<String> readCellTypes() {
		return null;
	}

	public void writeData(STDataAssembly data) throws IOException {
		N5Writer writer = getN5asWriter();
		STData stData = data.data();

		System.out.print( "Saving AnnData '" + path + "' ... " );
		long time = System.currentTimeMillis();

		AnnDataDetails.writeEncoding(writer, "/", AnnDataFieldType.ANNDATA);

		AnnDataDetails.writeArray(writer, "/X", stData.getAllExprValues(), options, AnnDataFieldType.CSR_MATRIX);

		AnnDataDetails.createDataFrame(writer, "/obs", stData.getBarcodes());
		AnnDataDetails.createDataFrame(writer, "/var", stData.getGeneNames());

		AnnDataDetails.createMapping(writer, "/obsm");
		AnnDataDetails.writeArray(writer, locationPath, Views.permute(stData.getLocations(), 0, 1), options);

		AnnDataDetails.createMapping(writer, "/uns");
		writeTransformation(data.transform(), "transform");
		writeTransformation(data.intensityTransform(), "intensity_transform");

		System.out.println( "took " + ( System.currentTimeMillis() - time ) + " ms." );
	}

	protected void writeTransformation(AffineGet transform, String name) {
		N5Writer writer = getN5asWriter();
		double[] trafoValues = transform.getRowPackedCopy();
		AnnDataDetails.writeArray(writer, "/uns/" + name, ArrayImgs.doubles(trafoValues, trafoValues.length), options1d);
	}

	@Override
	public void writeMetaData(JsonElement metaData) {

	}

	@Override
	protected void writeBarcodes(List<String> barcodes) {

	}

	@Override
	protected void writeGeneNames(List<String> geneNames) {

	}

	@Override
	protected void writeCellTypes(List<String> cellTypes) {

	}

	@Override
	protected void writeLocations(RandomAccessibleInterval<DoubleType> locations) {

	}

	@Override
	protected void writeExpressionValues(RandomAccessibleInterval<DoubleType> exprValues) {

	}
}
