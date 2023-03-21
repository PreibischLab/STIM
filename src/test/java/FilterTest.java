import filter.DensityFilterFactory;
import filter.Filter;
import filter.GaussianFilterFactory;
import filter.MeanFilterFactory;
import filter.MedianFilterFactory;
import filter.SingleSpotRemovingFilterFactory;
import net.imglib2.KDTree;
import net.imglib2.RealPoint;
import net.imglib2.RealPointSampleList;
import net.imglib2.type.numeric.real.DoubleType;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class FilterTest {

	@Parameterized.Parameter
	public BiFunction<Double, KDTree<DoubleType>, Filter<DoubleType>> filterCreator;

	@Parameterized.Parameters(name = "FilterFactory {index}")
    public static Iterable<?> data() {
		List<BiFunction<Double, KDTree<DoubleType>, Filter<DoubleType>>> parameters = Arrays.asList(
			(radius, tree) -> new DensityFilterFactory<>(new DoubleType(0), radius).createFilter(tree),
			(radius, tree) -> new MedianFilterFactory<>(new DoubleType(0), radius).createFilter(tree),
			(radius, tree) -> new SingleSpotRemovingFilterFactory<>(new DoubleType(0), radius).createFilter(tree),
			(radius, tree) -> new MeanFilterFactory<DoubleType, DoubleType>(new DoubleType(0), radius).createFilter(tree),
			(radius, tree) -> new GaussianFilterFactory<DoubleType, DoubleType>(new DoubleType(0), radius).createFilter(tree));
		return parameters;
    }

	@Test
	public void filterIsInvariantWrtRadius() {
		KDTree<DoubleType> tree = createSingleNodeTree();
		RealPoint queryPoint = new RealPoint(0.0, 0.0);

		DoubleType expectedValue = new DoubleType(0.0);
		filterCreator.apply(1e-14, tree).filter(queryPoint, expectedValue);
		DoubleType actualValue = new DoubleType(0.0);

		for (double radius = 1e-12; radius < 1e1; radius *= 1e3) {
			Filter<DoubleType> filter = filterCreator.apply(radius, tree);
			filter.filter(queryPoint, actualValue);
			assertEquals(expectedValue.getRealDouble(), actualValue.getRealDouble(), 1e-8);
		}
	}

	@Test
	public void filterIsLocal() {
		KDTree<DoubleType> tree = createSingleNodeTree();
		List<RealPoint> queryPoints = Arrays.asList(
				new RealPoint(-1.0, -1.0), new RealPoint(  1.0, -1.0),
				new RealPoint( 1.0,  1.0), new RealPoint( -1.0,  1.0));

		Filter<DoubleType> filter = filterCreator.apply(0.1, tree);
		DoubleType actualValue = new DoubleType(0.0);

		for (RealPoint point : queryPoints) {
			filter.filter(point, actualValue);
			assertEquals(0.0, actualValue.getRealDouble(), 1e-8);
		}
	}

	@Test
	public void filterRespectsNeighborhood() {
		KDTree<DoubleType> tree = createDoubleNodeTree();
		RealPoint queryPoint = new RealPoint(0.0, 0.0);

		Filter<DoubleType> filter = filterCreator.apply(1.0, tree);
		DoubleType actualValue = new DoubleType(0.0);
		filter.filter(queryPoint, actualValue);

		Assert.assertTrue(0.0 < actualValue.getRealDouble());
	}

	protected KDTree<DoubleType> createSingleNodeTree() {
		RealPointSampleList<DoubleType> samples = new RealPointSampleList<>(2);
		samples.add(new RealPoint(0.0, 0.0), new DoubleType(1.0));
		return new KDTree<>(samples);
	}

	protected KDTree<DoubleType> createDoubleNodeTree() {
		RealPointSampleList<DoubleType> samples = new RealPointSampleList<>(2);
		samples.add(new RealPoint( 0.5, 0.0), new DoubleType(1.0));
		samples.add(new RealPoint(-0.5, 0.0), new DoubleType(1.0));
		return new KDTree<>(samples);
	}
}