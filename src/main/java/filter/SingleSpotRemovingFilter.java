package filter;

import net.imglib2.RealLocalizable;
import net.imglib2.neighborsearch.RadiusNeighborSearch;
import net.imglib2.type.numeric.RealType;

public class SingleSpotRemovingFilter< T extends RealType< T > > extends RadiusSearchFilter< T, T >
{
	final T outofbounds;

	public SingleSpotRemovingFilter( final RadiusNeighborSearch< T > search, final double radius, final T outofbounds )
	{
		super( search, radius );

		this.outofbounds = outofbounds;
	}

	@Override
	public void filter( final RealLocalizable position, final T output )
	{
		search.search( position, radius, true );

		if ( search.numNeighbors() > 1 )
			output.setReal( search.getSampler( 0 ).get().getRealDouble() );
		else 
			output.set( outofbounds );
	}
}
