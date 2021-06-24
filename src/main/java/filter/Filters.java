package filter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import mpicbg.models.PointMatch;
import net.imglib2.Cursor;
import net.imglib2.IterableRealInterval;
import net.imglib2.Iterator;
import net.imglib2.KDTree;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealCursor;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPointSampleList;
import net.imglib2.view.Views;

public class Filters
{
	public static < S, T > RealPointSampleList< T > filter( final IterableRealInterval< S > data, final FilterFactory< S, T > filterFactory, ExecutorService service )
	{
		// create full realpointsamplelist
		final RealPointSampleList< T > filtered = new RealPointSampleList<>( data.numDimensions() );

		final RealCursor< S > cursor = data.localizingCursor();

		while ( cursor.hasNext() )
		{
			cursor.fwd();
			filtered.add( new RealPoint( cursor ), filterFactory.create() );
		}

		final KDTree< S > tree = new KDTree<S>( data );

		final List< Callable< Void > > tasks = new ArrayList<>();
		final long blockSize = Math.max( 1000, data.size() / 100 );

		for ( long i = 0; i < data.size(); i += blockSize )
		{
			final long steps = i;

			tasks.add( () ->
			{
				final Filter< T > filter = filterFactory.createFilter( tree );

				final RealCursor< T > outputCursor = filtered.localizingCursor();
				if ( steps > 0 )
					outputCursor.jumpFwd( steps );

				for ( long j = steps; j < Math.min( blockSize + steps, data.size() ); ++j )
				{
					final T value = outputCursor.next();
					filter.filter( outputCursor, value );
				}

				return null;
			});
		}

		try
		{
			final List< Future< Void > > futures = service.invokeAll( tasks );
			for ( final Future< Void > future : futures )
				future.get();
		}
		catch ( final InterruptedException | ExecutionException e )
		{
			e.printStackTrace();
			throw new RuntimeException( e );
		}

		return filtered;
	}

	public static < S, T > RealPointSampleList< T > filter( final IterableRealInterval< S > data, final FilterFactory< S, T > filterFactory )
	{
		return filter( data, data.localizingCursor(), filterFactory );
	}

	public static < S, T, C extends RealLocalizable & Iterator > RealPointSampleList< T > filter(
			final IterableRealInterval< S > data,
			final C outputCursor,
			final FilterFactory< S, T > filterFactory )
	{
		final RealPointSampleList< T > filtered = new RealPointSampleList<>( data.numDimensions() );

		final Filter< T > filter = filterFactory.createFilter( data );

		while ( outputCursor.hasNext() )
		{
			outputCursor.fwd();

			final T value = filterFactory.create();

			filter.filter( outputCursor, value );

			filtered.add( new RealPoint( outputCursor ), value );
		}

		return filtered;
	}

	public static < S, T > void filter(
			final IterableRealInterval< S > data,
			final RandomAccessibleInterval< T > filtered,
			final FilterFactory< S, T > filterFactory )
	{
		final Filter< T > filter = filterFactory.createFilter( data );

		final Cursor< T > cursor = Views.iterable( filtered ).localizingCursor();

		while ( cursor.hasNext() )
		{
			cursor.fwd();
			filter.filter( cursor, cursor.get() );
		}
	}
}
