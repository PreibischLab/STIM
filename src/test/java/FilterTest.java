import filter.DensityFilterFactory;
import filter.Filter;
import filter.GaussianFilterFactory;
import filter.MeanFilter;
import filter.MeanFilterFactory;
import filter.MedianFilterFactory;
import net.imglib2.KDTree;
import net.imglib2.RealPoint;
import net.imglib2.RealPointSampleList;
import net.imglib2.type.numeric.real.DoubleType;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class FilterTest {
	@Test
	public void test() {

		RealPointSampleList<DoubleType> samples = new RealPointSampleList<>(2);
		samples.add(new RealPoint(0.0, 0.0), new DoubleType(1.0));
		KDTree<DoubleType> tree = new KDTree<>(samples);

		Filter<DoubleType> filter = new GaussianFilterFactory<DoubleType, DoubleType>(new DoubleType(0), 1.0).createFilter(tree);

		RealPoint queryPoint = new RealPoint(0.0, 0.0);
		DoubleType actualValue = new DoubleType(0.0);
		filter.filter(queryPoint, actualValue);
		assertEquals(1.0, actualValue.getRealDouble(), 1e-8);
	}


}