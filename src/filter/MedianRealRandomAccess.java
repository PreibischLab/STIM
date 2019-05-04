package filter;

import net.imglib2.IterableRealInterval;
import net.imglib2.KDTree;
import net.imglib2.RealRandomAccess;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Util;
import test.SimpleRealLocalizable;

public class MedianRealRandomAccess< T extends RealType< T > > extends FilteredRealRandomAccess< T >
{
	final T outofbounds;
	final double radius;
	final SimpleRealLocalizable loc;
	final KDTree< T > tree;
	final RadiusNeighborSearchOnKDTree< T > search;

	public MedianRealRandomAccess(
			final IterableRealInterval< T > data,
			final T outofbounds,
			final double radius )
	{
		super( data );

		this.outofbounds = outofbounds;
		this.radius = radius;
		this.tree = new KDTree<>( data );
		this.search = new RadiusNeighborSearchOnKDTree<>( tree );
		this.loc = new SimpleRealLocalizable( pos );
	}

	@Override
	public T get()
	{
		search.search( loc, radius, false );

		if ( search.numNeighbors() > 0 )
		{
			final double[] values = new double[ search.numNeighbors() ];

			for ( int i = 0; i < search.numNeighbors(); ++i )
				values[ i ] = search.getSampler( i ).get().getRealDouble();

			final T value = outofbounds.createVariable();

			value.setReal( Util.median( values ) );

			return value;
		}
		else
		{
			return outofbounds;
		}
	}

	@Override
	public RealRandomAccess< T > copyRealRandomAccess()
	{
		final MedianRealRandomAccess< T > mrr = new MedianRealRandomAccess< T >( data, outofbounds, radius );
		mrr.setPosition( this );

		return mrr;
	}
}
