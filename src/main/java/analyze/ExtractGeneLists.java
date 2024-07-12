package analyze;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import data.STData;
import net.imglib2.Cursor;
import net.imglib2.IterableRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
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
	public static List<Pair<String, Double>> computeEntropy(final STData data, final Entropy entropy, final int numThreads) {

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

	/**
	 * Compute the entropy of each gene in the dataset and return the values in the order of the genes in the dataset.
	 *
	 * @param stData     the dataset
	 * @param entropy    the method to compute the entropy
	 * @param numThreads the number of threads to use
	 * @return the entropy values in the order of the genes in the dataset
	 */
	public static RandomAccessibleInterval<DoubleType> computeOrderedEntropy(final STData stData, final Entropy entropy, final int numThreads) {
		final List<Pair<String, Double>> geneToEntropy = computeEntropy(stData, entropy, numThreads);

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

		final double[] entropyValues = reorderedEntropy.stream()
				.mapToDouble(Pair::getB)
				.toArray();
		return ArrayImgs.doubles(entropyValues, stData.numGenes());
	}

	public static RandomAccessibleInterval<DoubleType> loadGeneEntropy(final STData stData, final String entropyLabel)
			throws IllegalArgumentException {

		Map<String, RandomAccessibleInterval<? extends NativeType<?>>> geneAnnotation = stData.getGeneAnnotations();
		if (!geneAnnotation.containsKey(entropyLabel)) {
			throw new IllegalArgumentException("The property '" + entropyLabel + "' was not found as gene annotation");
		}

		final RandomAccessibleInterval<? extends NativeType<?>> entropyValues = geneAnnotation.get(entropyLabel);
		// TODO: this will blow up if the annotation is not doubles; fix this!
		return (RandomAccessibleInterval<DoubleType>) entropyValues;
	}

	/**
	 * Combine gene names and entropy values into a list of pairs.
	 *
	 * @param stData       the dataset
	 * @param entropyLabel the label of the entropy values
	 * @return a list of pairs with the gene name and the entropy value
	 */
	public static List<Pair<String, Double>> zipNamesAndValues(final STData stData, final String entropyLabel) {
		final List<String> geneNames = stData.getGeneNames();
		RandomAccessibleInterval<DoubleType> entropy = (RandomAccessibleInterval<DoubleType>) stData.getGeneAnnotations().get(entropyLabel);
		final Cursor<DoubleType> cursor = Views.flatIterable(entropy).cursor();
		final List<Pair<String, Double>> list = new ArrayList<>();
		for (String geneName : geneNames) {
			list.add(new ValuePair<>(geneName, cursor.next().getRealDouble()));
		}
		return list;
	}
}
