package io;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;
import org.janelia.n5anndata.io.AnnDataUtils;
import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import data.STData;
import filter.FilterFactory;
import gui.STDataAssembly;
import gui.STDataExplorer;
import org.janelia.n5anndata.io.AnnDataFieldType;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converters;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineSet;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import render.Render;
import util.LoggerUtil;


public class AnnDataIO extends SpatialDataIO {

	private static final Logger logger = LoggerUtil.getLogger();

	public AnnDataIO(final Supplier<? extends N5Reader> ioSupplier, final String basePath, final boolean readOnly, final ExecutorService service) {
		super(ioSupplier, basePath, readOnly, service);
	}

	public AnnDataIO(
			final Supplier<? extends N5Reader> ioSupplier,
			final String basePath,
			final boolean readOnly,
			final int vectorBlockSize,
			final int[] matrixBlockSize,
			final Compression compression,
			final ExecutorService service) {

		super(ioSupplier, basePath, readOnly, vectorBlockSize, matrixBlockSize, compression, service);

		// TODO: remove this check once the issue is fixed
		if (!(ioSupplier.get() instanceof N5HDF5Reader))
			throw new IllegalArgumentException("IO for AnnData currently only supports hdf5.");
	}

	@Override
	public void setDataPaths(String locationPath, String exprValuePath, String annotationPath, String geneAnnotationPath) {
		this.locationPath = (locationPath == null) ? "/obsm/spatial" : locationPath;
		this.exprValuePath = (exprValuePath == null) ? "/X" : exprValuePath;
		this.annotationPath = (annotationPath == null) ? "/obs" : annotationPath;
		this.geneAnnotationPath = (geneAnnotationPath == null) ? "/var" : geneAnnotationPath;
	}

	public static void main( String[] args ) throws IOException
	{
		final String path = System.getProperty("user.dir") + "/data/human-lymph-node.h5ad";

		ExecutorService service = Executors.newFixedThreadPool(8);
		SpatialDataIO stio = SpatialDataIO.open(path, service);
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
		bdv.setDisplayRangeBounds( 0, 200 );
		bdv.setDisplayRange( 0, 5 );

		new STDataExplorer(Collections.singletonList(data), path, Collections.singletonList(""));
	}

	@Override
	public STDataAssembly readData() throws IOException {
		N5Reader reader = ioSupplier.get();
		if (!AnnDataUtils.isValidAnnData(reader))
			logger.warn("Anndata file seems to be missing some metadata. Trying to read it anyways...");
		return super.readData();
	}

	@Override
	protected RandomAccessibleInterval<DoubleType> readLocations(N5Reader reader, String locationPath) {
		// transpose locations, since AnnData stores them as columns
		RandomAccessibleInterval<? extends RealType<?>> locations = Views.permute(
				(RandomAccessibleInterval<? extends RealType<?>>) AnnDataUtils.readNumericalArray(reader, locationPath), 0, 1);
		return Converters.convert(locations, (i, o) -> o.set(i.getRealDouble()), new DoubleType());
	}

	@Override
	protected RandomAccessibleInterval<DoubleType> readExpressionValues(N5Reader reader, String exprValuePath) {
		// the cast here is necessary, there is a compiler error without it
		RandomAccessibleInterval<? extends RealType<?>> expressionVals = (RandomAccessibleInterval<? extends RealType<?>>) AnnDataUtils.readNumericalArray(reader, exprValuePath);
		return Converters.convert(expressionVals, (i, o) -> o.set(i.getRealDouble()), new DoubleType());
	}

	protected <T extends NativeType<T> & RealType<T>> void readAndSetTransformation(N5Reader reader, AffineSet transform, String name) {
		if (!reader.exists("/uns/" + name))
			return;

		RandomAccessibleInterval<T> trafoValues = AnnDataUtils.readNumericalArray(reader, "/uns/" + name);
		RandomAccess<T> ra = trafoValues.randomAccess();
		int n = (int) trafoValues.dimension(0);
		double[] convertedValues = new double[n];

		for (int k = 0; k < n; k++)
			convertedValues[k] = ra.setPositionAndGet(k).getRealDouble();
		transform.set(convertedValues);
	}

	@Override
	protected List<String> detectAnnotations(N5Reader reader, String annotationPath) {
		return new ArrayList<>(AnnDataUtils.getDataFrameDatasetNames(reader, annotationPath));
	}

	@Override
	protected List<String> detectGeneAnnotations(N5Reader reader, String geneAnnotationPath) {
		return new ArrayList<>(AnnDataUtils.getDataFrameDatasetNames(reader, geneAnnotationPath));
	}

	protected <T extends NativeType<T> & RealType<T>> RandomAccessibleInterval<T> readAnnotations(N5Reader reader, String annotationPath, String label) {
		return AnnDataUtils.readNumericalArray(reader, annotationPath + "/" + label);
	}

	protected <T extends NativeType<T> & RealType<T>> RandomAccessibleInterval<T> readGeneAnnotations(N5Reader reader, String geneAnnotationPath, String label) {
		return AnnDataUtils.readNumericalArray(reader, geneAnnotationPath + "/" + label);
	}

	@Override
	protected List<String> readBarcodes(N5Reader reader) {
		final String index_path = reader.getAttribute("obs", "_index", String.class);
		return AnnDataUtils.readStringArray(reader, "/obs/" + index_path);
	}

	@Override
	protected List<String> readGeneNames(N5Reader reader) {
		final String index_path = reader.getAttribute("var", "_index", String.class);
		return AnnDataUtils.readStringArray(reader, "/var/" + index_path);
	}

	@Override
	protected void initializeDataset(N5Writer writer, STData data) {
		AnnDataUtils.initializeAnnData(data.getBarcodes(), data.getGeneNames(), writer, options);
	}

	@Override
	public void writeTransformation(N5Writer writer, AffineGet transform, String name) throws IOException {
		double[] trafoValues = transform.getRowPackedCopy();
		AnnDataUtils.writeNumericalArray(ArrayImgs.doubles(trafoValues, trafoValues.length), writer, "/uns/" + name, options1d, AnnDataFieldType.DENSE_ARRAY);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected void writeAnnotations(N5Writer writer, String annotationPath, String label, RandomAccessibleInterval<? extends NativeType<?>> data) throws IOException {
		AnnDataUtils.writeNumericalArray((RandomAccessibleInterval<IntType>) data, writer, annotationPath + "/" + label, options1d, AnnDataFieldType.DENSE_ARRAY);
	}
	
	@Override
	@SuppressWarnings("unchecked")
	protected void writeGeneAnnotations(N5Writer writer, String geneAnnotationPath, String label, RandomAccessibleInterval<? extends NativeType<?>> data) throws IOException {
		AnnDataUtils.writeNumericalArray((RandomAccessibleInterval<DoubleType>) data, writer,  geneAnnotationPath + "/" + label, options1d, AnnDataFieldType.DENSE_ARRAY);
	}

	@Override
	protected void writeLocations(N5Writer writer, RandomAccessibleInterval<DoubleType> locations, String locationPath) throws IOException {
		AnnDataUtils.writeNumericalArray(Views.permute(locations, 0, 1), writer, locationPath, options, AnnDataFieldType.DENSE_ARRAY);
	}

	@Override
	protected void writeExpressionValues(N5Writer writer, RandomAccessibleInterval<DoubleType> exprValues, String exprValuePath) throws IOException {
		AnnDataUtils.writeNumericalArray(exprValues, writer, exprValuePath, options, AnnDataFieldType.CSR_MATRIX);
	}
}
