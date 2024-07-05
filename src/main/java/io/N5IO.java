package io;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineSet;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import data.STData;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

public class N5IO extends SpatialDataIO {

	public N5IO(final Supplier<? extends N5Reader> ioSupplier, final String basePath, final boolean readOnly, final ExecutorService service) {
		super(ioSupplier, basePath, readOnly, service);
	}

	public N5IO(
			final Supplier<? extends N5Reader> ioSupplier,
			final String basePath,
			final boolean readOnly,
			final int vectorBlockSize,
			final int[] matrixBlockSize,
			final Compression compression,
			final ExecutorService service) {

		super(ioSupplier, basePath, readOnly, vectorBlockSize, matrixBlockSize, compression, service);
	}

	@Override
	public void setDataPaths(String locationPath, String exprValuePath, String annotationPath, String geneAnnotationPath) {
		this.locationPath = (locationPath == null) ? "/locations" : locationPath;
		this.exprValuePath = (exprValuePath == null) ? "/expressionValues" : exprValuePath;
		this.annotationPath = (annotationPath == null) ? "/annotations" : annotationPath;
		this.geneAnnotationPath = (geneAnnotationPath == null) ? "/geneAnnotations" : geneAnnotationPath;
	}

	@Override
	protected void writeHeader(N5Writer writer, STData data) throws IOException {
		writer.createGroup(locationPath);
		writer.createGroup(exprValuePath);
		writer.createGroup(annotationPath);
		writer.createGroup(geneAnnotationPath);
		writer.setAttribute("/", "dim", data.numDimensions());
		writer.setAttribute("/", "numLocations", data.numLocations());
		writer.setAttribute("/", "numGenes", data.numGenes());
	}

	@Override
	protected RandomAccessibleInterval<DoubleType> readLocations(N5Reader reader, String locationsPath) throws IOException {
		return N5Utils.open(reader, locationsPath);
	}

	@Override
	protected RandomAccessibleInterval<DoubleType> readExpressionValues(N5Reader reader, String exprValuesPath) throws IOException {
		return N5Utils.open(reader, exprValuesPath);
	}

	@Override
	protected List<Pair<String,Double>> readExpressionStd(N5Reader reader, String exprValuePath) throws IOException {
		// TODO: refactor and use the functions from the N5IO and others
		// TODO: this will be more or less efficient depending on the underlying data structure
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

	@Override
	@SuppressWarnings("unchecked")
	protected List<String> readBarcodes(N5Reader reader) throws IOException {
		return reader.getAttribute("/", "barcodeList", List.class);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected List<String> readGeneNames(N5Reader reader) throws IOException {
		return reader.getAttribute("/", "geneList", List.class);
	}

	@Override
	protected <T extends NativeType<T> & RealType<T>> void readAndSetTransformation(N5Reader reader, AffineSet transform, String name) throws IOException {
		final Set<String> attributes = reader.listAttributes("/").keySet();
		if (attributes.contains(name))
			transform.set(reader.getAttribute("/", name, double[].class ) );
	}

	@Override
	protected List<String> detectAnnotations(N5Reader reader, String annotationsGroup) throws IOException {
		return Arrays.asList(reader.list(annotationsGroup));
	}

	@Override
	protected List<String> detectGeneAnnotations(N5Reader reader, String geneAnnotationsGroup) throws IOException {
		return Arrays.asList(reader.list(geneAnnotationsGroup));
	}

	@Override
	protected <T extends NativeType<T> & RealType<T>> RandomAccessibleInterval<T> readAnnotations(N5Reader reader, String annotationsGroup, String label) throws IOException {
		return N5Utils.open(reader, reader.groupPath(annotationsGroup, label));
	}

	@Override
	protected <T extends NativeType<T> & RealType<T>> RandomAccessibleInterval<T> readGeneAnnotations(N5Reader reader, String geneAnnotationsGroup, String label) throws IOException {
		return N5Utils.open(reader, reader.groupPath(geneAnnotationsGroup, label));
	}

	@Override
	protected void writeLocations(N5Writer writer, RandomAccessibleInterval<DoubleType> locations, String locationsPath) throws IOException {
		try {
			int[] blockSize = new int[]{options1d.blockSize[0], (int) locations.dimension(1)};
			N5Utils.save(locations, writer, locationsPath, blockSize, options.compression, options.exec);
		} catch (InterruptedException | ExecutionException e) {
			throw new IOException("Could not write locations.", e);
		}
	}

	@Override
	protected void writeExpressionValues(N5Writer writer, RandomAccessibleInterval<DoubleType> exprValues, String exprValuesPath) throws IOException {
		try {
			N5Utils.save(exprValues, writer, exprValuesPath, options.blockSize, options.compression, options.exec);
		} catch (InterruptedException | ExecutionException e) {
			throw new IOException("Could not write expression values.", e);
		}
	}
	
	@Override
	protected void writeBarcodes(N5Writer writer, List<String> barcodes) throws IOException {
		writer.setAttribute("/", "barcodeList", barcodes);
	}
	
	@Override
	protected void writeGeneNames(N5Writer writer, List<String> geneNames) throws IOException {
		writer.setAttribute("/", "geneList", geneNames);
	}
	
	@Override
	public void writeTransformation(N5Writer writer, AffineGet transform, String name) throws IOException {
		writer.setAttribute("/", name, transform.getRowPackedCopy());
	}

	@Override
	@SuppressWarnings("unchecked")
	protected void writeAnnotations(N5Writer writer, String annotationsGroup, String label, RandomAccessibleInterval<? extends NativeType<?>> data) throws IOException {
		String datasetName = writer.groupPath(annotationsGroup, label);
		writer.createGroup(datasetName);
		try {
			N5Utils.save((RandomAccessibleInterval<IntType>) data, writer, datasetName, options1d.blockSize, options.compression, options.exec);
		} catch (InterruptedException | ExecutionException e) {
			throw new IOException("Could not write expression values.", e);
		}
	}
	
	@Override
	protected void writeGeneAnnotations(N5Writer writer, String geneAnnotationsGroup, String label, RandomAccessibleInterval<? extends NativeType<?>> data) throws IOException {
		String datasetName = writer.groupPath(geneAnnotationsGroup, label);
		writer.createGroup(datasetName);
		try {
			N5Utils.save((RandomAccessibleInterval<IntType>) data, writer, datasetName, options1d.blockSize, options.compression, options.exec);
		} catch (InterruptedException | ExecutionException e) {
			throw new IOException("Could not write gene stdev values.", e);
		}
	}
}
