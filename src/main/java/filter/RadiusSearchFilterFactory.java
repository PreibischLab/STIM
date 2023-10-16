package filter;

import net.imglib2.IterableRealInterval;
import net.imglib2.KDTree;

public abstract class RadiusSearchFilterFactory< S, T > implements FilterFactory< S, T >
{
	double radius;

	public RadiusSearchFilterFactory( final double radius )
	{
		this.radius = radius;
	}

	@Override
	public Filter< T > createFilter( final IterableRealInterval< S > data )
	{
		return createFilter( new KDTree< S >( data ) );
	}

	public abstract Filter< T > createFilter( final KDTree< S > tree );

	/**
	 * @return - the current radius for the search, can be changed dynamically (each instance requests the radius from its factory when using it)
	 */
	public double getRadius() { return radius; }

	/**
	 * @param radius - dynamically change the radius of the filter (each instance requests the radius from its factory when using it)
	 */
	public void setRadius( final double radius ) { this.radius = radius; }
}
