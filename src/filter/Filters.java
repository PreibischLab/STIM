package filter;

import net.imglib2.Cursor;
import net.imglib2.IterableRealInterval;
import net.imglib2.Iterator;
import net.imglib2.KDTree;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealCursor;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPointSampleList;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

public class Filters
{
	public static < T extends RealType< T > > RealPointSampleList< T > filterMedian( final IterableRealInterval< T > data, final T outofbounds, final double radius )
	{
		return filterMedian( data, data.localizingCursor(), outofbounds, radius );
	}

	public static < T extends RealType< T >, C extends RealLocalizable & Iterator > RealPointSampleList< T > filterMedian( final IterableRealInterval< T > data, final C cursor, final T outofbounds, final double radius )
	{
		final KDTree< T > tree = new KDTree<>( data );

		final RadiusNeighborSearchOnKDTree< T > search = new RadiusNeighborSearchOnKDTree<>( tree );

		final RealPointSampleList< T > filtered = new RealPointSampleList<>( data.numDimensions() );

		while ( cursor.hasNext() )
		{
			cursor.fwd();
			search.search( cursor, radius, false );

			if ( search.numNeighbors() > 0 )
			{
				final double[] values = new double[ search.numNeighbors() ];
	
				for ( int i = 0; i < search.numNeighbors(); ++i )
					values[ i ] = search.getSampler( i ).get().getRealDouble();
	
				final T value = outofbounds.createVariable();
	
				value.setReal( Util.median( values ) );
	
				filtered.add( new RealPoint( cursor ), value );
			}
			else
			{
				filtered.add( new RealPoint( cursor ), outofbounds.copy() );
			}
		}

		return filtered;
	}

	public static < T extends RealType< T > > void filterMedian( final IterableRealInterval< T > data, final RandomAccessibleInterval< T > filtered, final T outofbounds, final double radius )
	{
		final KDTree< T > tree = new KDTree<>( data );

		final RadiusNeighborSearchOnKDTree< T > search = new RadiusNeighborSearchOnKDTree<>( tree );

		final Cursor< T > cursor = Views.iterable( filtered ).localizingCursor();

		while ( cursor.hasNext() )
		{
			cursor.fwd();
			search.search( cursor, radius, false );

			if ( search.numNeighbors() > 0 )
			{
				final double[] values = new double[ search.numNeighbors() ];
	
				for ( int i = 0; i < search.numNeighbors(); ++i )
					values[ i ] = search.getSampler( i ).get().getRealDouble();

				cursor.get().setReal( Util.median( values ) );
			}
			else
			{
				cursor.get().set( outofbounds );
			}
		}
	}

	public static < T extends RealType< T > > RealPointSampleList< DoubleType > filterAverage( final IterableRealInterval< T > data, final DoubleType outofbounds, final double radius )
	{
		final KDTree< T > tree = new KDTree<>( data );

		final RadiusNeighborSearchOnKDTree< T > search = new RadiusNeighborSearchOnKDTree<>( tree );

		final RealPointSampleList< DoubleType > filtered = new RealPointSampleList<>( data.numDimensions() );

		final RealCursor< T > cursor = data.localizingCursor();

		while ( cursor.hasNext() )
		{
			cursor.fwd();
			search.search( cursor, radius, false );

			if ( search.numNeighbors() > 0 )
			{
				double sum = 0;
	
				for ( int i = 0; i < search.numNeighbors(); ++i )
					sum += search.getSampler( i ).get().getRealDouble();
	
				filtered.add( new RealPoint( cursor ), new DoubleType( sum / (double)search.numNeighbors() ) );
			}
			else
			{
				filtered.add( new RealPoint( cursor ), outofbounds.copy() );
			}
		}

		return filtered;
	}
}
