package cmd;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import analyze.ExtractGeneLists;
import gui.STDataAssembly;
import io.SpatialDataIO;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
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
	private String method = "stdev";

	@Option(names = {"-gl", "--geneLabel"}, required = false, description = "custom label where to save the computed entropy")
	private String geneLabels = "stdev";

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
		final ArrayImg<DoubleType, DoubleArray> entropy_values_rai;

		logger.info("Computing gene variability with method '" + method + "' (might take a while)");
		final double[] entropy_values = ExtractGeneLists.computeEntropy(method, stData.data(), numThreads);
		entropy_values_rai = ArrayImgs.doubles(entropy_values, stData.data().numGenes());
		stData.data().getGeneAnnotations().put(geneLabels, entropy_values_rai);
		sdio.updateStoredGeneAnnotations(stData.data().getGeneAnnotations());
	
		logger.debug( "Done." );

		service.shutdown();
		return null;
	}

	public static void main(final String... args) {
		final CommandLine cmd = new CommandLine(new AddEntropy());
		cmd.execute(args);
	}
}
