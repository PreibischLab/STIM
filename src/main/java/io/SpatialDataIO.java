package io;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import org.janelia.n5anndata.io.N5Options;
import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.zarr.N5ZarrReader;
import org.janelia.saalfeldlab.n5.zarr.N5ZarrWriter;

import data.STData;
import data.STDataImgLib2;
import data.STDataStatistics;
import gui.STDataAssembly;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineSet;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Util;
import org.apache.logging.log4j.Logger;
import util.LoggerUtil;


public abstract class SpatialDataIO {
	
	private static final Logger logger = LoggerUtil.getLogger();
	public static String transformFieldName = "transform";

	protected final Supplier<? extends N5Reader> ioSupplier;

	/**
	 * @return the ioSupplier to be able to for example only write the transformation to a dataset
	 */
	public Supplier<? extends N5Reader> ioSupplier() { return ioSupplier; }

	protected boolean readOnly;
	protected N5Options options;
	protected N5Options options1d;
	protected String locationPath;
	protected String exprValuePath;
	protected String annotationPath;
	protected String geneAnnotationPath;
	protected String path;

	public String getPath() { return path; }

	/**
	 * Create a new SpatialDataIO instance.
	 *
	 * @param ioSupplier {@link Supplier} for N5Reader or N5Writer
	 * @param basePath the path to the dataset that we load (just for info)
	 * @param readOnly whether to open the file in read-only mode
	 * @param service {@link ExecutorService} for parallel IO
	 */
	public SpatialDataIO(
			final Supplier<? extends N5Reader> ioSupplier,
			final String basePath,
			final boolean readOnly,
			final ExecutorService service )
	{
		this(ioSupplier, basePath, readOnly, 128*128, new int[]{512, 512}, new GzipCompression(3), service);
	}

	/**
	 * Create a new SpatialDataIO instance.
	 *
	 * @param ioSupplier {@link Supplier} for N5Reader or N5Writer
	 * @param basePath the path to the dataset that we load (just for info)
	 * @param readOnly whether to open the file in read-only mode
	 * @param vectorBlockSize block size for vector data
	 * @param matrixBlockSize block size for matrix data
	 * @param compression compression type
	 * @param service {@link ExecutorService} for parallel IO
	 */
	public SpatialDataIO(
			final Supplier<? extends N5Reader> ioSupplier,
			final String basePath,
			final boolean readOnly,
			final int vectorBlockSize,
			final int[] matrixBlockSize,
			final Compression compression,
			final ExecutorService service) {

		if (!readOnly && !(ioSupplier.get() instanceof N5Writer))
			throw new IllegalArgumentException("Supplier for read-write must return N5Writer");

		this.path = basePath;

		this.ioSupplier = ioSupplier;
		this.readOnly = readOnly;

		this.options = new N5Options(matrixBlockSize, compression, service);
		this.options1d = new N5Options(new int[]{vectorBlockSize}, compression, service);
		setDataPaths(null, null, null, null);
	}

	/**
	 * Set paths to locations, expression values, and barcodes. Implementing classes should replace null arguments by
	 * default values.
	 *
	 * @param locationPath path to locations
	 * @param exprValuePath path to expression values
	 * @param annotationPath path to annotations
	 */
	public abstract void setDataPaths(String locationPath, String exprValuePath, String annotationPath, String geneAnnotationPath);

	/**
	 * Read data (locations, expression values, barcodes, gene names, and transformations) from the given instance.
	 *
	 * @return {@link STDataAssembly} containing the data
	 */
	public STDataAssembly readData() throws IOException {
		long time = System.currentTimeMillis();
		logger.debug( "Reading spatial data ... " );

		N5Reader reader = ioSupplier.get();
		RandomAccessibleInterval<DoubleType> locations = readLocations(reader);
		RandomAccessibleInterval<DoubleType> exprValues = readExpressionValues(reader);

		long[] locationDims = locations.dimensionsAsLongArray();
		long[] exprDims = exprValues.dimensionsAsLongArray();
		long numGenes = exprDims[0];
		long numLocations = exprDims[1];

		List<String> geneNames = readGeneNames(reader);
		List<String> barcodes = readBarcodes(reader);

		if (locations.dimension(0) != numLocations)
			throw new SpatialDataException("Inconsistent number of locations in data arrays.");

		if (geneNames == null || geneNames.isEmpty() || geneNames.size() != numGenes)
			throw new SpatialDataException("Missing or wrong number of gene names.");

		if (barcodes == null || barcodes.isEmpty() || barcodes.size() != numLocations) {
			logger.debug( "Missing or wrong number of barcodes, setting empty Strings instead");
			barcodes = new ArrayList<>();
			for (int i = 0; i < numLocations; ++i)
				barcodes.add("");
		}

		final HashMap<String, Integer> geneLookup = new HashMap<>();
		for (int i = 0; i < geneNames.size(); ++i )
			geneLookup.put(geneNames.get(i), i);

		STData stData = new STDataImgLib2(locations, exprValues, geneNames, barcodes, geneLookup);

		AffineTransform2D transform = new AffineTransform2D();
		readAndSetTransformation(reader, transform, transformFieldName);

		for (final String annotationLabel : detectAnnotations(reader))
			stData.getAnnotations().put(annotationLabel, readAnnotations(reader, annotationLabel));

		for (final String geneAnnotationLabel : detectGeneAnnotations(reader))
			stData.getGeneAnnotations().put(geneAnnotationLabel, readGeneAnnotations(reader, geneAnnotationLabel));

		logger.debug("Loading took {} ms.", System.currentTimeMillis() - time);
		logger.debug("Metadata: dims={}, numLocations={}, numGenes={}, size(locations)={}, size(exprValues)={}",
					 locationDims[1], numLocations, numGenes, Util.printCoordinates(locationDims), Util.printCoordinates(exprDims));

		return new STDataAssembly(stData, new STDataStatistics(stData), transform);
	}

