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
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;


@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class FilterTest {

    public static Stream<Named<DoubleFilterCreator>> provideParameters() {
		return Stream.of(
			named("Density Filter", (radius, tree) -> new DensityFilterFactory<>(new DoubleType(0), radius).createFilter(tree)),
			named("Median Filter", (radius, tree) -> new MedianFilterFactory<>(new DoubleType(0), radius).createFilter(tree)),
			named("Single Spot Removing Filter", (radius, tree) -> new SingleSpotRemovingFilterFactory<>(new DoubleType(0), radius).createFilter(tree)),
			named("Mean Filter", (radius, tree) -> new MeanFilterFactory<DoubleType, DoubleType>(new DoubleType(0), radius).createFilter(tree)),
			named("Gaussian Filter", (radius, tree) -> new GaussianFilterFactory<DoubleType, DoubleType>(new DoubleType(0), radius).createFilter(tree)));
    }

	@ParameterizedTest
	@MethodSource("provideParameters")
	public void filter_is_ivariant_wrt_radius(DoubleFilterCreator filterCreator) {
		KDTree<DoubleType> tree = createSingleNodeTree();
		RealPoint queryPoint = new RealPoint(0.0, 0.0);

		DoubleType expectedValue = new DoubleType(0.0);
		filterCreator.create(1e-14, tree).filter(queryPoint, expectedValue);
		DoubleType actualValue = new DoubleType(0.0);

		for (double radius = 1e-12; radius < 1e1; radius *= 1e3) {
			Filter<DoubleType> filter = filterCreator.create(radius, tree);
			filter.filter(queryPoint, actualValue);
			assertEquals(expectedValue.getRealDouble(), actualValue.getRealDouble(), 1e-8);
		}
	}

	@ParameterizedTest
	@MethodSource("provideParameters")
	public void filter_is_local(DoubleFilterCreator filterCreator) {
		KDTree<DoubleType> tree = createSingleNodeTree();
		List<RealPoint> queryPoints = Arrays.asList(
				new RealPoint(-1.0, -1.0), new RealPoint(  1.0, -1.0),
				new RealPoint( 1.0,  1.0), new RealPoint( -1.0,  1.0));

		Filter<DoubleType> filter = filterCreator.create(0.1, tree);
		DoubleType actualValue = new DoubleType(0.0);

		for (RealPoint point : queryPoints) {
			filter.filter(point, actualValue);
			assertEquals(0.0, actualValue.getRealDouble(), 1e-8);
		}
	}

	@ParameterizedTest
	@MethodSource("provideParameters")
	public void filter_respects_neighborhood(DoubleFilterCreator filterCreator) {
		KDTree<DoubleType> tree = createDoubleNodeTree();
		RealPoint queryPoint = new RealPoint(0.0, 0.0);

		Filter<DoubleType> filter = filterCreator.create(1.0, tree);
		DoubleType actualValue = new DoubleType(0.0);
		filter.filter(queryPoint, actualValue);

		assertTrue(0.0 < actualValue.getRealDouble());
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

	protected interface DoubleFilterCreator {
		Filter<DoubleType> create(double radius, KDTree<DoubleType> tree);
	}
}