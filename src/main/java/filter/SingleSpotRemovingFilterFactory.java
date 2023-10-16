package filter;

import net.imglib2.KDTree;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.type.numeric.RealType;

public class SingleSpotRemovingFilterFactory< T extends RealType< T > > extends RadiusSearchFilterFactory< T, T >
{
	final T outofbounds;

	public SingleSpotRemovingFilterFactory(
			final T outofbounds,
			final double radius )
	{
		super( radius );
		this.outofbounds = outofbounds;
	}

	@Override
	public Filter< T > createFilter( final KDTree< T > tree )
	{
		return new SingleSpotRemovingFilter< T >(
				new RadiusNeighborSearchOnKDTree<>( tree ),
				this,
				outofbounds );
	}

	@Override
	public T create()
	{
		return outofbounds.createVariable();
	}
}
