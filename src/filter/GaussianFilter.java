package filter;

import net.imglib2.RealLocalizable;
import net.imglib2.neighborsearch.RadiusNeighborSearch;
import net.imglib2.type.numeric.RealType;

public class GaussianFilter< S extends RealType< S >, T extends RealType< T > > extends RadiusSearchFilter< S, T >
{
	final T outofbounds;
	final boolean normalize;
	final double two_sq_sigma;

	public GaussianFilter(
			final RadiusNeighborSearch< S > search,
			final T outofbounds,
			final double radius,
			final double sigma,
			final boolean normalize )
	{
		super( search, radius );

		this.outofbounds = outofbounds;
		this.normalize = normalize;
		this.two_sq_sigma = 2 * sigma * sigma;
	}

	@Override
	public void filter( final RealLocalizable position, final T output )
	{
		search.search( position, radius, false );

		if ( search.numNeighbors() == 0 )
		{
			output.set( outofbounds );
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
				output.setReal( value / weight );
			else
				output.setReal( value );
		}
	}
}
