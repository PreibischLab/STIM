package cmd;

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import io.SpatialDataContainer;

import align.AlignTools;
import align.GlobalOptSIFT;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Command;
import util.Threads;
import org.apache.logging.log4j.Logger;

import util.Cloud;
import util.LoggerUtil;

@Command(name = "st-align-global", mixinStandardHelpOptions = true, version = "0.3.1", description = "Spatial Transcriptomics as IMages project - global alignment of all slices")
public class GlobalOpt implements Callable<Void> {
	
	private static final Logger logger = LoggerUtil.getLogger();

	@Option(names = {"-c", "--container"}, required = true, description = "input N5 container path, e.g. -i /home/ssq.n5.")
	private String containerPath = null;

	@Option(names = {"-d", "--datasets"}, required = false, description = "ordered, comma separated list of one or more datasets, e.g. -d 'Puck_180528_20,Puck_180528_22' (default: all, in order as saved in N5 metadata)")
	private String datasets = null;

	// general display options
	@Option(names = {"--skipDisplayResults"}, required = false, description = "do not show a preview of the aligned stack (default: false)")
	private boolean skipDisplayResults = false;

	// alignment options
	@Option(names = {"--ignoreQuality"}, required = false, description = "ignore the amount of RANSAC inlier ratio, otherwise used it to determine - if necessary - which pairwise connections to remove during global optimization (default: false)")
	private boolean ignoreQuality = false;

	@Option(names = {"-l", "--lambda"}, required = false, description = "lambda of the affine model regularized with the rigid model, 0.0 means fully affine, 1.0 means just rigid (default: 0.1)")
	private double lambda = 0.1;

	@Option(names = {"--maxAllowedError"}, required = false, description = "maximally allowed error during global optimization (default: 300.0 for slideseq)")
	private double maxAllowedError = 300.0;

	@Option(names = {"--maxIterations"}, required = false, description = "maximum number of iterations (default: 3000)")
	private int maxIterations = 3000;

	@Option(names = {"--minIterations"}, required = false, description = "minimum number of iterations (default: 500)")
	private int minIterations = 500;

	@Option(names = {"--relativeThreshold"}, required = false, description = "relative threshold for dropping pairwise connections, i.e. if the pairwise error is n-times higher than the average error (default: 3.0)")
	private double relativeThreshold = 300.0;

	@Option(names = {"--absoluteThreshold"}, required = false, description = "absolute error threshold for dropping pairwise connections - consult the results of pairwise matching to identify a reasonable number (default: 160.0 for slideseq)")
	private double absoluteThreshold = 160.0;

	// ICP parameters
	@Option(names = {"--skipICP"}, required = false, description = "skip the ICP refinement step (default: false)")
	private boolean skipICP = false;

	@Option(names = {"--icpIterations"}, required = false, description = "maximum number of ICP iterations for each pair of slides (default: 100)")
	private int icpIterations = 100;

	@Option(names = {"--icpErrorFraction"}, required = false, description = "distance at which locations will be assigned as corresponding during ICP, relative to median distance between all locations (default: 1.0)")
	private double icpErrorFraction = 1.0;

	@Option(names = {"--maxAllowedErrorICP"}, required = false, description = "maximum error allowed during ICP runs after model fit - consult the results of pairwise matching to identify a reasonable number (default: 140.0 for slideseq)")
	private double maxAllowedErrorICP = 140.0;

	@Option(names = {"--maxIterationsICP"}, required = false, description = "maximum number of iterations during ICP (default: 500)")
	private int maxIterationsICP = 500;

	@Option(names = {"--minIterationsICP"}, required = false, description = "minimum number of iterations during ICP (default: 500)")
	private int minIterationsICP = 500;

	@Option(names = {"-rf", "--renderingFactor"}, required = false, description = "FOR DISPLAY ONLY: factor for the amount of filtering or radius used for rendering, corresponds to smoothness for Gauss, e.g -rf 2.0 (default: 4.0)")
	private double smoothnessFactor = AlignTools.defaultSmoothnessFactor;

	@Option(names = {"-g", "--gene"}, required = false, description = "FOR DISPLAY ONLY: gene to display, e.g. -g Calm2")
	private String gene = AlignTools.defaultGene;

	@Override
	public Void call() throws Exception {
		if ( Cloud.isFile( URI.create( containerPath ) ) && !(new File(containerPath)).exists()) {
			logger.error("Container '{}' does not exist. Stopping.", containerPath);
			return null;
		}

		if (!SpatialDataContainer.isCompatibleContainer(containerPath)) {
			logger.error("Global alignment does not work for single dataset '{}'. Stopping.", containerPath);
			return null;
		}

		final ExecutorService service = Executors.newFixedThreadPool(8);
		SpatialDataContainer container = SpatialDataContainer.openExisting(containerPath, service);

		final List<String> datasetNames;
		if (datasets != null && !datasets.trim().isEmpty()) {
			datasetNames = Arrays.stream(datasets.split(","))
					.map(String::trim)
					.collect(Collectors.toList());
		}
		else {
			logger.warn("No input datasets specified. Trying to open all datasets in '{}' ...", containerPath);
			datasetNames = container.getDatasets();
		}

		for (final String dataset : datasetNames) {
			if (!container.getDatasets().contains(dataset)) {
				logger.error("Container does not contain dataset '{}' in '{}'. Stopping.", dataset, containerPath);
				return null;
			}
		}

		// -d 'Puck_180602_20,Puck_180602_17'

		final boolean skipDisplayResults = this.skipDisplayResults;
		final boolean useQuality = !ignoreQuality;
		final double lambda = this.lambda;
		final double maxAllowedError = this.maxAllowedError;
		final int maxIterations = this.maxIterations;
		final int maxPlateauwidth = this.minIterations;
		final double relativeThreshold = this.relativeThreshold;
		final double absoluteThreshold = this.absoluteThreshold;

		final boolean doICP = !skipICP;
		final int icpIterations = this.icpIterations;
		final double icpErrorFraction = this.icpErrorFraction;
		final double maxAllowedErrorICP = this.maxAllowedErrorICP;
		final int maxIterationsICP = this.maxIterationsICP;
		final int maxPlateauwhidthICP = this.minIterationsICP;

		GlobalOptSIFT.globalOpt(
				container,
				datasetNames,
				useQuality,
				lambda,
				maxAllowedError,
				maxIterations,
				maxPlateauwidth,
				relativeThreshold,
				absoluteThreshold,
				doICP,
				icpIterations,
				icpErrorFraction,
				maxAllowedErrorICP,
				maxIterationsICP,
				maxPlateauwhidthICP,
				Threads.numThreads(),
				skipDisplayResults,
				smoothnessFactor,
				gene );

		service.shutdown();
		return null;
	}

	public static void main(final String... args) {
		final CommandLine cmd = new CommandLine(new GlobalOpt());
		cmd.execute(args);
	}

}
