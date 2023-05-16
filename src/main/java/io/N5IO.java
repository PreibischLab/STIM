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
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import data.STData;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.real.DoubleType;

public class N5IO extends SpatialDataIO {

	protected static final String _exprValuesPath = "/expressionValues";
	protected static final String _locationsPath = "/locations";
	protected static final String _annotationsGroup = "/annotations";


	public N5IO(final Supplier<N5Writer> writerSupplier, final ExecutorService service) {
		super(writerSupplier, service);
	}

	public N5IO(final Supplier<? extends N5Reader> readerSupplier, final Supplier<N5Writer> writerSupplier, final ExecutorService service) {
		super(readerSupplier, writerSupplier, service);
	}

	public N5IO(
			final Supplier<? extends N5Reader> readerSupplier,
			final Supplier<N5Writer> writerSupplier,
			final int vectorBlockSize,
			final int[] matrixBlockSize,
			final Compression compression,
			final ExecutorService service) {

		super(readerSupplier, writerSupplier, vectorBlockSize, matrixBlockSize, compression, service);
	}

	@Override
	protected String defaultLocationsPath() {
		return _locationsPath;
	}

	@Override
	protected String defaultExprValuesPath() {
		return _exprValuesPath;
	}

	@Override
	protected String defaultAnnotationsPath() {
		return _annotationsGroup;
	}

	@Override
	protected void writeHeader(N5Writer writer, STData data) throws IOException {
		writer.createGroup(defaultLocationsPath());
		writer.createGroup(defaultExprValuesPath());
		writer.createGroup(defaultAnnotationsPath());
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
	protected List<String> detectMetaData(N5Reader reader, String annotationsGroup) throws IOException {
		return Arrays.asList(reader.list(annotationsGroup));
	}

	@Override
	protected <T extends NativeType<T> & RealType<T>> RandomAccessibleInterval<T> readMetaData(N5Reader reader, String annotationsGroup, String label) throws IOException {
		return N5Utils.open(reader, reader.groupPath(annotationsGroup, label));
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
	protected void writeTransformation(N5Writer writer, AffineGet transform, String name) throws IOException {
		writer.setAttribute("/", name, transform.getRowPackedCopy());
	}

	@Override
	@SuppressWarnings("unchecked")
	protected void writeMetaData(N5Writer writer, String annotationsGroup, String label, RandomAccessibleInterval<? extends NativeType<?>> data) throws IOException {
		String datasetName = writer.groupPath(annotationsGroup, label);
		writer.createGroup(datasetName);
		try {
			N5Utils.save((RandomAccessibleInterval<IntType>) data, writer, datasetName, options1d.blockSize, options.compression, options.exec);
		} catch (InterruptedException | ExecutionException e) {
			throw new IOException("Could not write expression values.", e);
		}
	}
}
