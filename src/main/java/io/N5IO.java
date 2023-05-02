package io;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
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

	public static Compression defaultCompression = new GzipCompression( 3 );
	public static int[] defaultBlockSize = new int[] { 512, 512 };
	public static int defaultBlockLength = 1024;

	protected static String exprValuesPath = "/expressionValues";
	protected static String locationsPath = "/locations";
	protected static String annotationsGroup = "/annotations";

	public N5IO(final Supplier<N5Writer> writer)
	{
		this(writer, writer);
	}

	public N5IO(final Supplier<? extends N5Reader> reader, final Supplier<N5Writer> writer)
	{
		super(reader, writer);
	}


	@Override
	protected void writeHeader(N5Writer writer, STData data) throws IOException {
		writer.createGroup(locationsPath);
		writer.createGroup(exprValuesPath);
		writer.createGroup(annotationsGroup);
		writer.setAttribute("/", "dim", data.numDimensions());
		writer.setAttribute("/", "numLocations", data.numLocations());
		writer.setAttribute("/", "numGenes", data.numGenes());
	}

	@Override
	protected RandomAccessibleInterval<DoubleType> readLocations() throws IOException {
		return N5Utils.open(n5, locationsPath);
	}

	@Override
	protected RandomAccessibleInterval<DoubleType> readExpressionValues() throws IOException {
		return N5Utils.open(n5, exprValuesPath);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected List<String> readBarcodes() throws IOException {
		return n5.getAttribute("/", "barcodeList", List.class);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected List<String> readGeneNames() throws IOException {
		return n5.getAttribute("/", "geneList", List.class);
	}

	@Override
	protected <T extends NativeType<T> & RealType<T>> void readAndSetTransformation(AffineSet transform, String name) throws IOException {
		final Set<String> attributes = n5.listAttributes("/").keySet();
		if (attributes.contains(name))
			transform.set(n5.getAttribute("/", name, double[].class ) );
	}

	@Override
	protected List<String> detectMetaData() throws IOException {
		return Arrays.asList(n5.list(annotationsGroup));
	}

	@Override
	protected <T extends NativeType<T> & RealType<T>> RandomAccessibleInterval<T> readMetaData(String label) throws IOException {
		return N5Utils.open(n5, n5.groupPath(annotationsGroup, label));
	}

	@Override
	protected void writeLocations(N5Writer writer, RandomAccessibleInterval<DoubleType> locations) throws IOException {
		try {
			int[] blockSize = new int[]{options1d.blockSize[0], (int) locations.dimension(1)};
			N5Utils.save(locations, writer, locationsPath, blockSize, options.compression, options.exec);
		} catch (InterruptedException | ExecutionException e) {
			throw new SpatialDataIOException("Could not write locations.", e);
		}
	}

	@Override
	protected void writeExpressionValues(N5Writer writer, RandomAccessibleInterval<DoubleType> exprValues) throws IOException {
		try {
			N5Utils.save(exprValues, writer, exprValuesPath, options.blockSize, options.compression, options.exec);
		} catch (InterruptedException | ExecutionException e) {
			throw new SpatialDataIOException("Could not write expression values.", e);
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
	protected void writeMetaData(N5Writer writer, String label, RandomAccessibleInterval<? extends NativeType<?>> data) throws IOException {
		String datasetName = writer.groupPath(annotationsGroup, label);
		writer.createGroup(datasetName);
		try {
			N5Utils.save((RandomAccessibleInterval<IntType>) data, writer, datasetName, options1d.blockSize, options.compression, options.exec);
		} catch (InterruptedException | ExecutionException e) {
			throw new SpatialDataIOException("Could not write expression values.", e);
		}
	}
}
