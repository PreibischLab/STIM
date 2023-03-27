package io;

import com.google.gson.JsonElement;
import data.STData;
import data.STDataN5;
import data.STDataStatistics;
import gui.STDataAssembly;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.real.DoubleType;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;

import java.io.IOException;
import java.util.List;

public abstract class SpatialDataIO {

	protected final String path;
	protected final N5Reader n5;
	protected boolean readOnly = true;

	SpatialDataIO(String path, N5Reader n5) {
		this.path = path;
		this.n5 = n5;

		if (n5 instanceof N5Writer)
			readOnly = false;
	}

	public abstract STDataAssembly readData() throws IOException;

	protected abstract RandomAccessibleInterval<DoubleType> readLocations();

	protected abstract RandomAccessibleInterval<DoubleType> readExpressionValues();

	public abstract JsonElement readMetaData();

	protected abstract List<String> readBarcodes();

	protected abstract List<String> readGeneNames();

	protected abstract List<String> readCellTypes();

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

	public static class SpatialDataIOException extends RuntimeException {
		public SpatialDataIOException() {}

		public SpatialDataIOException(String message) {
			super(message);
		}
	}
}
