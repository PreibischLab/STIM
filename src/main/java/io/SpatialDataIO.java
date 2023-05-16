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
import java.util.function.Supplier;


public abstract class SpatialDataIO {

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

		public List<String> detectMetaData(N5Reader reader) throws IOException {
			return instance.detectMetaData(reader);
		}

		public <T extends NativeType<T> & RealType<T>> RandomAccessibleInterval<T> readMetaData(N5Reader reader, String label) throws IOException {
			return instance.readMetaData(reader, label);
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

		public void writeMetaData(N5Writer writer, String label, RandomAccessibleInterval<? extends NativeType<?>> data) throws IOException {
			instance.writeMetaData(writer, label, data);
		}
	}

	public InternalMethods internalMethods() { return new InternalMethods( this ); }

	protected final Supplier<? extends N5Reader> readerSupplier;
	protected final Supplier<N5Writer> writerSupplier;
	protected boolean readOnly;
	protected N5Options options;
	protected N5Options options1d;
	protected StorageSpec storageSpec;

	public SpatialDataIO(final Supplier<N5Writer> writerSupplier, final ExecutorService service) {
		this(writerSupplier, writerSupplier, service);
	}

	// TODO: should be smaller for HDF5?
	public SpatialDataIO(final Supplier<? extends N5Reader> readerSupplier, final Supplier<N5Writer> writerSupplier, final ExecutorService service) {
		this(readerSupplier, writerSupplier, 1024, new int[]{512, 512}, new GzipCompression(3), service, null, null, null);
	}

	// TODO: this has a lot of parameters -> encapsulate subsets, or use builder?
	public SpatialDataIO(
			final Supplier<? extends N5Reader> readerSupplier,
			final Supplier<N5Writer> writerSupplier,
			final int vectorBlockSize,
			final int[] matrixBlockSize,
			final Compression compression,
			final ExecutorService service,
			final String locationPath,
			final String exprValuePath,
			final String annotationPath) {

		if (readerSupplier == null)
			throw new IllegalArgumentException("No N5 reader supplier given.");

		this.readerSupplier = readerSupplier;
		this.writerSupplier = writerSupplier;
		readOnly = (writerSupplier == null);

		options = new N5Options(matrixBlockSize, compression, service);
		options1d = new N5Options(new int[]{vectorBlockSize}, compression, service);
		storageSpec = createStorageSpecOrDefault(locationPath, exprValuePath, annotationPath);
	}

	// implementing classes should replace null arguments by default values
	protected abstract StorageSpec createStorageSpecOrDefault(String locationPath, String exprValuePath, String annotation);

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

	protected RandomAccessibleInterval<DoubleType> readLocations(N5Reader reader) throws IOException {
		return readLocations(reader, storageSpec.locationPath);
	}

	protected abstract RandomAccessibleInterval<DoubleType> readLocations(N5Reader reader, String locationPath) throws IOException; // size: [numLocations x numDimensions]

	protected RandomAccessibleInterval<DoubleType> readExpressionValues(N5Reader reader) throws IOException {
		return readExpressionValues(reader, storageSpec.exprValuePath);
	}

	protected abstract RandomAccessibleInterval<DoubleType> readExpressionValues(N5Reader reader, String exprValuesPath) throws IOException; // size: [numGenes x numLocations]

	protected abstract List<String> readBarcodes(N5Reader reader) throws IOException;

	protected abstract List<String> readGeneNames(N5Reader reader) throws IOException;

	protected abstract <T extends NativeType<T> & RealType<T>> void readAndSetTransformation(N5Reader reader, AffineSet transform, String name) throws IOException;

	protected List<String> detectMetaData(N5Reader reader) throws IOException {
		return detectMetaData(reader, storageSpec.annoationPath);
	}

	protected abstract List<String> detectMetaData(N5Reader reader, String annotationsPath) throws IOException;

	protected <T extends NativeType<T> & RealType<T>> RandomAccessibleInterval<T> readMetaData(N5Reader reader, String label) throws IOException {
		return readMetaData(reader, storageSpec.annoationPath, label);
	}

