package cmd;

import align.SiftMatch;
import io.SpatialDataContainer;
import io.SpatialDataIO;
import mpicbg.models.AffineModel2D;
import mpicbg.models.InterpolatedAffineModel2D;
import mpicbg.models.PointMatch;
import mpicbg.models.RigidModel2D;
import mpicbg.models.SimilarityModel2D;
import net.imglib2.realtransform.AffineTransform2D;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import util.LoggerUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Short script that extracts the relative transformations between two slices computed for different parameters.
 * Containers have a name 'scaleXXXX_rfYYY', where XXXX is the scale and YYY is the render factor.
 */
public class ExtractPairwiseTransformations implements Callable<Void> {

	@Option(names = {"-i", "--input"}, required = true, description = "base path to the parameter scan")
	private String basePath = null;

	@Option(names = {"-o", "--output"}, required = true, description = "output file to store relative transformations in, e.g. -i /home/comparison.csv")
	private String outputPath = null;

	@Option(names = {"-b", "--baseline"}, required = true, description = "baseline transformations to compare against, e.g. -i /home/transformations-baseline.dat")
	private String baselinePath = null;

	@Option(names = {"-c", "--container"}, required = true, description = "input container in which the datasets exist, e.g. -i /home/openst.n5")
	private String containerPath = null;

	private static final Logger logger = LoggerUtil.getLogger();

	private static final Pattern folderNamePattern = Pattern.compile("scale\\d{4}_rf\\d{3}");

	public Void call() throws Exception {
		// get all matching folders in the input directory
		final File inputDir = new File(basePath);
		final String[] containers = inputDir.list((dir, name) -> folderNamePattern.matcher(name).matches());

		if (containers == null || containers.length == 0) {
			System.err.println("No matching folders found in input directory: " + basePath);
			return null;
		}

		logger.info("Found {} matching folders in input directory: {}", containers.length, basePath);

		// get the dataset name from the first container (assume that it's allways the same dataset)
		final ExecutorService executor = Executors.newFixedThreadPool(8);
		SpatialDataContainer container = openContainer(containers[0], inputDir, executor);
		SiftMatch siftMatch = getSiftMatch(container);
		String dataset = siftMatch.getStDataBName();

		final SpatialDataContainer fullContainer = SpatialDataContainer.openForReading(containerPath, executor);
		final String previousDataset = CompareTransformations.getPreviousDataset(dataset, fullContainer.getDatasets());

		final List<String> baselineDatasets = new ArrayList<>();
		final Map<String, AffineTransform2D> baselineTransformations = new HashMap<>();
		CompareTransformations.readTransformations(baselinePath, baselineDatasets, baselineTransformations);

		// open output file and write header
		final BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath));
		writer.write("scale,renderFactor,count,mean,std\n");

		for (final String containerName : containers) {
			// extract affine model from match data
			logger.info("Processing container: {}", containerName);
			container = openContainer(containerName, inputDir, executor);
			siftMatch = getSiftMatch(container);
			dataset = siftMatch.getStDataBName();
			final List<PointMatch> pointMatches = siftMatch.getInliers();

			final InterpolatedAffineModel2D<SimilarityModel2D, RigidModel2D> model = new InterpolatedAffineModel2D<>(new SimilarityModel2D(), new RigidModel2D(), 0.1);
			model.fit(pointMatches);
			final AffineTransform2D target = affineFromModel(model.createAffineModel2D());

			final AffineTransform2D baseline = baselineTransformations.get(dataset);
			final AffineTransform2D previousBaseline = baselineTransformations.get(previousDataset) == null ? new AffineTransform2D() : baselineTransformations.get(previousDataset);

			final AffineTransform2D relativeTransform = new AffineTransform2D();
			relativeTransform.preConcatenate(target);
			relativeTransform.preConcatenate(baseline.inverse());
			relativeTransform.preConcatenate(previousBaseline);

			// compute error
			final SpatialDataIO sdio = fullContainer.openDatasetReadOnly(dataset);
			final List<double[]> locations = sdio.readData().data().getLocationsCopy();
			final double[] distances = CompareTransformations.computeDistances(locations, relativeTransform);

			final double count = distances.length;
			final double mean = Arrays.stream(distances).average().orElse(0);
			final double std = Math.sqrt(Arrays.stream(distances).map(d -> Math.pow(d - mean, 2)).average().orElse(0));

			// read the error from the temporary file and print to console
			final double scale = Integer.parseInt(containerName.substring(5, 9)) / 1000.0;
			final double renderFactor = Integer.parseInt(containerName.substring(12, 15)) / 100.0;
			writer.write(scale + "," + renderFactor + "," + count + "," + mean + "," + std + "\n");
		}

		writer.close();
		return null;
	}

	private static SiftMatch getSiftMatch(SpatialDataContainer container) throws ClassNotFoundException {
		final String matchNames = container.getMatches().get(0);
		final String[] parts = matchNames.split("-");
		return container.loadPairwiseMatch(parts[0], parts[1]);
	}

	private static SpatialDataContainer openContainer(String containerName, File inputDir, ExecutorService executor) throws IOException {
		final String containerPath = inputDir.toPath().resolve(containerName).toString();
		return SpatialDataContainer.openForReading(containerPath, executor);
	}

	private static AffineTransform2D affineFromModel(final AffineModel2D model) {
		final double[] params = new double[6];
		model.toArray(params);

		// permute parameters to match AffineTransform2D
		final double[] permuted = new double[] {params[0], params[2], params[4], params[1], params[3], params[5]};

		final AffineTransform2D affine = new AffineTransform2D();
		affine.set(permuted);
		return affine;
	}

	public static void main(final String... args) {
		final CommandLine cmd = new CommandLine(new ExtractPairwiseTransformations());
		cmd.execute(args);
	}
}
