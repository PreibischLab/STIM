package filter;

import net.imglib2.KDTree;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.type.numeric.RealType;

public class MedianFilterFactory< T extends RealType< T > > extends RadiusSearchFilterFactory< T, T >
{
	final T outOfBounds;

	public MedianFilterFactory(
			final T outOfBounds,
			final double radius )
	{
		super( radius );
		this.outOfBounds = outOfBounds;
	}

	@Override
	public Filter< T > createFilter( final KDTree< T > tree )
	{
		return new MedianFilter<>(
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
