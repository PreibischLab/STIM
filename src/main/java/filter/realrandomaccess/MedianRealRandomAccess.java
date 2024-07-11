package filter.realrandomaccess;

import filter.MedianFilterFactory;
import net.imglib2.IterableRealInterval;
import net.imglib2.type.numeric.RealType;

public class MedianRealRandomAccess< T extends RealType< T > > extends FilteredRealRandomAccess< T, T >
{
	public MedianRealRandomAccess(
			final IterableRealInterval< T > data,
			final MedianFilterFactory< T > filterFactory )
	{
		super( data, filterFactory );
	}

	public MedianRealRandomAccess< T > copyRealRandomAccess()
	{
		final MedianRealRandomAccess< T > mrr = new MedianRealRandomAccess< T >( data, (MedianFilterFactory< T >)filterFactory );
		mrr.setPosition( this );

		return mrr;
	}
}
