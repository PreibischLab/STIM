package test;

import net.imglib2.IterableRealInterval;
import net.imglib2.KDTree;
import net.imglib2.RealCursor;
import net.imglib2.RealPoint;
import net.imglib2.RealPointSampleList;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Util;

public class Filters
{
	public static < T extends RealType< T > > RealPointSampleList< T > filterMedian( final IterableRealInterval< T > data, final double radius )
	{
		final T type = data.firstElement().createVariable();

		final KDTree< T > tree = new KDTree<>( data );

		final RadiusNeighborSearchOnKDTree< T > search = new RadiusNeighborSearchOnKDTree<>( tree );

		final RealPointSampleList< T > filtered = new RealPointSampleList<>( data.numDimensions() );

		final RealCursor< T > cursor = data.localizingCursor();

		while ( cursor.hasNext() )
		{
			cursor.fwd();
			search.search( cursor, radius, false );

			final double[] values = new double[ search.numNeighbors() ];

			for ( int i = 0; i < search.numNeighbors(); ++i )
				values[ i ] = search.getSampler( i ).get().getRealDouble();

			final T value = type.copy();
			value.setReal( Util.median( values ) );

			filtered.add( new RealPoint( cursor ), value );
		}

		return filtered;
	}
}
