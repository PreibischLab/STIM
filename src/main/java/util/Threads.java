package util;

import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.Logger;

public class Threads
{
	private static final Logger logger = LoggerUtil.getLogger();
	public static int numThreads() { return Math.max( 1, Runtime.getRuntime().availableProcessors() ); }

	public static ExecutorService createFlexibleExecutorService( final int nThreads ) { return Executors.newWorkStealingPool( nThreads ); }
	public static ExecutorService createFlexibleExecutorService() { return createFlexibleExecutorService( numThreads() ); }

	public static ExecutorService createFixedExecutorService( final int nThreads ) { return Executors.newFixedThreadPool( nThreads ); }
	public static ExecutorService createFixedExecutorService() { return createFixedExecutorService( numThreads() ); }

	public static Vector<ImagePortion> divideIntoPortions(final long imageSize)
	{
		int numPortions;

		if ( imageSize <= Threads.numThreads() )
			numPortions = (int)imageSize;
		else
			numPortions = Math.max( Threads.numThreads(), (int)( imageSize / (64L * 64L * 64L) ) );

		//System.out.println( "nPortions for copy:" + numPortions );

		final Vector<ImagePortion> portions = new Vector<>();

		if ( imageSize == 0 )
			return portions;

		long threadChunkSize = imageSize / numPortions;

		while ( threadChunkSize == 0 )
		{
			--numPortions;
			threadChunkSize = imageSize / numPortions;
		}

		long threadChunkMod = imageSize % numPortions;

		for ( int portionID = 0; portionID < numPortions; ++portionID )
		{
			// move to the starting position of the current thread
			final long startPosition = portionID * threadChunkSize;

			// the last thread may have to run longer if the number of pixels cannot be divided by the number of threads
			final long loopSize;
			if ( portionID == numPortions - 1 )
				loopSize = threadChunkSize + threadChunkMod;
			else
				loopSize = threadChunkSize;
			
			portions.add( new ImagePortion( startPosition, loopSize ) );
		}
		
		return portions;
	}

	public static void execTasks(final ArrayList<Callable<Void>> tasks, final ExecutorService taskExecutor, final String jobDescription)
	{
		try
		{
			// invokeAll() returns when all tasks are complete
			taskExecutor.invokeAll( tasks );
		}
		catch ( final InterruptedException e )
		{
			logger.error("Failed to {}: ", jobDescription, e);
		}
	}

	public static class ImagePortion
	{
		public ImagePortion( final long startPosition, final long loopSize )
		{
			this.startPosition = startPosition;
			this.loopSize = loopSize;
		}
		
		public long getStartPosition() { return startPosition; }
		public long getLoopSize() { return loopSize; }
		
		protected long startPosition;
		protected long loopSize;
		
		@Override
		public String toString() { return "Portion [" + getStartPosition() + " ... " + ( getStartPosition() + getLoopSize() - 1 ) + " ]"; }
	}
}
