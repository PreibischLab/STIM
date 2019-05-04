package render;

import net.imglib2.KDTree;
import net.imglib2.RealLocalizable;
import net.imglib2.type.numeric.RealType;

public class GaussMaxDistanceSearchOnKDTree< T extends RealType< T > > extends AveragingMaxDistanceSearchOnKDTree< T >
{
	final double sigma, two_sq_sigma;
	final boolean normalize;

	public GaussMaxDistanceSearchOnKDTree(
			final KDTree< T > tree,
			final T outofbounds,
			final double maxDistance,
			final double sigma,
			final boolean normalize )
	{
		super( tree, outofbounds, maxDistance );

		this.sigma = sigma;
		this.two_sq_sigma = 2 * sigma * sigma;
		this.normalize = normalize;
	}

	@Override
	public void search( final RealLocalizable p )
	{
		p.localize( pos );

		search.search( position, maxDistance, false );

		if ( search.numNeighbors() == 0 )
		{
			value = oobsSampler;
		}
		else
		{
			double value = 0;
			double weight = 0;

			for ( int i = 0; i < search.numNeighbors(); ++i )
			{
				final double dist = search.getDistance( i );
				final double w = Math.exp( -( dist * dist ) / two_sq_sigma );

				value += search.getSampler( i ).get().getRealDouble() * w;

				if ( normalize )
					weight += w;
			}

			if ( normalize )
				avg.setReal( value / weight );
			else
				avg.setReal( value );

			this.value = avgSampler;
		}
	}

	@Override
	public GaussMaxDistanceSearchOnKDTree< T > copy()
	{
		final GaussMaxDistanceSearchOnKDTree< T > copy = new GaussMaxDistanceSearchOnKDTree< T >( tree, outofbounds, maxDistance, sigma, normalize );
		System.arraycopy( pos, 0, copy.pos, 0, pos.length );
		return copy;
	}

}
