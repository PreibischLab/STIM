package io;

import data.STData;
import data.STDataImgLib2;
import data.STDataStatistics;
import gui.STDataAssembly;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineSet;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Util;
import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.zarr.N5ZarrWriter;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public abstract class SpatialDataIO {

	protected final Supplier<? extends N5Reader> readerSupplier;
	protected final Supplier<N5Writer> writerSupplier;
	protected boolean readOnly;
	protected N5Options options;
	protected N5Options options1d;

	public SpatialDataIO(final Supplier<N5Writer> writerSupplier) {
		this(writerSupplier, writerSupplier);
	}

	public SpatialDataIO(final Supplier<N5Writer> writerSupplier, final int blockSize1D, final int[] blockSize, final Compression compression) {
		this(writerSupplier, writerSupplier, blockSize1D, blockSize, compression);
	}

	public SpatialDataIO(final Supplier<? extends N5Reader> readerSupplier, final Supplier<N5Writer> writerSupplier) {
		this(readerSupplier, writerSupplier, 512*512, new int[]{512, 512}, new GzipCompression(3));
	}

	public SpatialDataIO(
			final Supplier<? extends N5Reader> readerSupplier,
			final Supplier<N5Writer> writerSupplier,
			final int blockSize1D,
			final int[] blockSize,
			final Compression compression) {

		if (readerSupplier == null)
			throw new IllegalArgumentException("No N5 reader supplier given.");

		this.readerSupplier = readerSupplier;
		this.writerSupplier = writerSupplier;
		readOnly = (writerSupplier == null);

		options = new N5Options(blockSize, compression, null);
		options1d = new N5Options(new int[]{blockSize1D}, compression, null);
	}

	public STDataAssembly readData() throws IOException {
		long time = System.currentTimeMillis();
		System.out.print( "Reading spatial data ... " );

		N5Reader reader = readerSupplier.get();
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

		if (geneNames == null || geneNames.size() == 0 || geneNames.size() != numGenes)
			throw new SpatialDataException("Missing or wrong number of gene names.");

		if (barcodes == null || barcodes.size() == 0 || barcodes.size() != numLocations) {
			System.out.println( "Missing or wrong number of barcodes, setting empty Strings instead");
			barcodes = new ArrayList<>();
			for (int i = 0; i < numLocations; ++i)
				barcodes.add("");
		}

		final HashMap<String, Integer> geneLookup = new HashMap<>();
		for (int i = 0; i < geneNames.size(); ++i )
			geneLookup.put(geneNames.get(i), i);

		STData stData = new STDataImgLib2(locations, exprValues, geneNames, barcodes, geneLookup);

		AffineTransform intensityTransform = new AffineTransform(1);
		readAndSetTransformation(reader, intensityTransform, "intensity_transform");
		AffineTransform2D transform = new AffineTransform2D();
		readAndSetTransformation(reader, transform, "transform");

		for (final String annotationLabel : detectMetaData(reader))
			stData.getMetaData().put(annotationLabel, readMetaData(reader, annotationLabel));

		System.out.println("Loading took " + (System.currentTimeMillis() - time) + " ms.");
		System.out.println("Metadata:" +
				" dims=" + locationDims[1] +
				", numLocations=" + numLocations +
				", numGenes=" + numGenes +
				", size(locations)=" + Util.printCoordinates(locationDims) +
				", size(exprValues)=" + Util.printCoordinates(exprDims));

		return new STDataAssembly(stData, new STDataStatistics(stData), transform, intensityTransform);
	}

	public boolean ensureRunningExecutorService() {
		return ensureRunningExecutorService(Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() / 2)));
	}

	/**
	 * Ensure a running ExecutorService instance.
	 * @param exec ExecutorService to use if one is not already running
	 * @return true if ExecutorService was previously running, false otherwise
	 */
	public boolean ensureRunningExecutorService(ExecutorService exec) {
		final boolean previouslyRunning = (options.exec != null && !options.exec.isShutdown());
		if (!previouslyRunning) {
			options.exec = exec;
			options1d.exec = exec;
		}
		return previouslyRunning;
	}

	/**
	 * Shutdown ExecutorService instance.
	 * @return true if ExecutorService was previously running, false otherwise
	 */
	public boolean shutdownExecutorService() {
		final boolean previouslyRunning = (options.exec != null && !options.exec.isShutdown());
		if (previouslyRunning)
			options.exec.shutdown();
		options.exec = null;
		options1d.exec = null;
		return previouslyRunning;
	}

	protected abstract RandomAccessibleInterval<DoubleType> readLocations(N5Reader reader) throws IOException; // size: [numLocations x numDimensions]

	protected abstract RandomAccessibleInterval<DoubleType> readExpressionValues(N5Reader reader) throws IOException; // size: [numGenes x numLocations]

	protected abstract List<String> readBarcodes(N5Reader reader) throws IOException;

	protected abstract List<String> readGeneNames(N5Reader reader) throws IOException;

	protected abstract <T extends NativeType<T> & RealType<T>> void readAndSetTransformation(N5Reader reader, AffineSet transform, String name) throws IOException;

	protected abstract List<String> detectMetaData(N5Reader reader) throws IOException;

	protected abstract <T extends NativeType<T> & RealType<T>> RandomAccessibleInterval<T> readMetaData(N5Reader reader, String label) throws IOException;

	public void writeData(STDataAssembly data) throws IOException {
		if (readOnly)
			throw new IllegalStateException("Trying to write to read-only file.");

		N5Writer writer = writerSupplier.get();
		STData stData = data.data();

		System.out.print( "Saving spatial data ... " );
		long time = System.currentTimeMillis();
		final boolean previouslyRunning = ensureRunningExecutorService();

		writeHeader(writer, stData);
		writeBarcodes(writer, stData.getBarcodes());
		writeGeneNames(writer, stData.getGeneNames());

		writeExpressionValues(writer, stData.getAllExprValues());
		writeLocations(writer, stData.getLocations());
		writeTransformation(writer, data.transform(), "transform");
		writeTransformation(writer, data.intensityTransform(), "intensity_transform");

		updateStoredMetadata(data.data().getMetaData());

		if (!previouslyRunning)
			shutdownExecutorService();
		System.out.println( "Saving took " + ( System.currentTimeMillis() - time ) + " ms." );
	}

	public void updateStoredMetadata(Map<String, RandomAccessibleInterval<? extends NativeType<?>>> metadata) throws IOException {
		if (readOnly)
			throw new IllegalStateException("Trying to write to read-only file.");

		N5Writer writer = writerSupplier.get();
		List<String> existingMetadata = detectMetaData(writer);

		final boolean previouslyRunning = ensureRunningExecutorService();
		for (Entry<String, RandomAccessibleInterval<? extends NativeType<?>>> newEntry : metadata.entrySet()) {
			if (existingMetadata.contains(newEntry.getKey()))
				System.out.println("Existing metadata '" + newEntry.getKey() + "' was not updated.");
			else
				writeMetaData(writer, newEntry.getKey(),  newEntry.getValue());
		}

		if (!previouslyRunning)
			shutdownExecutorService();
	}

	protected abstract void writeHeader(N5Writer writer, STData data) throws IOException;

	protected abstract void writeLocations(N5Writer writer, RandomAccessibleInterval<DoubleType> locations) throws IOException;

	protected abstract void writeExpressionValues(N5Writer writer, RandomAccessibleInterval<DoubleType> exprValues) throws IOException;

	protected abstract void writeBarcodes(N5Writer writer, List<String> barcodes) throws IOException;

	protected abstract void writeGeneNames(N5Writer writer, List<String> geneNames) throws IOException;

	protected abstract void writeTransformation(N5Writer writer, AffineGet transform, String name) throws IOException;

	protected abstract void writeMetaData(N5Writer writer, String label, RandomAccessibleInterval<? extends NativeType<?>> data) throws IOException;

	public void updateTransformation(AffineGet transform, String name) throws IOException {
		if (readOnly)
			throw new IllegalStateException("Trying to modify a read-only file.");

		N5Writer writer = writerSupplier.get();
		final boolean previouslyRunning = ensureRunningExecutorService(Executors.newFixedThreadPool(1));
		writeTransformation(writer, transform, name);
		if (!previouslyRunning)
			shutdownExecutorService();
	}

	public static SpatialDataIO inferFromName(String path) throws IOException {
		Path absolutePath = Paths.get(path).toAbsolutePath();
		String fileName = absolutePath.getFileName().toString();
		String[] components = fileName.split("\\.");
		String extension = components[components.length-1];

		Supplier<N5Writer> backendSupplier;
		if (extension.startsWith("h5")) {
			backendSupplier = () -> {
				try {return new N5HDF5Writer(path);}
				catch (IOException e) {throw new SpatialDataException("Supplier cannot open '" + path + "'.", e);}
			};
		}
		else if (extension.startsWith("n5")) {
			backendSupplier = () -> {
				try {return new N5FSWriter(path);}
				catch (IOException e) {throw new SpatialDataException("Supplier cannot open '" + path + "'.", e);}
			};
		}
		else if (extension.startsWith("zarr")) {
			backendSupplier = () -> {
				try {return new N5ZarrWriter(path);}
				catch (IOException e) {throw new SpatialDataException("Supplier cannot open '" + path + "'.", e);}
			};
		}
		else {
			throw new UnsupportedOperationException("Cannot find N5 backend for extension'" + extension + "'.");
		}

		if (extension.endsWith("ad"))
			return new AnnDataIO(backendSupplier);
		else
			return new N5IO(backendSupplier);
	}

	static class N5Options {

		int[] blockSize;
		Compression compression;
		ExecutorService exec;

		public N5Options(int[] blockSize, Compression compression, ExecutorService exec) {
			this.blockSize = blockSize;
			this.compression = compression;
			this.exec = exec;
		}
	}
}
