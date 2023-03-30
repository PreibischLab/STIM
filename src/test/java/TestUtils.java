import data.STData;
import data.STDataStatistics;
import data.STDataText;
import gui.STDataAssembly;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.Type;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestUtils {

	public static <T extends Type<T>> void assertRaiEquals(RandomAccessibleInterval<T> expected, RandomAccessibleInterval<T> actual) {
		assertEquals(expected.dimension(0), actual.dimension(0), "Number of columns does not coincide.");
		assertEquals(expected.dimension(1), actual.dimension(1), "Number of rows does not coincide.");

		RandomAccess<T> raExpected = expected.randomAccess();
		RandomAccess<T> raActual = actual.randomAccess();
		for (int i = 0; i < expected.dimension(0); ++i)
			for (int j = 0; j < expected.dimension(1); ++j)
				assertEquals(raExpected.setPositionAndGet(i, j), raActual.setPositionAndGet(i, j),
						"Rai's differ on entry (" + i + "," + j +")");
	}

	public static STData createTestDataSet() {
		final ArrayList<Pair<double[], String>> coordinates = new ArrayList<>();
		coordinates.add(new ValuePair<>(new double[]{ -1, 1 }, "ATTA"));
		coordinates.add(new ValuePair<>(new double[]{ 2.1, 2 }, "GTTC"));
		coordinates.add(new ValuePair<>(new double[]{ 17.1, -5.1 }, "CCCT"));

		final HashMap<String, double[]> geneMap = new HashMap<>();

		geneMap.put("gene1", new double[]{ 1.1, 2.2, 13.1 });
		geneMap.put("gene2", new double[]{ 0.0, 23.12, 1.1 });
		geneMap.put("gene3", new double[]{ 4.1, 0.0, 7.65 });
		geneMap.put("gene4", new double[]{ 0.0, 6.12, 5.12 });

		return new STDataText(coordinates, geneMap);
	}

	public static STDataAssembly createTrivialAssembly(STData data) {
		final STDataStatistics stat = new STDataStatistics(data);
		final AffineTransform2D transform = new AffineTransform2D();
		final AffineTransform intensityTransform = new AffineTransform(1);
		intensityTransform.set(1, 0);

		return new STDataAssembly(data, stat, transform, intensityTransform);
	}
}
