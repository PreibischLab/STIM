package io;

import com.google.gson.JsonElement;
import data.STData;
import data.STDataImgLib2;
import data.STDataStatistics;
import gui.STDataAssembly;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.numeric.real.DoubleType;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

public abstract class SpatialDataIO {

	protected final String path;
	protected final N5Reader n5;
	protected boolean readOnly = true;

	public SpatialDataIO(String path, N5Constructor n5Constructor) {
		this.path = path;
		try {
			this.n5 = n5Constructor.apply(path);
		}
		catch (IOException e) {
			throw new SpatialDataIOException("Could not open file: " + path + "\n" + e.getMessage());
		}

		if (n5 instanceof N5Writer)
			readOnly = false;
	}

	public STDataAssembly readData() throws IOException {
		long time = System.currentTimeMillis();

		List<String> geneNames = readGeneNames();
		List<String> barcodes = readBarcodes();

		final HashMap<String, Integer> geneLookup = new HashMap<>();
		for (int i = 0; i < geneNames.size(); ++i ) {
			geneLookup.put(geneNames.get(i), i);
		}

		RandomAccessibleInterval<DoubleType> locations = readLocations();
		RandomAccessibleInterval<DoubleType> exprValues = readExpressionValues();

		System.out.println("Parsing took " + (System.currentTimeMillis() - time) + " ms.");

		STData stData = new STDataImgLib2(locations, exprValues, geneNames, barcodes, geneLookup);
		return createTrivialAssembly(stData);
	}

	protected abstract RandomAccessibleInterval<DoubleType> readLocations();

	protected abstract RandomAccessibleInterval<DoubleType> readExpressionValues();

	public abstract JsonElement readMetaData();

	protected abstract List<String> readBarcodes();

	protected abstract List<String> readGeneNames();

	protected abstract List<String> readCellTypes();

	public abstract Boolean containsCellTypes();

	public abstract void writeData(STDataAssembly data) throws IOException;

	protected abstract void writeLocations(RandomAccessibleInterval<DoubleType> locations);

	protected abstract void writeExpressionValues(RandomAccessibleInterval<DoubleType> exprValues);

	public abstract void writeMetaData(JsonElement metaData);

	protected abstract void writeBarcodes(List<String> barcodes);

	protected abstract void writeGeneNames(List<String> geneNames);

	protected abstract void writeCellTypes(List<String> cellTypes);

	protected STDataAssembly createTrivialAssembly(STData data) {
		final STDataStatistics stat = new STDataStatistics(data);
		final AffineTransform2D transform = new AffineTransform2D();
		final AffineTransform intensityTransform = new AffineTransform(1);
		intensityTransform.set(1, 0);

		return new STDataAssembly(data, stat, transform, intensityTransform);
	}

	// custom functional interface, since Function<String, N5Reader> doesn't throw IOException
	public interface N5Constructor {
		public N5Reader apply(String path) throws IOException;
	}

	public static class SpatialDataIOException extends RuntimeException {
		public SpatialDataIOException() {}

		public SpatialDataIOException(String message) {
			super(message);
		}
	}
}
