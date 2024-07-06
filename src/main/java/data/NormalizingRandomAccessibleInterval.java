package data;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import net.imglib2.Interval;
import net.imglib2.Positionable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPositionable;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;
import util.CompensatedSum;
import util.Threads;
import org.apache.logging.log4j.Logger;
import util.LoggerUtil;

public class NormalizingRandomAccessibleInterval implements RandomAccessibleInterval< DoubleType >
{
	private static final Logger logger = LoggerUtil.getLogger();
	final int n;

	/*
	 * the underlying 2d datastructure that holds all expression values by index, size: [numGenes x numLocations]
	 */
	final RandomAccessibleInterval< DoubleType > input;

	/*
	 * the sum of all genes at each location
	 */
	final Img< DoubleType > sumsPerLocation;

	public NormalizingRandomAccessibleInterval( final RandomAccessibleInterval< DoubleType > input )
	{
		this.n = input.numDimensions();
		this.input = input;
		
		this.sumsPerLocation = ArrayImgs.doubles( input.dimension( 1 ) ); // numLocations in size

		init();
	}

	final protected void init()
	{
		final long numGenes = input.dimension( 0 );
		final long numLocations = input.dimension( 1 );

		logger.info( "Computing normalization sums for all genes (" + input.dimension( 0 ) + ") and locations (" + input.dimension( 1 ) + ") ... " );

		/*
		final Cursor< DoubleType > cursor = this.sumsPerLocation.cursor();

		// for each location do
		for ( long i = 0; i < numLocations; ++i )
		{
			final CompensatedSum realSum = new CompensatedSum( (int)numGenes );

			// iterate and sum all gene expression values of that location
			for ( final DoubleType t : Views.iterable( Views.hyperSlice( input, 1, i ) ) )
				realSum.add( t.get() );

			cursor.next().set( realSum.getSum() );
		}
		*/

		final List< Callable< Void > > tasks = new ArrayList<>();
		final AtomicLong nextLocation = new AtomicLong();

		final int numThreads = Threads.numThreads();
		final ExecutorService executorService = Threads.createFixedExecutorService( numThreads );

		for ( int threadNum = 0; threadNum < numThreads; ++threadNum )
		{
			tasks.add( () -> {
				final RandomAccess< DoubleType > randomAccess = sumsPerLocation.randomAccess();

				// for each location do
				for ( long i = nextLocation.getAndIncrement(); i < numLocations; i = nextLocation.getAndIncrement() )
				{
					final CompensatedSum realSum = new CompensatedSum();

					// iterate and sum all gene expression values of that location
					for ( final DoubleType t : Views.iterable( Views.hyperSlice( input, 1, i ) ) )
						realSum.add( t.get() );

					randomAccess.setPosition( i, 0 );
					randomAccess.get().set( realSum.getSum() );
				}

				return null;
			} );
		}

		try
		{
			final List< Future< Void > > futures = executorService.invokeAll( tasks );
			for ( final Future< Void > future : futures )
				future.get();
		}
		catch ( final InterruptedException | ExecutionException e )
		{
			logger.error("Error during dataset normalization", e);
			throw new RuntimeException( e );
		}

		executorService.shutdown();
	}

	@Override
	public int numDimensions()
	{
		return n;
	}

	@Override
	public RandomAccess< DoubleType > randomAccess()
	{
		return new NormalizingRandomAccess( input.randomAccess(), sumsPerLocation.randomAccess() );
	}

	@Override
	public RandomAccess< DoubleType > randomAccess( Interval interval )
	{
		return randomAccess();
	}

	@Override
	public long min( final int d ) { return input.min( d ); }

	@Override
	public void min( final long[] min ) { input.min( min ); }

	@Override
	public void min( final Positionable min ) { input.min( min ); }

	@Override
	public long max( final int d ) { return input.max( d ); }

	@Override
	public void max( final long[] max ) { input.max( max ); }

	@Override
	public void max( final Positionable max )  { input.max( max ); }

	@Override
	public double realMin( final int d ) { return input.realMin( d ); }

	@Override
	public void realMin( final double[] min ) { input.realMin( min ); }

	@Override
	public void realMin( final RealPositionable min ) { input.realMin( min ); }

	@Override
	public double realMax( final int d ) { return input.realMax( d ); }

	@Override
	public void realMax( final double[] max ) { input.realMax( max ); }

	@Override
	public void realMax( final RealPositionable max ) { input.realMax( max ); }

	@Override
	public void dimensions( final long[] dimensions ) { input.dimensions( dimensions ); }

	@Override
	public long dimension( final int d ) { return input.dimension( d ); }
}
