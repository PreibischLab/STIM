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


public abstract class SpatialDataIO {

	public static String transformFieldName = "transform";

	// expose internal methods
	public static class InternalMethods
	{
		private final SpatialDataIO instance;

		public InternalMethods(final SpatialDataIO data) {
			this.instance = data;
		}


		public RandomAccessibleInterval<DoubleType> readLocations(N5Reader reader) throws IOException {
			return instance.readLocations(reader);
		}

		public RandomAccessibleInterval<DoubleType> readExpressionValues(N5Reader reader) throws IOException {
			return instance.readExpressionValues(reader);
		}

		public List<String> readBarcodes(N5Reader reader) throws IOException {
			return instance.readBarcodes(reader);
		}

		public List<String> readGeneNames(N5Reader reader) throws IOException {
			return instance.readGeneNames(reader);
		}

		public <T extends NativeType<T> & RealType<T>> void readAndSetTransformation(N5Reader reader, AffineSet transform, String name) throws IOException {
			instance.readAndSetTransformation(reader, transform, name);
		}

		public List<String> detectAnnotations(N5Reader reader) throws IOException {
			return instance.detectAnnotations(reader);
		}

		public <T extends NativeType<T> & RealType<T>> RandomAccessibleInterval<T> readAnnotations(N5Reader reader, String label) throws IOException {
			return instance.readAnnotations(reader, label);
		}

		public void writeHeader(N5Writer writer, STData data) throws IOException {
			instance.writeHeader(writer, data);
		}

		public void writeLocations(N5Writer writer, RandomAccessibleInterval<DoubleType> locations) throws IOException {
			instance.writeLocations(writer, locations);
		}

		public void writeExpressionValues(N5Writer writer, RandomAccessibleInterval<DoubleType> exprValues) throws IOException {
			instance.writeExpressionValues(writer, exprValues);
		}

		public void writeBarcodes(N5Writer writer, List<String> barcodes) throws IOException {
			instance.writeBarcodes(writer, barcodes);
		}

		public void writeGeneNames(N5Writer writer, List<String> geneNames) throws IOException {
			instance.writeGeneNames(writer, geneNames);
		}

		public void writeTransformation(N5Writer writer, AffineGet transform, String name) throws IOException {
			instance.writeTransformation(writer, transform, name);
		}

		public void writeAnnotations(N5Writer writer, String label, RandomAccessibleInterval<? extends NativeType<?>> data) throws IOException {
			instance.writeAnnotations(writer, label, data);
		}
	}

	/**
	 * Create a new InternalMethods instance that grants access to private methods of SpatialDataIO.
	 *
	 * @return {@link InternalMethods} linking to the calling SpatialDataIO instance
	 */
	public InternalMethods internalMethods() { return new InternalMethods( this ); }

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

	/**
	 * Create a new SpatialDataIO instance.
	 *
	 * @param ioSupplier {@link Supplier} for N5Reader or N5Writer
	 * @param readOnly whether to open the file in read-only mode
	 * @param service {@link ExecutorService} for parallel IO
	 */
	public SpatialDataIO(final Supplier<? extends N5Reader> ioSupplier, final boolean readOnly, ExecutorService service) {
		this(ioSupplier, readOnly, 1024, new int[]{512, 512}, new GzipCompression(3), service);
	}

	/**
	 * Create a new SpatialDataIO instance.
	 *
	 * @param ioSupplier {@link Supplier} for N5Reader or N5Writer
	 * @param readOnly whether to open the file in read-only mode
	 * @param vectorBlockSize block size for vector data
	 * @param matrixBlockSize block size for matrix data
	 * @param compression compression type
	 * @param service {@link ExecutorService} for parallel IO
	 */
	public SpatialDataIO(
			final Supplier<? extends N5Reader> ioSupplier,
			final boolean readOnly,
			final int vectorBlockSize,
			final int[] matrixBlockSize,
			final Compression compression,
			final ExecutorService service) {

		if (!readOnly && !(ioSupplier.get() instanceof N5Writer))
			throw new IllegalArgumentException("Supplier for read-write must return N5Writer");

		this.ioSupplier = ioSupplier;
		this.readOnly = readOnly;

		this.options = new N5Options(matrixBlockSize, compression, service);
		this.options1d = new N5Options(new int[]{vectorBlockSize}, compression, service);
		setDataPaths(null, null, null);
	}

	/**
	 * Set paths to locations, expression values, and barcodes. Implementing classes should replace null arguments by
	 * default values.
	 *
	 * @param locationPath path to locations
	 * @param exprValuePath path to expression values
	 * @param annotationPath path to annotations
	 */
	public abstract void setDataPaths(String locationPath, String exprValuePath, String annotationPath);

