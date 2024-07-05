package analyze;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import data.STData;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import util.CompensatedSum;
import util.Threads;

public class ExtractGeneLists
{
	public static ArrayList< Pair< String, Double > > sortByStDevIntensity( final STData data, final int numThreads )
	{
		final ArrayList< Pair< String, Double > > stDev = computeStdev(data, numThreads);
		Collections.sort( stDev, (o1, o2) -> o2.getB().compareTo( o1.getB() ));
		return stDev;
	}

	public static double[] computeEntropy(final String method, final STData stData, final int numThreads) throws IOException {
		final ArrayList<Pair<String, Double>> entropy;
		final double[] entropy_values;

		switch (method) {
			case "stdev":
				entropy = ExtractGeneLists.computeStdev(stData, Math.min(Threads.numThreads(), numThreads) );
				entropy_values = entropy.stream()
										.mapToDouble(pair -> pair.getB())
										.toArray();
				return entropy_values;
			default:
				System.out.println("Error: method " + method + " not supported");
				return null;
		}
	}

	public static ArrayList< Pair< String, Double > > computeStdev( final STData data, final int numThreads )
	{
		final ArrayList< Pair< String, Double > > stDev = new ArrayList<>();

		final AtomicInteger nextGene = new AtomicInteger();
		final List< Callable< List< Pair< String, Double > > > > tasks = new ArrayList<>();

		for ( int threadNum = 0; threadNum < numThreads; ++threadNum )
		{
			tasks.add( () ->
			{
				final List< Pair< String, Double > > stDevLocal = new ArrayList<>();

				for ( int g = nextGene.getAndIncrement(); g < data.getGeneNames().size(); g = nextGene.getAndIncrement() )
				{
					final String gene = data.getGeneNames().get( g );
					final CompensatedSum sum = new CompensatedSum();
					for ( final DoubleType t : data.getExprData( gene ) )
						sum.add( t.get() );
	
					final double avg = sum.getSum() / data.numLocations();
	
					final CompensatedSum stdev = new CompensatedSum();
					for ( final DoubleType t : data.getExprData( gene ) )
						stdev.add( Math.pow( t.get() - avg , 2));

					stDevLocal.add( new ValuePair<>( gene, Math.sqrt( stdev.getSum() / data.numLocations() ) ) );
					// if (g % 1000 == 0){
					// 	System.out.println("Processed " + g + " genes");
					// }
				}

				return stDevLocal;
			});
		}

		final ExecutorService service = Threads.createFixedExecutorService( numThreads );

		try
		{
			final List< Future< List< Pair< String, Double > > > > futures = service.invokeAll( tasks );
			for ( final Future< List< Pair< String, Double > > > future : futures )
				stDev.addAll( future.get() );
		}
		catch ( final InterruptedException | ExecutionException e )
		{
			e.printStackTrace();
			throw new RuntimeException( e );
		}

		service.shutdown();

		return stDev;
	}

	public static ArrayList< Pair< String, Double > > sortByAvgIntensity( final STData data )
	{
		final ArrayList< Pair< String, Double > > avgs = new ArrayList<>();

		for ( final String gene : data.getGeneNames() )
		{
			final CompensatedSum sum = new CompensatedSum();
			for ( final DoubleType t : data.getExprData( gene ) )
				sum.add( t.get() );

			avgs.add( new ValuePair<>( gene, sum.getSum() / data.numLocations() ) );
		}

		Collections.sort( avgs, (o1, o2) -> o2.getB().compareTo( o1.getB() ));

		return avgs;
	}
}