	protected RandomAccessibleInterval<DoubleType> readLocations(N5Reader reader) throws IOException {
		return readLocations(reader, locationPath);
	}

	protected abstract RandomAccessibleInterval<DoubleType> readLocations(N5Reader reader, String locationPath) throws IOException; // size: [numLocations x numDimensions]

	protected RandomAccessibleInterval<DoubleType> readExpressionValues(N5Reader reader) throws IOException {
		return readExpressionValues(reader, exprValuePath);
	}

	protected abstract RandomAccessibleInterval<DoubleType> readExpressionValues(N5Reader reader, String exprValuesPath) throws IOException; // size: [numGenes x numLocations]

	protected abstract List<String> readBarcodes(N5Reader reader) throws IOException;

	protected abstract List<String> readGeneNames(N5Reader reader) throws IOException;

	protected abstract <T extends NativeType<T> & RealType<T>> void readAndSetTransformation(N5Reader reader, AffineSet transform, String name) throws IOException;

	protected List<String> detectAnnotations(N5Reader reader) throws IOException {
		return detectAnnotations(reader, annotationPath);
	}

	protected List<String> detectGeneAnnotations(N5Reader reader) throws IOException {
		return detectGeneAnnotations(reader, geneAnnotationPath);
	}

	protected abstract List<String> detectAnnotations(N5Reader reader, String annotationsPath) throws IOException;

	protected abstract List<String> detectGeneAnnotations(N5Reader reader, String geneAnnotationsPath) throws IOException;

	protected <T extends NativeType<T> & RealType<T>> RandomAccessibleInterval<T> readAnnotations(N5Reader reader, String label) throws IOException {
		return readAnnotations(reader, annotationPath, label);
	}

	protected <T extends NativeType<T> & RealType<T>> RandomAccessibleInterval<T> readGeneAnnotations(N5Reader reader, String label) throws IOException {
		return readAnnotations(reader, geneAnnotationPath, label);
	}

	protected abstract <T extends NativeType<T> & RealType<T>> RandomAccessibleInterval<T> readAnnotations(N5Reader reader, String annotationsPath, String label) throws IOException;

	protected abstract <T extends NativeType<T> & RealType<T>> RandomAccessibleInterval<T> readGeneAnnotations(N5Reader reader, String annotationsPath, String label) throws IOException;

	/**
	 * Write data (locations, expression values, barcodes, gene names, and transformations) for the given instance.
	 *
	 * @param data {@link STDataAssembly} containing the data
	 */
	public void writeData(STDataAssembly data) throws IOException {
		if (readOnly)
			throw new IllegalStateException("Trying to write to read-only file.");

		N5Writer writer = (N5Writer) ioSupplier.get();
		STData stData = data.data();

		logger.debug( "Saving spatial data ... " );
		long time = System.currentTimeMillis();

		initializeDataset(writer, stData);

		writeExpressionValues(writer, stData.getAllExprValues());
		writeLocations(writer, stData.getLocations());
		updateTransformation(writer, data.transform(), transformFieldName);
		//writeTransformation(writer, data.intensityTransform(), "intensity_transform");

		updateStoredAnnotations(stData.getAnnotations());

		logger.debug("Saving took {} ms.", System.currentTimeMillis() - time);
	}

	/**
	 * Write the given annotations to the underlying file; existing annotations are not updated.
	 *
	 * @param metadata map of annotations
	 */
	public void updateStoredAnnotations(Map<String, RandomAccessibleInterval<? extends NativeType<?>>> metadata) throws IOException {
		if (readOnly)
			throw new IllegalStateException("Trying to write to read-only file.");

		N5Writer writer = (N5Writer) ioSupplier.get();
		List<String> existingAnnotations = detectAnnotations(writer);

		for (Entry<String, RandomAccessibleInterval<? extends NativeType<?>>> newEntry : metadata.entrySet()) {
			if (existingAnnotations.contains(newEntry.getKey()))
				logger.warn("Metadata '{}' already exists. Skip writing.", newEntry.getKey());
			else
				writeAnnotations(writer, newEntry.getKey(), newEntry.getValue());
		}
	}

