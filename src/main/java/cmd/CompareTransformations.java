package cmd;

import io.SpatialDataContainer;
import io.SpatialDataIO;
import net.imglib2.realtransform.AffineTransform2D;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import util.LoggerUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compare transformations for datasets given as a csv file. A line in the csv file should have the following format:
 * dataset-name;2d-affine: (a,b,c,d,e,f)
 * where dataset-name is the name of the dataset and a-f are the elements of the 2d affine transformation matrix.
 * The transformations are compared based on the locations of the corresponding datasets.
 */
public class CompareTransformations implements Callable<Void> {

	private static final Logger logger = LoggerUtil.getLogger();

	@Option(names = {"-c", "--container"}, required = true, description = "input container in which the datasets exist, e.g. -i /home/openst.n5")
	private String containerPath = null;

	@Option(names = {"-o", "--output"}, required = true, description = "output file to store relative transformation quality in, e.g. -i /home/comparison.csv")
	private String outputPath = null;

	@Option(names = {"-b", "--baseline"}, required = true, description = "baseline transformations to compare against, e.g. -i /home/transformations-baseline.dat")
	private String baselinePath = null;

	@Option(names = {"-t", "--transformations"}, required = true, description = "transformations that should be compared against the baseline, e.g. -i /home/transformations.dat")
	private String transformationPath = null;

	private static final Pattern transformationPattern = Pattern.compile("2d-affine: \\((.*)\\)");

	public Void call() throws Exception {
		final ExecutorService executor = Executors.newFixedThreadPool(8);
		final SpatialDataContainer container = SpatialDataContainer.openForReading(containerPath, executor);

		// read transformations from files
		final List<String> baselineDatasets = new ArrayList<>();
		final Map<String, AffineTransform2D> baselineTransformations = new HashMap<>();
		final List<String> targetDatasets = new ArrayList<>();
		final Map<String, AffineTransform2D> targetTransformations = new HashMap<>();

		readTransformations(baselinePath, baselineDatasets, baselineTransformations);
		readTransformations(transformationPath, targetDatasets, targetTransformations);

		// get all datasets as sorted list to be able to identify previous dataset
		final List<String> allDatasets = container.getDatasets();
		Collections.sort(allDatasets);

		// write header
		final BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath));
		writer.write("previousDataset,dataset,nPoints,meanError,stdError\n");

		for (final String dataset : targetDatasets) {
			logger.info("Comparing transformations for dataset: {}", dataset);

			// find previous dataset
			final int index = allDatasets.indexOf(dataset);
			final String previousDataset;
			if (index < 0) {
				throw new IllegalArgumentException("Dataset " + dataset + " not found in container");
			} else if (index == 0) {
				// first dataset; since we assume that this is fixed, the error is 0 by definition
				continue;
			} else {
				previousDataset = allDatasets.get(index - 1);
			}

			// get transformations (previous transformations can be missing, in which case we assume the identity)
			final AffineTransform2D baseline = baselineTransformations.get(dataset);
			final AffineTransform2D target = targetTransformations.get(dataset);

			final AffineTransform2D previousBaseline = baselineTransformations.get(previousDataset) == null ? new AffineTransform2D() : baselineTransformations.get(previousDataset);
			final AffineTransform2D previousTarget = targetTransformations.get(previousDataset) == null ? new AffineTransform2D() : targetTransformations.get(previousDataset);

			// compute relative transformation quality
			final AffineTransform2D relativeTransform = new AffineTransform2D();
			relativeTransform.preConcatenate(previousTarget.inverse());
			relativeTransform.preConcatenate(target);
			relativeTransform.preConcatenate(baseline.inverse());
			relativeTransform.preConcatenate(previousBaseline);

			// compute error
			final SpatialDataIO sdio = container.openDatasetReadOnly(dataset);
			final List<double[]> locations = sdio.readData().data().getLocationsCopy();
			final double[] transformed = new double[2];

			final double[] distances = locations.stream()
					.map(location -> {
						relativeTransform.apply(location, transformed);
						return distance(location, transformed);
					})
					.mapToDouble(Double::doubleValue)
					.toArray();

			final double count = distances.length;
			final double mean = Arrays.stream(distances).average().orElse(0);
			final double std = Math.sqrt(Arrays.stream(distances).map(d -> Math.pow(d - mean, 2)).average().orElse(0));

			writer.write(previousDataset + "," + dataset + "," + count + "," + mean + "," + std + "\n");
		}

		writer.close();
		return null;
	}

	public static double distance(final double[] a, final double[] b) {
		double sum = 0;
		for (int i = 0; i < a.length; i++) {
			final double diff = a[i] - b[i];
			sum += diff * diff;
		}
		return Math.sqrt(sum);
	}

	private static void readTransformations(final String path, final List<String> datasets, final Map<String, AffineTransform2D> transformations) throws IOException {
		try (final BufferedReader reader = new BufferedReader(new FileReader(path))) {
			String line;
			while ((line = reader.readLine()) != null) {
				final String[] parts = line.split(";");
				final String dataset = parts[0];
				final String serializedTransform = parts[1];

				final Matcher matcher = transformationPattern.matcher(serializedTransform);
				if (!matcher.matches()) {
					throw new IllegalArgumentException("Could not parse transformation: " + serializedTransform);
				}

				final double[] matrix = Arrays.stream(matcher.group(1).split(",")).mapToDouble(Double::parseDouble).toArray();
				final AffineTransform2D transform = new AffineTransform2D();
				transform.set(matrix);

				System.out.println("dataset: " + dataset);
				System.out.println("transform: " + transform);

				datasets.add(dataset);
				transformations.put(dataset, transform);
			}
		}
	}

	public static void main(final String... args) {
		final CommandLine cmd = new CommandLine(new CompareTransformations());
		cmd.execute(args);
	}
}