	/**
	 * Read data (locations, expression values, barcodes, gene names, and transformations) from the given instance.
	 *
	 * @return {@link STDataAssembly} containing the data
	 * @throws IOException
	 */
	public STDataAssembly readData() throws IOException {
		long time = System.currentTimeMillis();
		System.out.print( "Reading spatial data ... " );

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
			System.out.println( "Missing or wrong number of barcodes, setting empty Strings instead");
			barcodes = new ArrayList<>();
			for (int i = 0; i < numLocations; ++i)
				barcodes.add("");
		}

		final HashMap<String, Integer> geneLookup = new HashMap<>();
		for (int i = 0; i < geneNames.size(); ++i )
			geneLookup.put(geneNames.get(i), i);

		STData stData = new STDataImgLib2(locations, exprValues, geneNames, barcodes, geneLookup);

		//AffineTransform intensityTransform = new AffineTransform(1);
		//readAndSetTransformation(reader, intensityTransform, "intensity_transform");
		AffineTransform2D transform = new AffineTransform2D();
		readAndSetTransformation(reader, transform, transformFieldName);

		for (final String annotationLabel : detectAnnotations(reader))
			stData.getAnnotations().put(annotationLabel, readAnnotations(reader, annotationLabel));

		System.out.println("Loading took " + (System.currentTimeMillis() - time) + " ms.");
		System.out.println("Metadata:" +
				" dims=" + locationDims[1] +
				", numLocations=" + numLocations +
				", numGenes=" + numGenes +
				", size(locations)=" + Util.printCoordinates(locationDims) +
				", size(exprValues)=" + Util.printCoordinates(exprDims));

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

	protected abstract List<String> detectAnnotations(N5Reader reader, String annotationsPath) throws IOException;

	protected <T extends NativeType<T> & RealType<T>> RandomAccessibleInterval<T> readAnnotations(N5Reader reader, String label) throws IOException {
		return readAnnotations(reader, annotationPath, label);
	}

	protected abstract <T extends NativeType<T> & RealType<T>> RandomAccessibleInterval<T> readAnnotations(N5Reader reader, String annotationsPath, String label) throws IOException;

	/**
	 * Write data (locations, expression values, barcodes, gene names, and transformations) for the given instance.
	 *
	 * @param data {@link STDataAssembly} containing the data
	 * @throws IOException
	 */
	public void writeData(STDataAssembly data) throws IOException {
		if (readOnly)
			throw new IllegalStateException("Trying to write to read-only file.");

		N5Writer writer = (N5Writer) ioSupplier.get();
		STData stData = data.data();

		System.out.print( "Saving spatial data ... " );
		long time = System.currentTimeMillis();

		writeHeader(writer, stData);
		writeBarcodes(writer, stData.getBarcodes());
		writeGeneNames(writer, stData.getGeneNames());

		writeExpressionValues(writer, stData.getAllExprValues());
		writeLocations(writer, stData.getLocations());
		writeTransformation(writer, data.transform(), transformFieldName);
		//writeTransformation(writer, data.intensityTransform(), "intensity_transform");

		updateStoredAnnotations(stData.getAnnotations());

		System.out.println( "Saving took " + ( System.currentTimeMillis() - time ) + " ms." );
	}

	/**
	 * Write the given annotations to the underlying file; existing annotations are not updated.
	 *
	 * @param metadata map of annotations
	 * @throws IOException
	 */
	public void updateStoredAnnotations(Map<String, RandomAccessibleInterval<? extends NativeType<?>>> metadata) throws IOException {
		if (readOnly)
			throw new IllegalStateException("Trying to write to read-only file.");

		N5Writer writer = (N5Writer) ioSupplier.get();
		List<String> existingAnnotations = detectAnnotations(writer);

		for (Entry<String, RandomAccessibleInterval<? extends NativeType<?>>> newEntry : metadata.entrySet()) {
			if (existingAnnotations.contains(newEntry.getKey()))
				System.out.println("Existing metadata '" + newEntry.getKey() + "' was not updated.");
			else
				writeAnnotations(writer, newEntry.getKey(), newEntry.getValue());
		}
	}

	protected abstract void writeHeader(N5Writer writer, STData data) throws IOException;

	protected void writeLocations(N5Writer writer, RandomAccessibleInterval<DoubleType> locations) throws IOException {
		writeLocations(writer, locations, locationPath);
	}

	protected abstract void writeLocations(N5Writer writer, RandomAccessibleInterval<DoubleType> locations, String locationsPath) throws IOException;

