package util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Threads
{
	public static int numThreads() { return Math.max( 1, Runtime.getRuntime().availableProcessors() ); }

	public static ExecutorService createFlexibleExecutorService( final int nThreads ) { return Executors.newWorkStealingPool( nThreads ); }
	public static ExecutorService createFlexibleExecutorService() { return createFlexibleExecutorService( numThreads() ); }

	public static ExecutorService createFixedExecutorService( final int nThreads ) { return Executors.newFixedThreadPool( nThreads ); }
	public static ExecutorService createFixedExecutorService() { return createFixedExecutorService( numThreads() ); }
}
