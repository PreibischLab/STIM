package filter;

import net.imglib2.RealLocalizable;
import net.imglib2.neighborsearch.RadiusNeighborSearch;
import net.imglib2.type.numeric.RealType;

/**
 * Visualizes the density at a certain location given the radius
 * 
 * @author spreibi
 *
 * @param <T> - a RealType
 */
public class DensityFilter< T extends RealType< T > > extends RadiusSearchFilter< T, T, DensityFilterFactory< T > >
{
	public DensityFilter( final RadiusNeighborSearch< T > search, final DensityFilterFactory< T > factory )
	{
		super( search, factory );
	}

	@Override
	public void filter( final RealLocalizable position, final T output )
	{
		search.search( position, factory.getRadius(), false );

		output.setReal( search.numNeighbors() );
	}
}
