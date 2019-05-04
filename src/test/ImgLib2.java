package test;

import java.util.ArrayList;
import java.util.List;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.KDTree;
import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealPointSampleList;
import net.imglib2.neighborsearch.KNearestNeighborSearch;
import net.imglib2.neighborsearch.KNearestNeighborSearchOnKDTree;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;

public class ImgLib2
{
	public static class SimpleStats
	{
		public double avg, median, min, max;
	}

	public static SimpleStats distanceStats( final List< double[] > coordinates )
	{
		final List< RealPoint > points = getRealPointList( coordinates );

		final KDTree< RealPoint > tree = new KDTree<>( points, points );

		final KNearestNeighborSearch< RealPoint > search =  new KNearestNeighborSearchOnKDTree<>( tree, 2 );

		final double[] values = new double[ points.size() ];
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;

		for ( int i = 0; i < values.length; ++i )
		{
			final RealPoint p = points.get( i );

			search.search( p );
			values[ i ] = search.getDistance( 1 );
			min = Math.min( values[ i ], min );
			max = Math.max( values[ i ], max );
		}

		final SimpleStats stats = new SimpleStats();

		stats.median = Util.median( values );
		stats.avg = Util.average( values );
		stats.min = min;
		stats.max = max;

		return stats;
	}

	public static long[] dimensions( final Interval ri )
	{
		final long[] dim = new long[ ri.numDimensions() ];
		ri.dimensions( dim );
		return dim;
	}

	public static long[] min( final Interval ri )
	{
		final long[] min = new long[ ri.numDimensions() ];
		ri.min( min );
		return min;
	}

	public static Interval roundRealInterval( final RealInterval ri )
	{
		final long[] min = new long[ ri.numDimensions() ];
		final long[] max = new long[ ri.numDimensions() ];

		for ( int d = 0; d < ri.numDimensions(); ++d )
		{
			min[ d ] = Math.round( Math.floor( ri.realMin( d ) ) );
			max[ d ] = Math.round( Math.ceil( ri.realMax( d ) ) );
		}

		return new FinalInterval( min, max );
	}

	public static List< RealPoint > getRealPointList( final List< double[] > coordinates )
	{
		final ArrayList< RealPoint > rp = new ArrayList<>();

		for ( final double[] c : coordinates )
			rp.add( new RealPoint( c ) );

		return rp;
	}

	public static RealPointSampleList< DoubleType > wrapDouble(
			final List< double[] > coordinates,
			final double[] values )
	{
		final RealPointSampleList< DoubleType > list =
				new RealPointSampleList<>( coordinates.get( 0 ).length );

		for ( int i = 0; i < coordinates.size(); ++i )
			list.add( new RealPoint( coordinates.get( i ) ), new DoubleType( values[ i ] ) );

		return list;
	}

	public static RealPointSampleList< FloatType > wrapFloat(
			final List< double[] > coordinates,
			final double[] values )
	{
		final RealPointSampleList< FloatType > list =
				new RealPointSampleList<>( coordinates.get( 0 ).length );

		for ( int i = 0; i < coordinates.size(); ++i )
			list.add( new RealPoint( coordinates.get( i ) ), new FloatType( (float)values[ i ] ) );

		return list;
	}
}