	protected abstract <T extends NativeType<T> & RealType<T>> RandomAccessibleInterval<T> readMetaData(N5Reader reader, String annotationsPath, String label) throws IOException;

	public void writeData(STDataAssembly data) throws IOException {
		if (readOnly)
			throw new IllegalStateException("Trying to write to read-only file.");

		N5Writer writer = writerSupplier.get();
		STData stData = data.data();

		System.out.print( "Saving spatial data ... " );
		long time = System.currentTimeMillis();

		writeHeader(writer, stData);
		writeBarcodes(writer, stData.getBarcodes());
		writeGeneNames(writer, stData.getGeneNames());

		writeExpressionValues(writer, stData.getAllExprValues());
		writeLocations(writer, stData.getLocations());
		writeTransformation(writer, data.transform(), "transform");
		writeTransformation(writer, data.intensityTransform(), "intensity_transform");

		updateStoredMetadata(data.data().getMetaData());

		System.out.println( "Saving took " + ( System.currentTimeMillis() - time ) + " ms." );
	}

	public void updateStoredMetadata(Map<String, RandomAccessibleInterval<? extends NativeType<?>>> metadata) throws IOException {
		if (readOnly)
			throw new IllegalStateException("Trying to write to read-only file.");

		N5Writer writer = writerSupplier.get();
		List<String> existingMetadata = detectMetaData(writer);

		for (Entry<String, RandomAccessibleInterval<? extends NativeType<?>>> newEntry : metadata.entrySet()) {
			if (existingMetadata.contains(newEntry.getKey()))
				System.out.println("Existing metadata '" + newEntry.getKey() + "' was not updated.");
			else
				writeMetaData(writer, newEntry.getKey(),  newEntry.getValue());
		}
	}

	protected abstract void writeHeader(N5Writer writer, STData data) throws IOException;

	protected void writeLocations(N5Writer writer, RandomAccessibleInterval<DoubleType> locations) throws IOException {
		writeLocations(writer, locations, storageSpec.locationPath);
	}

	protected abstract void writeLocations(N5Writer writer, RandomAccessibleInterval<DoubleType> locations, String locationsPath) throws IOException;

	protected void writeExpressionValues(N5Writer writer, RandomAccessibleInterval<DoubleType> exprValues) throws IOException {
		writeExpressionValues(writer, exprValues, storageSpec.exprValuePath);
	}

	protected abstract void writeExpressionValues(N5Writer writer, RandomAccessibleInterval<DoubleType> exprValues, String exprValuesPath) throws IOException;

	protected abstract void writeBarcodes(N5Writer writer, List<String> barcodes) throws IOException;

	protected abstract void writeGeneNames(N5Writer writer, List<String> geneNames) throws IOException;

	protected abstract void writeTransformation(N5Writer writer, AffineGet transform, String name) throws IOException;

	protected void writeMetaData(N5Writer writer, String label, RandomAccessibleInterval<? extends NativeType<?>> data) throws IOException {
		writeMetaData(writer, storageSpec.annoationPath, label, data);
	}

	protected abstract void writeMetaData(N5Writer writer, String annotationsPath, String label, RandomAccessibleInterval<? extends NativeType<?>> data) throws IOException;

	public void updateTransformation(AffineGet transform, String name) throws IOException {
		if (readOnly)
			throw new IllegalStateException("Trying to modify a read-only file.");

		N5Writer writer = writerSupplier.get();
		writeTransformation(writer, transform, name);
	}

	public static SpatialDataIO inferFromName(final String path, final ExecutorService service) throws IOException {
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
			return new AnnDataIO(backendSupplier, service);
		else
			return new N5IO(backendSupplier, service);
	}

	static class StorageSpec {
		public final String locationPath;
		public final String exprValuePath;
		public final String annoationPath;

		StorageSpec(final String locationPath, final String exprValuePath, final String annoationPath) {
			this.locationPath = locationPath;
			this.exprValuePath = exprValuePath;
			this.annoationPath = annoationPath;
		}
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
