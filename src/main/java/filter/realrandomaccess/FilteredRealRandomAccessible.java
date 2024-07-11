package filter.realrandomaccess;

import filter.FilterFactory;
import net.imglib2.IterableRealInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;

public class FilteredRealRandomAccessible< S, T > implements RealRandomAccessible< T >
{
	final IterableRealInterval< S > data;
	final FilterFactory< S, T > filterFactory;

	public FilteredRealRandomAccessible(
			final IterableRealInterval< S > data,
			final FilterFactory< S, T > filterFactory )
	{
		this.data = data;
		this.filterFactory = filterFactory;
	}

	@Override
	public RealRandomAccess< T > realRandomAccess()
	{
		return new FilteredRealRandomAccess<>(data, filterFactory);
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
