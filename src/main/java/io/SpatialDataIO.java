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

	protected final Supplier<? extends N5Reader> n5supplier;
	protected final Supplier<N5Writer> n5wsupplier;
	//protected boolean readOnly;
	protected N5Options options;
	protected N5Options options1d;

	public SpatialDataIO(final Supplier<? extends N5Reader> reader, final Supplier<N5Writer> writer) {
		if (reader == null)
			throw new IllegalArgumentException("No N5 reader / writer given.");

		this.n5supplier = reader;
		this.n5wsupplier = writer; // can be null
		//readOnly = N5Writer.class.isInstance( reader ); //!(reader instanceof N5Writer);

		options = new N5Options(
				new int[]{512, 512},
				new GzipCompression(3),
				null);
		options1d = new N5Options(
				new int[]{512*512},
				new GzipCompression(3),
				null);
	}

	public STDataAssembly readData() throws IOException {
		long time = System.currentTimeMillis();
		System.out.print( "Reading spatial data ... " );

		RandomAccessibleInterval<DoubleType> locations = readLocations();
		RandomAccessibleInterval<DoubleType> exprValues = readExpressionValues();

		long[] locationDims = locations.dimensionsAsLongArray();
		long[] exprDims = exprValues.dimensionsAsLongArray();
		long numGenes = exprDims[0];
		long numLocations = exprDims[1];

		List<String> geneNames = readGeneNames();
		List<String> barcodes = readBarcodes();

		if (locations.dimension(0) != numLocations)
			throw new SpatialDataIOException("Inconsistent number of locations in data arrays.");

		if (geneNames == null || geneNames.size() == 0 || geneNames.size() != numGenes)
			throw new SpatialDataIOException("Missing or wrong number of gene names.");

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
		readAndSetTransformation(intensityTransform, "intensity_transform");
		AffineTransform2D transform = new AffineTransform2D();
		readAndSetTransformation(transform, "transform");

		for (final String annotationLabel : detectMetaData())
			stData.getMetaData().put(annotationLabel, readMetaData(annotationLabel));

		System.out.println("Loading took " + (System.currentTimeMillis() - time) + " ms.");
		System.out.println("Metadata:" +
				" dims=" + locationDims[1] +
				", numLocations=" + numLocations +
				", numGenes=" + numGenes +
				", size(locations)=" + Util.printCoordinates(locationDims) +
				", size(exprValues)=" + Util.printCoordinates(exprDims));

		return new STDataAssembly(stData, new STDataStatistics(stData), transform, intensityTransform);
	}

	public void setBlockSize(int... blockSize) {
		if (blockSize.length != 2)
			throw new IllegalArgumentException("Block size must be two dimensional.");
		options.blockSize = blockSize;
		options1d.blockSize = new int[]{blockSize[0] * blockSize[1]};
	}

	public void setCompression(Compression compression) {
		options.compression = compression;
		options1d.compression = compression;
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

	protected abstract RandomAccessibleInterval<DoubleType> readLocations() throws IOException; // size: [numLocations x numDimensions]

	protected abstract RandomAccessibleInterval<DoubleType> readExpressionValues() throws IOException; // size: [numGenes x numLocations]

	protected abstract List<String> readBarcodes() throws IOException;

	protected abstract List<String> readGeneNames() throws IOException;

	protected abstract <T extends NativeType<T> & RealType<T>> void readAndSetTransformation(AffineSet transform, String name) throws IOException;

	protected abstract List<String> detectMetaData() throws IOException;

	protected abstract <T extends NativeType<T> & RealType<T>> RandomAccessibleInterval<T> readMetaData(String label) throws IOException;

	public void writeData(STDataAssembly data) throws IOException {
		//if (readOnly)
		if (n5w == null)
			throw new SpatialDataIOException("Trying to write to read-only file.");

		N5Writer writer = (N5Writer) n5;
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
		//if (readOnly)
		if (n5w == null)
			throw new SpatialDataIOException("Trying to write to read-only file.");

		N5Writer writer = (N5Writer) n5;
		List<String> existingMetadata = detectMetaData();

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
		//if (readOnly)
		if (n5w == null)
			throw new SpatialDataIOException("Trying to modify a read-only file.");

		N5Writer writer = (N5Writer) n5;
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

		switch (extension) {
			case "h5":
				return new N5IO(new N5HDF5Writer(path));
			case "n5":
				return new N5IO(new N5FSWriter(path));
			case "zarr":
				return new N5IO(new N5ZarrWriter(path));
			case "h5ad":
				return new AnnDataIO(new N5HDF5Writer(path));
			case "n5ad":
				return new AnnDataIO(new N5FSWriter(path));
			case "zarrad":
				return new AnnDataIO(new N5ZarrWriter(path));
			default:
				throw new SpatialDataIOException("Cannot determine file type for extension " + extension + ".");
		}
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
