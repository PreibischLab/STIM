package render;

import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.KDTree;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.interpolation.neighborsearch.NearestNeighborSearchInterpolatorFactory;
import net.imglib2.neighborsearch.NearestNeighborSearch;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

public class Render
{
	public static < T extends RealType< T > > RealRandomAccessible< T > render( final IterableRealInterval< T > data )
	{
		final KDTree< T > tree = new KDTree< T > ( data );

		NearestNeighborSearch< T > search = new NearestNeighborSearchOnKDTree< T >( tree );

		// make it into RealRandomAccessible using nearest neighbor search
		return Views.interpolate( search, new NearestNeighborSearchInterpolatorFactory< T >() );
	}

	public static < T extends RealType< T > > RandomAccessibleInterval< T > render( final IterableRealInterval< T > data, final Interval interval )
	{
		// make it into RealRandomAccessible using nearest neighbor search
		RealRandomAccessible< T > realRandomAccessible = render( data );

		// convert it into a RandomAccessible which can be displayed
		RandomAccessible< T > randomAccessible = Views.raster( realRandomAccessible );

		return Views.interval( randomAccessible, interval );
	}

	public static < T extends RealType< T > > RealRandomAccessible< T > renderGauss( final IterableRealInterval< T > data, final T outofbounds, final double maxRadius, final double sigma )
	{
		final KDTree< T > tree = new KDTree< T > ( data );

		IntegratingNeighborSearch< T > search = new GaussMaxDistanceSearchOnKDTree< T >( tree, outofbounds, maxRadius, sigma, false );

		// make it into RealRandomAccessible using nearest neighbor search
		return Views.interpolate( search, new IntegratingNeighborSearchInterpolatorFactory< T >() );
	}

	public static < T extends RealType< T > > RealRandomAccessible< T > renderAvg( final IterableRealInterval< T > data, final T outofbounds, final double maxRadius )
	{
		final KDTree< T > tree = new KDTree< T > ( data );

		IntegratingNeighborSearch< T > search = new AveragingMaxDistanceSearchOnKDTree< T >( tree, outofbounds, maxRadius );

		// make it into RealRandomAccessible using nearest neighbor search
		return Views.interpolate( search, new IntegratingNeighborSearchInterpolatorFactory< T >() );
	}

	public static < T extends RealType< T > > RealRandomAccessible< T > render( final IterableRealInterval< T > data, final T outofbounds, final double maxRadius )
	{
		final KDTree< T > tree = new KDTree< T > ( data );

		NearestNeighborSearch< T > search = new NearestNeighborMaxDistanceSearchOnKDTree< T >( tree, outofbounds, maxRadius );

		// make it into RealRandomAccessible using nearest neighbor search
		return Views.interpolate( search, new NearestNeighborSearchInterpolatorFactory< T >() );
	}

	public static < T extends RealType< T > > RandomAccessibleInterval< T > render( final IterableRealInterval< T > data, final Interval interval, final T outofbounds, final double maxRadius )
	{
		// make it into RealRandomAccessible using nearest neighbor search
		RealRandomAccessible< T > realRandomAccessible = render( data, outofbounds, maxRadius );

		// convert it into a RandomAccessible which can be displayed
		RandomAccessible< T > randomAccessible = Views.raster( realRandomAccessible );

		return Views.interval( randomAccessible, interval );
	}

}
