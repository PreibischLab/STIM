package cmd;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import analyze.Entropy;
import analyze.ExtractGeneLists;
import gui.STDataAssembly;
import io.SpatialDataIO;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.real.DoubleType;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.apache.logging.log4j.Logger;
import util.LoggerUtil;

// In the future, this will support more methods for computing the std
@Command(name = "st-add-entropy", mixinStandardHelpOptions = true, version = "0.3.0", description = "Spatial Transcriptomics as IMages project - add annotations to slice-dataset")
public class AddEntropy implements Callable<Void> {

	private static final Logger logger = LoggerUtil.getLogger();

	@Option(names = {"-i", "--input"}, required = true, description = "input dataset, e.g. -i /home/ssq.n5/Puck_180528_20")
	private String inputPath = null;

	@Option(names = {"-m", "--method"}, required = false, description = "method to compute gene entropy")
	private Entropy entropy = Entropy.STDEV;

	@Option(names = {"-gl", "--geneLabel"}, required = false, description = "custom label where to save the computed entropy (if not given: name of the method)")
	private String geneLabels = null;

	@Option(names = {"--numThreads"}, required = false, description = "number of threads for parallel processing")
	private int numThreads = 8;

	@Override
	public Void call() throws Exception {
		if (inputPath == null) {
			logger.error("No input path defined. Stopping.");
			return null;
		}

		final ExecutorService service = Executors.newFixedThreadPool(numThreads);
		final SpatialDataIO sdio = SpatialDataIO.open(inputPath, service);
		final STDataAssembly stData = sdio.readData();

		logger.info("Computing gene variability with method '{}' (might take a while)", entropy.label());
		final RandomAccessibleInterval<DoubleType> entropyValues = ExtractGeneLists.computeOrderedEntropy(stData.data(), entropy, numThreads);

		final String actualLabel = (geneLabels == null) ? entropy.label() : geneLabels;
		stData.data().getGeneAnnotations().put(actualLabel, entropyValues);
		try {
			sdio.updateStoredGeneAnnotations(stData.data().getGeneAnnotations());
		}
		catch (IOException e) {
			throw new IllegalStateException("Trying to write to read-only file.");
		}

		logger.debug( "Done." );

		service.shutdown();
		return null;
	}

	public static void main(final String... args) {
		final CommandLine cmd = new CommandLine(new AddEntropy());
		cmd.execute(args);
	}
}
