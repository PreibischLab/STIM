package analyze;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import align.Entropy;
import data.STData;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import util.CompensatedSum;
import util.Threads;
import org.apache.logging.log4j.Logger;
import util.LoggerUtil;

public class ExtractGeneLists
{
	private static final Logger logger = LoggerUtil.getLogger();

	public static ArrayList< Pair< String, Double > > sortByStDevIntensity( final STData data, final int numThreads )
	{
		final ArrayList< Pair< String, Double > > stDev = computeStdev(data, numThreads);
		final Comparator<Pair<String, Double>> byStdDev = Comparator.comparing(Pair::getB);
		stDev.sort(byStdDev.reversed());
		return stDev;
	}

	public static double[] computeEntropy(final Entropy entropy, final STData stData, final int numThreads) {
		final ArrayList<Pair<String, Double>> geneToEntropy;

		switch (entropy) {
			case STDEV:
				geneToEntropy = ExtractGeneLists.computeStdev(stData, Math.min(Threads.numThreads(), numThreads) );
				break;
			default:
				logger.error("Method {} not supported", entropy.label());
				return null;
		}

		// We resort given the current order of genes by creating a hashmap of genes
		Map<String, Pair<String, Double>> pairMap = new HashMap<>();
		for (Pair<String, Double> pair : geneToEntropy) {
			pairMap.put(pair.getA(), pair);
		}

		// and here we do the resorting
		ArrayList<Pair<String, Double>> reorderedEntropy = new ArrayList<>();
		for (String key : stData.getGeneNames()) {
			reorderedEntropy.add(pairMap.get(key));
		}

		return reorderedEntropy.stream()
								.mapToDouble(Pair::getB)
								.toArray();
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
			logger.error("Error computing standard deviation", e);
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

		final Comparator<Pair<String, Double>> byAvg = Comparator.comparing(Pair::getB);
		avgs.sort(byAvg.reversed());

		return avgs;
	}
}
