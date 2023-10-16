package filter;

import net.imglib2.RealLocalizable;
import net.imglib2.neighborsearch.RadiusNeighborSearch;
import net.imglib2.type.numeric.RealType;

public class MeanFilter< S extends RealType< S >, T extends RealType< T > > extends RadiusSearchFilter< S, T, MeanFilterFactory< S, T > >
{
	final T outofbounds;

	public MeanFilter(
			final RadiusNeighborSearch< S > search,
			final MeanFilterFactory< S, T > factory,
			final T outofbounds )
	{
		super( search, factory );

		this.outofbounds = outofbounds;
	}

	@Override
	public void filter( final RealLocalizable position, final T output )
	{
		search.search( position, factory.getRadius(), false );

		if ( search.numNeighbors() == 0 )
		{
			output.set( outofbounds );
		}
		else if ( search.numNeighbors() == 1 )
		{
			output.setReal( search.getSampler( 0 ).get().getRealDouble() );
		}
		else
		{
			double value = 0;

			for ( int i = 0; i < search.numNeighbors(); ++i )
				value += search.getSampler( i ).get().getRealDouble();

			output.setReal( value / search.numNeighbors() );
		}
	}
}
