package io;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
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
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.real.DoubleType;

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
	protected void initializeDataset(N5Writer writer, STData data) {
		writer.createGroup(locationPath);
		writer.createGroup(exprValuePath);
		writer.createGroup(annotationPath);
		writer.createGroup(geneAnnotationPath);
		writer.setAttribute("/", "dim", data.numDimensions());
		writer.setAttribute("/", "numLocations", data.numLocations());
		writer.setAttribute("/", "numGenes", data.numGenes());
		writeBarcodes(writer, data.getBarcodes());
		writeGeneNames(writer, data.getGeneNames());
	}

	@Override
	protected RandomAccessibleInterval<DoubleType> readLocations(N5Reader reader, String locationsPath) {
		return N5Utils.open(reader, locationsPath);
	}

	@Override
	protected RandomAccessibleInterval<DoubleType> readExpressionValues(N5Reader reader, String exprValuesPath) {
		return N5Utils.open(reader, exprValuesPath);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected List<String> readBarcodes(N5Reader reader) {
		return reader.getAttribute("/", "barcodeList", List.class);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected List<String> readGeneNames(N5Reader reader) {
		return reader.getAttribute("/", "geneList", List.class);
	}

	@Override
	protected void readAndSetTransformation(N5Reader reader, AffineSet transform, String name) {
		final Set<String> attributes = reader.listAttributes("/").keySet();
		if (attributes.contains(name))
			transform.set(reader.getAttribute("/", name, double[].class ) );
	}

	@Override
	protected List<String> detectAnnotations(N5Reader reader, String annotationsGroup) {
		return Arrays.asList(reader.list(annotationsGroup));
	}

	@Override
	protected List<String> detectGeneAnnotations(N5Reader reader, String geneAnnotationsGroup) {
		return Arrays.asList(reader.list(geneAnnotationsGroup));
	}

	@Override
	protected <T extends NativeType<T> & RealType<T>> RandomAccessibleInterval<T> readAnnotations(N5Reader reader, String annotationsGroup, String label) {
		return N5Utils.open(reader, reader.groupPath(annotationsGroup, label));
	}

	@Override
	protected <T extends NativeType<T> & RealType<T>> RandomAccessibleInterval<T> readGeneAnnotations(N5Reader reader, String geneAnnotationsGroup, String label) {
		return N5Utils.open(reader, reader.groupPath(geneAnnotationsGroup, label));
	}

	@Override
	protected void writeLocations(N5Writer writer, RandomAccessibleInterval<DoubleType> locations, String locationsPath) throws IOException {
		try {
			int[] blockSize = new int[]{options1d.blockSize()[0], (int) locations.dimension(1)};
			N5Utils.save(locations, writer, locationsPath, blockSize, options.compression(), options.executorService());
		} catch (InterruptedException | ExecutionException e) {
			throw new IOException("Could not write locations.", e);
		}
	}

	@Override
	protected void writeExpressionValues(N5Writer writer, RandomAccessibleInterval<DoubleType> exprValues, String exprValuesPath) throws IOException {
		try {
			N5Utils.save(exprValues, writer, exprValuesPath, options.blockSize(), options.compression(), options.executorService());
		} catch (InterruptedException | ExecutionException e) {
			throw new IOException("Could not write expression values.", e);
		}
	}
	
	protected void writeBarcodes(N5Writer writer, List<String> barcodes) {
		writer.setAttribute("/", "barcodeList", barcodes);
	}
	
	protected void writeGeneNames(N5Writer writer, List<String> geneNames) {
		writer.setAttribute("/", "geneList", geneNames);
	}
	
	@Override
	public void writeTransformation(N5Writer writer, AffineGet transform, String name) {
		writer.setAttribute("/", name, transform.getRowPackedCopy());
	}

	@Override
	@SuppressWarnings("unchecked")
	protected void writeAnnotations(N5Writer writer, String annotationsGroup, String label, RandomAccessibleInterval<? extends NativeType<?>> data) throws IOException {
		String datasetName = writer.groupPath(annotationsGroup, label);
		writer.createGroup(datasetName);
		try {
			N5Utils.save((RandomAccessibleInterval<IntType>) data, writer, datasetName, options1d.blockSize(), options.compression(), options.executorService());
		} catch (InterruptedException | ExecutionException e) {
			throw new IOException("Could not write expression values.", e);
		}
	}
	
	@Override
	@SuppressWarnings("unchecked")
	protected void writeGeneAnnotations(N5Writer writer, String geneAnnotationsGroup, String label, RandomAccessibleInterval<? extends NativeType<?>> data) throws IOException {
		String datasetName = writer.groupPath(geneAnnotationsGroup, label);
		writer.createGroup(datasetName);
		try {
			N5Utils.save((RandomAccessibleInterval<IntType>) data, writer, datasetName, options1d.blockSize(), options.compression(), options.executorService());
		} catch (InterruptedException | ExecutionException e) {
			throw new IOException("Could not write gene stdev values.", e);
		}
	}
}
