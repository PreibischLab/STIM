package io;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

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
import io.AnnDataDetails.AnnDataFieldType;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
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
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import render.Render;


public class AnnDataIO extends SpatialDataIO {

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
		if (!N5HDF5Reader.class.isInstance(ioSupplier.get()))
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
		if (!AnnDataDetails.isValidAnnData(reader))
			System.out.println("Anndata file seems to be missing some metadata. Trying to read it anyways...");
		return super.readData();
	}

	@Override
	protected RandomAccessibleInterval<DoubleType> readLocations(N5Reader reader, String locationPath) throws IOException {
		// transpose locations, since AnnData stores them as columns
		RandomAccessibleInterval<? extends RealType<?>> locations = Views.permute(
				(RandomAccessibleInterval<? extends RealType<?>>) AnnDataDetails.readArray(reader, locationPath), 0, 1);
		return Converters.convert(locations, (i, o) -> o.set(i.getRealDouble()), new DoubleType());
	}

	@Override
	protected RandomAccessibleInterval<DoubleType> readExpressionValues(N5Reader reader, String exprValuePath) throws IOException {
		RandomAccessibleInterval<? extends RealType<?>> expressionVals = (RandomAccessibleInterval<? extends RealType<?>>) AnnDataDetails.readArray(reader, exprValuePath);
		return Converters.convert(expressionVals, (i, o) -> o.set(i.getRealDouble()), new DoubleType());
	}

	@Override
	protected List<Pair<String,Double>> readExpressionStd(N5Reader reader, String exprValuePath) throws IOException {
		// TODO: refactor and use the functions from the N5IO and others
		// TODO: this will be more or less efficient depending on the underlying data structure
		// final AnnDataFieldType type = AnnDataDetails.getFieldType(reader, path);

		// TODO: implement if it is sparse, else it is the same as the N5IO function
		// switch (type) {
		// 	case CSR_MATRIX:
        //         return AnnDataDetails.readArray(reader, exprValuePath);
        //     case CSC_MATRIX:
        //         return AnnDataDetails.readArray(reader, exprValuePath);
        //     default:
        //         throw new UnsupportedOperationException("This AnnData does not support sparse matrix");
		// }

		final AtomicInteger nextGene = new AtomicInteger();
		final List< Pair< String, Double > > exprStd = new ArrayList<>();
		final List<String> gene_names = this.readGeneNames(reader);

		RandomAccessibleInterval<DoubleType> allExprValues = readExpressionValues(reader);
		long[] exprDims = allExprValues.dimensionsAsLongArray();
		final double numLocations = exprDims[1];

		for ( int g = nextGene.getAndIncrement(); g < gene_names.size(); g = nextGene.getAndIncrement() )
		{
			final String gene = gene_names.get( g );
			final IterableInterval< DoubleType > exprValues = Views.flatIterable(Views.hyperSlice( allExprValues, 1, g ));
			final double[] exprValuesCopy = new double[ (int)exprValues.size() ];

			final Cursor< DoubleType > cursor = exprValues.localizingCursor();

			while ( cursor.hasNext() )
			{
				final DoubleType t = cursor.next();
				exprValuesCopy[ cursor.getIntPosition( 0 ) ] = t.get();
			}

			double sum = Arrays.stream(exprValuesCopy).sum();
			double sumOfSquares = Arrays.stream(exprValuesCopy).map(x -> x*x).sum();

			double avg = sum / numLocations;
			double variance = (sumOfSquares / numLocations) - (avg * avg);
			double stdev = Math.sqrt(variance);

			exprStd.add(new ValuePair<>(gene, stdev));
		}

		return exprStd;
	}



	protected <T extends NativeType<T> & RealType<T>> void readAndSetTransformation(N5Reader reader, AffineSet transform, String name) throws IOException {
		if (!reader.exists("/uns/" + name))
			return;

		RandomAccessibleInterval<T> trafoValues = AnnDataDetails.readArray(reader, "/uns/" + name);
		RandomAccess<T> ra = trafoValues.randomAccess();
		int n = (int) trafoValues.dimension(0);
		double[] convertedValues = new double[n];

		for (int k = 0; k < n; k++)
			convertedValues[k] = ra.setPositionAndGet(k).getRealDouble();
		transform.set(convertedValues);
	}

	@Override
	protected List<String> detectAnnotations(N5Reader reader, String annotationPath) throws IOException {
		return AnnDataDetails.getExistingDataFrameDatasets(reader, annotationPath);
	}

	@Override
	protected List<String> detectGeneAnnotations(N5Reader reader, String geneAnnotationPath) throws IOException {
		return AnnDataDetails.getExistingDataFrameDatasets(reader, geneAnnotationPath);
	}

	protected <T extends NativeType<T> & RealType<T>> RandomAccessibleInterval<T> readAnnotations(N5Reader reader, String annotationPath, String label) throws IOException {
		return AnnDataDetails.readFromDataFrame(reader, annotationPath, label);
	}

	protected <T extends NativeType<T> & RealType<T>> RandomAccessibleInterval<T> readGeneAnnotations(N5Reader reader, String geneAnnotationPath, String label) throws IOException {
		return AnnDataDetails.readFromDataFrame(reader, geneAnnotationPath, label);
	}

	@Override
	protected List<String> readBarcodes(N5Reader reader) throws IOException {
		return AnnDataDetails.readStringAnnotation(reader, "/obs/_index");
	}

	@Override
	protected List<String> readGeneNames(N5Reader reader) throws IOException {
		return AnnDataDetails.readStringAnnotation(reader, "/var/_index");
	}

	@Override
	protected void writeHeader(N5Writer writer, STData data) throws IOException {
		AnnDataDetails.writeEncoding(writer, "/", AnnDataFieldType.ANNDATA);
		AnnDataDetails.createMapping(writer, "/obsm");
		AnnDataDetails.createMapping(writer, "/uns");
	}

	@Override
	public void writeTransformation(N5Writer writer, AffineGet transform, String name) throws IOException {
		double[] trafoValues = transform.getRowPackedCopy();
		AnnDataDetails.writeArray(writer, "/uns/" + name, ArrayImgs.doubles(trafoValues, trafoValues.length), options1d);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected void writeAnnotations(N5Writer writer, String annotationPath, String label, RandomAccessibleInterval<? extends NativeType<?>> data) throws IOException {
		AnnDataDetails.addToDataFrame(writer, annotationPath, label, (RandomAccessibleInterval<IntType>) data, options1d);
	}
	
	@Override
	protected void writeGeneAnnotations(N5Writer writer, String annotationPath, String label, RandomAccessibleInterval<? extends NativeType<?>> data) throws IOException {
		AnnDataDetails.addToDataFrame(writer,  geneAnnotationPath, label, (RandomAccessibleInterval<DoubleType>) data, options1d);
	}

	@Override
	protected void writeBarcodes(N5Writer writer, List<String> barcodes) throws IOException {
		AnnDataDetails.createDataFrame(writer, "/obs", barcodes);
	}

	@Override
	protected void writeGeneNames(N5Writer writer, List<String> geneNames) throws IOException {
		AnnDataDetails.createDataFrame(writer, "/var", geneNames);
	}

	@Override
	protected void writeLocations(N5Writer writer, RandomAccessibleInterval<DoubleType> locations, String locationPath) throws IOException {
		AnnDataDetails.writeArray(writer, locationPath, Views.permute(locations, 0, 1), options);
	}

	@Override
	protected void writeExpressionValues(N5Writer writer, RandomAccessibleInterval<DoubleType> exprValues, String exprValuePath) throws IOException {
		AnnDataDetails.writeArray(writer, exprValuePath, exprValues, options, AnnDataFieldType.CSR_MATRIX);
	}
}
