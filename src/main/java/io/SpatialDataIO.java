package io;

import com.google.gson.JsonElement;
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
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class SpatialDataIO {

	protected final String path;
	protected final N5Reader n5;
	protected boolean readOnly = true;
	protected N5Options options;
	protected N5Options options1d;

	public SpatialDataIO(String path, N5Reader reader) {
		if (path == null)
			throw new SpatialDataIOException("No path to N5 given.");

		this.path = path;
		this.n5 = reader;
		readOnly = !(reader instanceof N5Writer);

		options = new N5Options(
				new int[]{512, 512},
				new GzipCompression(3),
				Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() / 2)));
		options1d = new N5Options(
				new int[]{512*512},
				new GzipCompression(3),
				Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() / 2)));
	}

	public STDataAssembly readData() throws IOException {
		long time = System.currentTimeMillis();
		System.out.print( "Reading spatial data '" + path + "' ... " );

		List<String> geneNames = readGeneNames();
		List<String> barcodes = readBarcodes();

		final HashMap<String, Integer> geneLookup = new HashMap<>();
		for (int i = 0; i < geneNames.size(); ++i ) {
			geneLookup.put(geneNames.get(i), i);
		}

		RandomAccessibleInterval<DoubleType> locations = readLocations();
		RandomAccessibleInterval<DoubleType> exprValues = readExpressionValues();

		STData stData = new STDataImgLib2(locations, exprValues, geneNames, barcodes, geneLookup);

		AffineTransform intensityTransform = new AffineTransform(1);
		readAndSetTransformation(intensityTransform, "intensity_transform");
		AffineTransform2D transform = new AffineTransform2D();
		readAndSetTransformation(transform, "transform");

		System.out.println("Parsing took " + (System.currentTimeMillis() - time) + " ms.");
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

	public void setExecutorService(ExecutorService exec) {
		options.exec = exec;
		options1d.exec = exec;
	}

	protected abstract RandomAccessibleInterval<DoubleType> readLocations();

	protected abstract RandomAccessibleInterval<DoubleType> readExpressionValues();

	public abstract JsonElement readMetaData();

	protected abstract List<String> readBarcodes();

	protected abstract List<String> readGeneNames();

	protected abstract List<String> readCellTypes();

	public abstract Boolean containsCellTypes();

	protected abstract <T extends NativeType<T> & RealType<T>> void readAndSetTransformation(AffineSet transform, String name);

	public void writeData(STDataAssembly data) throws IOException {
		if (readOnly)
			throw new SpatialDataIOException("Trying to write to read-only N5.");

		N5Writer writer = (N5Writer) n5;
		STData stData = data.data();

		System.out.print( "Saving spatial data '" + path + "' ... " );
		long time = System.currentTimeMillis();

		writeHeader(writer);
		writeBarcodes(writer, stData.getBarcodes());
		writeGeneNames(writer, stData.getGeneNames());

		writeExpressionValues(writer, stData.getAllExprValues());
		writeLocations(writer, stData.getLocations());
		writeTransformation(writer, data.transform(), "transform");
		writeTransformation(writer, data.intensityTransform(), "intensity_transform");

		System.out.println( "Saving took " + ( System.currentTimeMillis() - time ) + " ms." );
	}

	protected abstract void writeHeader(N5Writer writer) throws IOException;

	protected abstract void writeLocations(N5Writer writer, RandomAccessibleInterval<DoubleType> locations);

	protected abstract void writeExpressionValues(N5Writer writer, RandomAccessibleInterval<DoubleType> exprValues);

	public abstract void writeMetaData(N5Writer writer, JsonElement metaData);

	protected abstract void writeBarcodes(N5Writer writer, List<String> barcodes) throws IOException;

	protected abstract void writeGeneNames(N5Writer writer, List<String> geneNames) throws IOException;

	protected abstract void writeTransformation(N5Writer writer, AffineGet transform, String name);

	protected abstract void writeCellTypes(N5Writer writer, List<String> cellTypes);


	class N5Options {

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
