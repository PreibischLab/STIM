package filter;

import net.imglib2.KDTree;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.type.numeric.RealType;

public class MeanFilterFactory< S extends RealType< S >, T extends RealType< T > > extends RadiusSearchFilterFactory< S, T >
{
	final T outOfBounds;

	public MeanFilterFactory(
			final T outOfBounds,
			final double radius )
	{
		super( radius );
		this.outOfBounds = outOfBounds;
	}

	@Override
	public Filter< T > createFilter( final KDTree< S > tree )
	{
		return new MeanFilter<>(
				new RadiusNeighborSearchOnKDTree<>(tree),
				this,
				outOfBounds);
	}

	@Override
	public T create()
	{
		return outOfBounds.createVariable();
	}
}
