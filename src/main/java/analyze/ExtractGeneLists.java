package analyze;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import data.STData;
import net.imglib2.IterableRealInterval;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import util.Threads;
import org.apache.logging.log4j.Logger;
import util.LoggerUtil;

public class ExtractGeneLists
{
	private static final Logger logger = LoggerUtil.getLogger();

	/**
	 * Compute the entropy of each gene in the dataset using a given method.
	 *
	 * @param data       the dataset
	 * @param entropy    the method to compute the entropy
	 * @param numThreads the number of threads to use
	 * @return a list of pairs with the gene name and the entropy value
	 */
	public static List<Pair<String, Double>> computeEntropyNew(final STData data, final Entropy entropy, final int numThreads) {

		final ExecutorService service = Threads.createFixedExecutorService(numThreads);
		final List<Future<Pair<String, Double>>> futures = data.getGeneNames().stream()
				.map(gene -> service.submit(() -> computeSingleGeneEntropy(data, gene, entropy)))
				.collect(Collectors.toList());

		final List<Pair<String, Double>> geneToEntropy = new ArrayList<>();
		try {
			for (final Future<Pair<String, Double>> future : futures) {
				geneToEntropy.add(future.get());
			}
		} catch (final InterruptedException | ExecutionException e) {
			logger.error("Error computing standard deviation", e);
			throw new RuntimeException(e);
		} finally {
			service.shutdown();
		}

		return geneToEntropy;
	}

	private static Pair<String, Double> computeSingleGeneEntropy(final STData data, final String gene, final Entropy entropy) {
		final IterableRealInterval<DoubleType> expressionData = data.getExprData(gene);
		final double entropyValue = entropy.computeFor(expressionData);
		return new ValuePair<>(gene, entropyValue);
	}

	public static List<Pair<String, Double>> sortByEntropy(final STData data, final Entropy entropy, final int numThreads) {
		final List<Pair<String, Double>> geneToEntropy = computeEntropyNew(data, entropy, numThreads);
		final Comparator<Pair<String, Double>> byEntropy = Comparator.comparing(Pair::getB);
		geneToEntropy.sort(byEntropy.reversed());
		return geneToEntropy;
	}

	public static ArrayList< Pair< String, Double > > sortByStDevIntensity( final STData data, final int numThreads )
	{
		return new ArrayList<>(sortByEntropy(data, Entropy.STDEV, numThreads));
	}

	public static double[] computeEntropy(final Entropy entropy, final STData stData, final int numThreads) {
		final List<Pair<String, Double>> geneToEntropy = computeEntropyNew(stData, entropy, numThreads);

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
}
