package data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.KDTree;
import net.imglib2.RealCursor;
import net.imglib2.RealInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.neighborsearch.KNearestNeighborSearch;
import net.imglib2.neighborsearch.KNearestNeighborSearchOnKDTree;
import net.imglib2.util.Util;

public class STDataUtils
{
	static class DistanceStats
	{
		public double avgDist, medianDist, minDist, maxDist;
	}

	public static List< String > commonGeneNames( final List< String > names0, final List< String > names1 )
	{
		final ArrayList< String > commonGeneNames = new ArrayList<>();
		final HashSet< String > names0Hash = new HashSet<>( names0 );

		for ( final String name1 : names1 )
			if ( names0Hash.contains( name1 ) )
				commonGeneNames.add( name1 );

		return commonGeneNames;
	}

	public static Interval getCommonInterval( final STData stDataA, final STData stDataB )
	{
		final ArrayList< STData > list = new ArrayList<>();
		list.add( stDataA );
		list.add( stDataB );

		return getCommonInterval( list );
	}

	public static Interval getCommonInterval( final Collection< STData > datasets )
	{
		long[] min = null, max = null;

		for ( final STData dataset : datasets )
		{
			if ( min == null )
			{
				min = new long[ dataset.getRenderInterval().numDimensions() ];
				max = new long[ dataset.getRenderInterval().numDimensions() ];
				
				dataset.getRenderInterval().min( min );
				dataset.getRenderInterval().max( max );
			}
			else
			{
				for ( int d = 0; d < min.length; ++d )
				{
					min[ d ] = Math.min( min[ d ], dataset.getRenderInterval().min( d ) );
					max[ d ] = Math.max( max[ d ], dataset.getRenderInterval().max( d ) );
				}
			}
		}

		if ( min != null )
			return new FinalInterval( min, max );
		else
			return null;
	}

	public static < R extends RealLocalizable > DistanceStats distanceStats( final KDTree< R > tree )
	{
		final KNearestNeighborSearch< R > search =  new KNearestNeighborSearchOnKDTree< R >( tree, 2 );

		final double[] values = new double[ (int)tree.size() ];
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;

		int i = 0;

		for ( final R p : tree )
		{
			search.search( p );
			values[ i ] = search.getDistance( 1 );
			min = Math.min( values[ i ], min );
			max = Math.max( values[ i ], max );

			++i;
		}

		final DistanceStats stats = new DistanceStats();

		stats.medianDist = Util.median( values );
		stats.avgDist = Util.average( values );
		stats.minDist = min;
		stats.maxDist = max;

		return stats;
	}

	public static RealInterval computeRealInterval( final IterableRealInterval< ? > coord )
	{
		if ( coord.size() == 0 )
			return null;

		final int n = coord.numDimensions();

		final RealCursor< ? > cursor = coord.localizingCursor();
		cursor.fwd();

		final double[] min = new double[ n ];
		final double[] max = min.clone();

		cursor.localize( min );
		cursor.localize( max );


		while( cursor.hasNext() )
		{
			cursor.fwd();

			for ( int d = 0; d < n; ++d )
			{
				final double pos = cursor.getDoublePosition( d );

				min[ d ] = Math.min( pos, min[ d ] );
				max[ d ] = Math.max( pos, max[ d ] );
			}
		}

		return new FinalRealInterval( min, max );
	}


	public static STData createTestDataSet()
	{
		final ArrayList< double[] > coordinates = new ArrayList<>();
		coordinates.add( new double[] { -1, 1 } );
		coordinates.add( new double[] { 2.1, 2 } );
		coordinates.add( new double[] { 17.1, -5.1 } );

		final HashMap< String, double[] > geneMap = new HashMap<>();

		geneMap.put( "gene1", new double[] { 1.1, 2.2, 13.1 } );
		geneMap.put( "gene2", new double[] { 14.1, 23.12, 1.1 } );
		geneMap.put( "gene3", new double[] { 4.1, 4.15, 7.65 } );
		geneMap.put( "gene4", new double[] { 0.1, 6.12, 5.12 } );

		return new STDataText( coordinates, geneMap );
	}

}
