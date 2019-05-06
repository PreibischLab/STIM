package filter.realrandomaccess;

import filter.MedianFilterFactory;
import net.imglib2.IterableRealInterval;
import net.imglib2.type.numeric.RealType;

public class MedianRealRandomAccessible< T extends RealType< T > > extends FilteredRealRandomAccessible< T, T >
{
	public MedianRealRandomAccessible( final IterableRealInterval< T > data, final T outofbounds, final double radius )
	{
		super( data, new MedianFilterFactory<>( outofbounds, radius ) );
	}

	@Override
	public MedianRealRandomAccess< T > realRandomAccess()
	{
		return new MedianRealRandomAccess< T >( data, (MedianFilterFactory< T >)filterFactory );
	}
}