	protected void writeExpressionValues(N5Writer writer, RandomAccessibleInterval<DoubleType> exprValues) throws IOException {
		writeExpressionValues(writer, exprValues, exprValuePath);
	}

	protected abstract void writeExpressionValues(N5Writer writer, RandomAccessibleInterval<DoubleType> exprValues, String exprValuesPath) throws IOException;

	protected abstract void writeBarcodes(N5Writer writer, List<String> barcodes) throws IOException;

	protected abstract void writeGeneNames(N5Writer writer, List<String> geneNames) throws IOException;

	// public to be able to only write the transformation to a dataset
	public abstract void writeTransformation(N5Writer writer, AffineGet transform, String name) throws IOException;

	protected void writeAnnotations(N5Writer writer, String label, RandomAccessibleInterval<? extends NativeType<?>> data) throws IOException {
		writeAnnotations(writer, annotationPath, label, data);
	}

	protected abstract void writeAnnotations(N5Writer writer, String annotationsPath, String label, RandomAccessibleInterval<? extends NativeType<?>> data) throws IOException;

	/**
	 * Write the given transformation to the underlying file; existing transformations with the same name will be overwritten.
	 *
	 * @param transform the new transformation
	 * @param name the name of the transformation
	 * @throws IOException
	 */
	public void updateTransformation(AffineGet transform, String name) throws IOException {
		if (readOnly)
			throw new IllegalStateException("Trying to modify a read-only file.");

		N5Writer writer = (N5Writer) ioSupplier.get();
		writeTransformation(writer, transform, name);
	}

	/**
	 * Open file with write access solely based on the file name.
	 *
	 * @param path the path to the file
	 * @param service {@link ExecutorService} to use for parallel IO
	 * @throws IOException
	 */
	public static SpatialDataIO open(final String path, final ExecutorService service) throws IOException {
		Path absolutePath = Paths.get(path).toAbsolutePath();
		String fileName = absolutePath.getFileName().toString();
		String[] components = fileName.split("\\.");
		String extension = components[components.length-1];

		Supplier<N5Writer> writerSupplier;
		if (extension.startsWith("h5")) {
			writerSupplier = () -> {
				try {return new N5HDF5Writer(path);}
				catch (IOException e) {throw new SpatialDataException("Supplier cannot open '" + path + "'.", e);}
			};
		} else if (extension.startsWith("n5")) {
			writerSupplier = () -> {
				try {return new N5FSWriter(path);}
				catch (IOException e) {throw new SpatialDataException("Supplier cannot open '" + path + "'.", e);}
			};
		} else if (extension.startsWith("zarr")) {
			writerSupplier = () -> {
				try {return new N5ZarrWriter(path);}
				catch (IOException e) {throw new SpatialDataException("Supplier cannot open '" + path + "'.", e);}
			};
		} else {
			throw new UnsupportedOperationException("Cannot find N5 backend for extension'" + extension + "'.");
		}

		if (extension.endsWith("ad"))
			return new AnnDataIO(writerSupplier, false, service);
		else
			return new N5IO(writerSupplier, false, service);
	}

	/**
	 * Open file read-only solely based on the file name.
	 *
	 * @param path the path to the file
	 * @param service {@link ExecutorService} to use for parallel IO
	 * @throws IOException
	 */
	public static SpatialDataIO openReadOnly(final String path, final ExecutorService service) throws IOException {
		Path absolutePath = Paths.get(path).toAbsolutePath();
		String fileName = absolutePath.getFileName().toString();
		String[] components = fileName.split("\\.");
		String extension = components[components.length-1];

		Supplier<N5Reader> readerSupplier;
		if (extension.startsWith("h5")) {
			readerSupplier = () -> {
				try {return new N5HDF5Reader(path);}
				catch (IOException e) {throw new SpatialDataException("Supplier cannot open '" + path + "'.", e);}
			};
		} else if (extension.startsWith("n5")) {
			readerSupplier = () -> {
				try {return new N5FSReader(path);}
				catch (IOException e) {throw new SpatialDataException("Supplier cannot open '" + path + "'.", e);}
			};
		} else if (extension.startsWith("zarr")) {
			readerSupplier = () -> {
				try {return new N5ZarrReader(path);}
				catch (IOException e) {throw new SpatialDataException("Supplier cannot open '" + path + "'.", e);}
			};
		} else {
			throw new UnsupportedOperationException("Cannot find N5 backend for extension'" + extension + "'.");
		}

		if (extension.endsWith("ad"))
			return new AnnDataIO(readerSupplier, true, service);
		else
			return new N5IO(readerSupplier, true, service);
	}

	// TODO: refactor when pulling out AnnData stuff
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