	/**
	 * Write the given annotations to the underlying file; existing annotations are not updated.
	 *
	 * @param metadata map of annotations
	 */
	public void updateStoredGeneAnnotations(Map<String, RandomAccessibleInterval<? extends NativeType<?>>> metadata) throws IOException {
		if (readOnly)
			throw new IllegalStateException("Trying to write to read-only file.");

		N5Writer writer = (N5Writer) ioSupplier.get();
		List<String> existingGeneAnnotations = detectGeneAnnotations(writer);

		for (Entry<String, RandomAccessibleInterval<? extends NativeType<?>>> newEntry : metadata.entrySet()) {
			if (existingGeneAnnotations.contains(newEntry.getKey()))
				logger.warn("Metadata '{}' already exists. Skip writing.", newEntry.getKey());
			else
				writeGeneAnnotations(writer, newEntry.getKey(), newEntry.getValue());
		}
	}

	protected abstract void initializeDataset(N5Writer writer, STData data) throws IOException;

	protected void writeLocations(N5Writer writer, RandomAccessibleInterval<DoubleType> locations) throws IOException {
		writeLocations(writer, locations, locationPath);
	}

	protected abstract void writeLocations(N5Writer writer, RandomAccessibleInterval<DoubleType> locations, String locationsPath) throws IOException;

	protected void writeExpressionValues(N5Writer writer, RandomAccessibleInterval<DoubleType> exprValues) throws IOException {
		writeExpressionValues(writer, exprValues, exprValuePath);
	}

	protected abstract void writeExpressionValues(N5Writer writer, RandomAccessibleInterval<DoubleType> exprValues, String exprValuesPath) throws IOException;

	// public to be able to only write the transformation to a dataset
	public abstract void updateTransformation(N5Writer writer, AffineGet transform, String name) throws IOException;

	protected void writeAnnotations(N5Writer writer, String label, RandomAccessibleInterval<? extends NativeType<?>> data) throws IOException {
		writeAnnotations(writer, annotationPath, label, data);
	}

	protected void writeGeneAnnotations(N5Writer writer, String label, RandomAccessibleInterval<? extends NativeType<?>> data) throws IOException {
		writeGeneAnnotations(writer, geneAnnotationPath, label, data);
	}

	protected abstract void writeAnnotations(N5Writer writer, String annotationsPath, String label, RandomAccessibleInterval<? extends NativeType<?>> data) throws IOException;

	protected abstract void writeGeneAnnotations(N5Writer writer, String annotationsPath, String label, RandomAccessibleInterval<? extends NativeType<?>> data) throws IOException;

	/**
	 * Write the given transformation to the underlying file; existing transformations with the same name will be overwritten.
	 *
	 * @param transform the new transformation
	 * @param name the name of the transformation
	 * @throws IOException if trying to write to a read-only file
	 */
	public void updateTransformation(AffineGet transform, String name) throws IOException {
		if (readOnly)
			throw new IllegalStateException("Trying to modify a read-only file.");

		N5Writer writer = (N5Writer) ioSupplier.get();
		updateTransformation(writer, transform, name);
	}

	/**
	 * Open file with write access solely based on the file name.
	 *
	 * @param path the path to the file
	 * @param service {@link ExecutorService} to use for parallel IO
	 */
	public static SpatialDataIO open(final String path, final ExecutorService service) throws IOException {
		Path absolutePath = Paths.get(path).toAbsolutePath();
		String fileName = absolutePath.getFileName().toString();
		String[] components = fileName.split("\\.");
		String extension = components[components.length-1];

		Supplier<N5Writer> writerSupplier;
		if (extension.startsWith("h5")) {
			writerSupplier = () -> new N5HDF5Writer(path);
		} else if (extension.startsWith("n5")) {
			writerSupplier = () -> new N5FSWriter(path);
		} else if (extension.startsWith("zarr")) {
			writerSupplier = () -> new N5ZarrWriter(path);
		} else {
			throw new UnsupportedOperationException("Cannot find N5 backend for extension'" + extension + "'.");
		}

		if (extension.endsWith("ad"))
			return new AnnDataIO(writerSupplier, path, false, service);
		else
			return new N5IO(writerSupplier, path, false, service);
	}

	/**
	 * Open file read-only solely based on the file name.
	 *
	 * @param path the path to the file
	 * @param service {@link ExecutorService} to use for parallel IO
	 */
	public static SpatialDataIO openReadOnly(final String path, final ExecutorService service) {
		Path absolutePath = Paths.get(path).toAbsolutePath();
		String fileName = absolutePath.getFileName().toString();
		String[] components = fileName.split("\\.");
		String extension = components[components.length-1];

		Supplier<N5Reader> readerSupplier;
		if (extension.startsWith("h5")) {
			readerSupplier = () -> new N5HDF5Reader(path);
		} else if (extension.startsWith("n5")) {
			readerSupplier = () -> new N5FSReader(path);
		} else if (extension.startsWith("zarr")) {
			readerSupplier = () -> new N5ZarrReader(path);
		} else {
			throw new UnsupportedOperationException("Cannot find N5 backend for extension'" + extension + "'.");
		}

		if (extension.endsWith("ad"))
			return new AnnDataIO(readerSupplier, path, true, service);
		else
			return new N5IO(readerSupplier, path, true, service);
	}
}
