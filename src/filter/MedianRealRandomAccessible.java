package filter;

import net.imglib2.IterableRealInterval;
import net.imglib2.type.numeric.RealType;

public class MedianRealRandomAccessible< T extends RealType< T > > extends FilteredRealRandomAccessible< T >
{
	final T outofbounds;
	final double radius;

	public MedianRealRandomAccessible( final IterableRealInterval< T > data, final T outofbounds, final double radius )
	{
		super( data );

		this.outofbounds = outofbounds;
		this.radius = radius;
	}

	@Override
	public MedianRealRandomAccess< T > realRandomAccess()
	{
		return new MedianRealRandomAccess< T >( data, outofbounds, radius );
	}
}
