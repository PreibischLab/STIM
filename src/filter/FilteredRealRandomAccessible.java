package filter;

import net.imglib2.IterableRealInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;

public abstract class FilteredRealRandomAccessible< T > implements RealRandomAccessible< T >
{
	final IterableRealInterval< T > data;

	public FilteredRealRandomAccessible( final IterableRealInterval< T > data )
	{
		this.data = data;
	}

	@Override
	public int numDimensions()
	{
		return data.numDimensions();
	}

	@Override
	public RealRandomAccess< T > realRandomAccess( final RealInterval interval )
	{
		return realRandomAccess();
	}
}
