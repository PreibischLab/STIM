package filter;

import net.imglib2.KDTree;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.type.numeric.RealType;

public class MeanFilterFactory< S extends RealType< S >, T extends RealType< T > > extends RadiusSearchFilterFactory< S, T >
{
	final T outofbounds;

	public MeanFilterFactory(
			final T outofbounds,
			final double radius )
	{
		super( radius );
		this.outofbounds = outofbounds;
	}

	@Override
	public Filter< T > createFilter( final KDTree< S > tree )
	{
		return new MeanFilter< S, T >(
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
