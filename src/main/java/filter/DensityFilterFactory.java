package filter;

import net.imglib2.KDTree;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.type.numeric.RealType;

public class DensityFilterFactory< T extends RealType< T > > extends RadiusSearchFilterFactory< T, T >
{
	final T type;

	public DensityFilterFactory( final T type, final double radius )
	{
		super( radius );
		this.type = type;
	}

	@Override
	public Filter< T > createFilter( final KDTree< T > tree )
	{
		return new DensityFilter<>(
				new RadiusNeighborSearchOnKDTree<>(tree),
				this);
	}

	@Override
	public T create()
	{
		return type.createVariable();
	}
}
