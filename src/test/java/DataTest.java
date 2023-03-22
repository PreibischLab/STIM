import data.STData;
import data.STDataImgLib2;
import data.STDataN5;
import data.STDataStatistics;
import data.STDataText;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.ValuePair;
import org.janelia.saalfeldlab.n5.N5FSReader;
import net.imglib2.util.Pair;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Named.named;


@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class DataTest {

	@ParameterizedTest
	@MethodSource("createDataInstances")
	public void render_interval_is_correct(STData data) {
		assertEquals(2, data.numDimensions());
		assertEquals(5, data.numLocations());
		assertEquals(4, data.numGenes());

		Interval interval = data.getRenderInterval();
		long[] actual = new long[2];

		interval.min(actual);
		assertArrayEquals(new long[]{0, 0}, actual);
		interval.max(actual);
		assertArrayEquals(new long[]{1, 1}, actual);
	}

	@ParameterizedTest
	@MethodSource("createDataInstances")
	public void statistics_are_correct(STData data) {
		// nearest neighbor distances: 2x 1 (nodes 1 and 5), 3x 1/2 (nodes 2, 3, and 4)
		STDataStatistics statistics = new STDataStatistics(data);

		assertEquals(0.7, statistics.getMeanDistance(), 1e-8);
		assertEquals(0.5, statistics.getMedianDistance(), 1e-8);
		assertEquals(0.5, statistics.getMinDistance(), 1e-8);
		assertEquals(1.0, statistics.getMaxDistance(), 1e-8);
	}

	protected static List<Named<STData>> createDataInstances() {
		/* Create locations  5 - 4
		   with the          |   3
		   following layout  1 - 2  */
		final List<Pair<double[], String>> textLocations = new ArrayList<>();
		textLocations.add(new ValuePair(new double[]{0.0, 0.0}, "001"));
		textLocations.add(new ValuePair(new double[]{1.0, 0.0}, "010"));
		textLocations.add(new ValuePair(new double[]{1.0, 0.5}, "011"));
		textLocations.add(new ValuePair(new double[]{1.0, 1.0}, "100"));
		textLocations.add(new ValuePair(new double[]{0.0, 1.0}, "101"));

		final HashMap<String, double[]> textExprValues = new HashMap<>();
		textExprValues.put("Gene 1", IntStream.range(0, 5).mapToDouble((k) -> k).toArray());
		textExprValues.put("Gene 2", IntStream.range(0, 5).mapToDouble((k) -> 5-k).toArray());
		textExprValues.put("Gene 3", IntStream.range(0, 5).mapToDouble((k) -> 1.0).toArray());
		textExprValues.put("Gene 4", IntStream.range(0, 5).mapToDouble((k) -> 0.0).toArray());

		List<Named<STData>> dataList = new ArrayList<>();
		dataList.add(named("Text Data", new STDataText(textLocations, textExprValues)));

		final List<String> barcodeNames = textLocations.stream().map(Pair::getB).collect(Collectors.toList());
		final List<String> geneNames = new ArrayList<>(textExprValues.keySet());

		final RandomAccessibleInterval<DoubleType> locations = new ArrayImgFactory<>(new DoubleType()).create(barcodeNames.size(), 2);
		for (int k = 0; k < barcodeNames.size(); k++) {
			fillDimension(locations, textLocations.get(k).getA(), k, 1);
		}

		final RandomAccessibleInterval<DoubleType> exprValues = new ArrayImgFactory<>(new DoubleType()).create(geneNames.size(), barcodeNames.size());
		for (int k = 0; k < geneNames.size(); k++) {
			fillDimension(exprValues, textExprValues.get("Gene " + (k+1)), k, 0);
		}

		final HashMap<String, Integer> geneLookup = new HashMap<>();
		for (int k = 0; k < geneNames.size(); k++) {
			geneLookup.put("Gene " + (k+1), k);
		}

		dataList.add(named("ImgLib2 Data", new STDataImgLib2(locations, exprValues, geneNames, barcodeNames, geneLookup)));

		try {
			String path = "/test";
			N5FSReader reader = new N5FSReader(path);
			dataList.add(named("N5 Data", new STDataN5(locations, exprValues, geneNames, barcodeNames, geneLookup, reader, new File(path), "dataset")));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return dataList;
	}

	protected static void fillDimension(RandomAccessibleInterval<DoubleType> rai, double[] values, int index, int dim) {
		RandomAccess<DoubleType> ra = rai.randomAccess();
		ra.setPosition(index, (dim == 0) ? 1 : 0);

		for (int k = 0; k <= rai.max(dim); k++) {
			ra.setPosition(k, dim);
			ra.get().set(values[k]);
		}
	}
}