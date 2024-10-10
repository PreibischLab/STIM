import data.NormalizingSTData;
import data.STData;
import data.STDataImgLib2;
import data.STDataStatistics;
import data.STDataText;
import net.imglib2.Interval;
import net.imglib2.KDTree;
import net.imglib2.PointSampleList;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealCursor;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPointSampleList;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.kdtree.KDTreeData;
import net.imglib2.neighborsearch.KNearestNeighborSearch;
import net.imglib2.neighborsearch.KNearestNeighborSearchOnKDTree;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.ValuePair;
import net.imglib2.util.Pair;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
		STDataStatistics statistics = new STDataStatistics( data );

		System.out.println( data.getClass().getName() ); // data.STDataImgLib2
		System.out.println( data.cursor().getClass().getName() ); // imglib2.LocationRealCursor
		data.cursor().copy().forEachRemaining( p -> System.out.println( "cursor on STDATA: " + Arrays.toString( p.positionAsDoubleArray() ) ) );

		/*
		// this works ...
		RealPointSampleList<RealLocalizable> psl = new RealPointSampleList<>( 2 );
		RealPoint p = new RealPoint( 1, 1 );
		psl.add( p,p );

		p = new RealPoint( 2, 2 );
		psl.add( p,p );
		*/

		// the treedata iterator already fails
		KDTreeData<RealLocalizable> treeData = KDTreeData.create( (int)data.size(), data.copy(), data.copy(), false );
		treeData.values().forEach( p -> System.out.println( "cursor on KDTreeData: " + Arrays.toString( p.positionAsDoubleArray() ) ) );

		final KDTree<RealLocalizable> tree = new KDTree<>( KDTreeData.create( (int)data.size(), data.copy(), data.copy(), false ) ); // also doesn't work
		//final KDTree<RealLocalizable> tree = new KDTree<>( KDTreeData.create( (int)data.size(), data, data, false ) ); // also doesn't work
		//final KDTree<RealLocalizable> tree = new KDTree<>( (int)data.size(), data, data ); // also doesn't work
		//final KDTree<RealLocalizable> tree = new KDTree<>( data ); // doesn't work
		tree.iterator().forEachRemaining( po -> System.out.println( "iterator on tree: " + Arrays.toString( po.positionAsDoubleArray() ) ) );;

		final KNearestNeighborSearch<RealLocalizable> search =  new KNearestNeighborSearchOnKDTree<>( tree, 2 );

		// TODO: seems like the KDTree iterator is not working ... 
		assertEquals(0.7, statistics.getMeanDistance(), 1e-8);
		assertEquals(0.5, statistics.getMedianDistance(), 1e-8);
		assertEquals(0.5, statistics.getMinDistance(), 1e-8);
		assertEquals(1.0, statistics.getMaxDistance(), 1e-8);
	}

	@ParameterizedTest
	@MethodSource("createDataInstances")
	public void zero_expression_is_zero(STData data) {
		RealCursor<DoubleType> cursor = data.getExprData("Gene 1").localizingCursor();
		while (cursor.hasNext())
			assertEquals(0.0, cursor.next().getRealDouble(), 1e-8);
	}

	@ParameterizedTest
	@MethodSource("createDataInstances")
	public void nonzero_expression_is_not_constant(STData data) {
		RealCursor<DoubleType> cursor = data.getExprData("Gene 3").localizingCursor();
		double firstValue = cursor.next().getRealDouble();
		while (cursor.hasNext())
			assertNotEquals(firstValue, cursor.next().getRealDouble(), 1e-8);
	}

	@ParameterizedTest
	@MethodSource("createDataInstances")
	public void locations_are_inserted_correctly(STData data) {
		RealCursor<DoubleType> cursor = data.getExprData("Gene 2").localizingCursor();
		List<double[]> locations = data.getLocationsCopy();
		double[] actualPosition = new double[2];

		while (cursor.hasNext()) {
			cursor.fwd();
			cursor.localize(actualPosition);
			assertTrue(locations.stream().anyMatch((loc) -> Arrays.equals(loc, actualPosition)));
		}
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
		textExprValues.put("Gene 1", IntStream.range(0, 5).mapToDouble((k) -> 0.0).toArray());
		textExprValues.put("Gene 2", IntStream.range(0, 5).mapToDouble((k) -> 1.0).toArray());
		textExprValues.put("Gene 3", IntStream.range(0, 5).mapToDouble((k) -> k).toArray());
		textExprValues.put("Gene 4", IntStream.range(0, 5).mapToDouble((k) -> 5-k).toArray());

		List<Named<STData>> dataList = new ArrayList<>();
		dataList.add(named("Text Data", new STDataText(textLocations, textExprValues)));

		final List<String> barcodeNames = textLocations.stream().map(Pair::getB).collect(Collectors.toList());
		final List<String> geneNames = new ArrayList<>(textExprValues.keySet());

		final RandomAccessibleInterval<DoubleType> locations = new ArrayImgFactory<>(new DoubleType()).create(barcodeNames.size(), 2);
		for (int k = 0; k < barcodeNames.size(); k++) {
			fillColumn(locations, textLocations.get(k).getA(), k);
		}

		final RandomAccessibleInterval<DoubleType> exprValues = new ArrayImgFactory<>(new DoubleType()).create(geneNames.size(), barcodeNames.size());
		for (int k = 0; k < geneNames.size(); k++) {
			fillColumn(exprValues, textExprValues.get("Gene " + (k+1)), k);
		}

		final HashMap<String, Integer> geneLookup = new HashMap<>();
		for (int k = 0; k < geneNames.size(); k++) {
			geneLookup.put("Gene " + (k+1), k);
		}

		dataList.add(named("ImgLib2 Data", new STDataImgLib2(locations, exprValues, geneNames, barcodeNames, geneLookup)));
		dataList.add(named("Normalized Data", new NormalizingSTData(dataList.get(1).getPayload())));

		return dataList;
	}

	protected static List<String> provideNonconstantGenes() {
		return Arrays.asList("Gene 1", "Gene 2");
	}

	protected static List<String> provideConstantGenes() {
		return Arrays.asList("Gene 3", "Gene 4");
	}

	/**
	 * Fill column
	 *
	 * @param rai 2d {@RandomAccessibleInterval} to fill
	 * @param values array of double values to fill in
	 * @param index index of the column
	 */
	protected static void fillColumn(RandomAccessibleInterval<DoubleType> rai, double[] values, int index) {
		RandomAccess<DoubleType> ra = rai.randomAccess();
		ra.setPosition(index, 0);

		for (int k = 0; k < rai.dimension(1); k++) {
			ra.setPosition(k, 1);
			ra.get().set(values[k]);
		}
	}
}