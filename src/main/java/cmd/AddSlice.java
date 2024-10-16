package cmd;

import io.SpatialDataContainer;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Command;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.Logger;
import util.LoggerUtil;

@Command(name = "st-add-slice", mixinStandardHelpOptions = true, version = "0.3.2-SNAPSHOT", description = "Spatial Transcriptomics as IMages project - add slice-dataset to a container")
public class AddSlice implements Callable<Void> {

	private static final Logger logger = LoggerUtil.getLogger();

	@Option(names = {"-i", "--input"}, required = true, description = "input dataset, e.g. -i /home/ssq.n5")
	private String inputDatasetPath = null;

	@Option(names = {"-c", "--container"}, required = true, description = "container to add dataset to; if it doesn't exist, it will be created")
	private String containerPath = null;

	@Option(names = {"-m", "--move"}, required = false, description = "flag to indicate if dataset should be moved to container; if not, it is linked")
	private boolean shouldBeMoved = false;

	@Option(names = {"-l", "--locations"}, required = false, description = "path to locations within the dataset; if not given, the standard path is assumed")
	private String locationPath = null;

	@Option(names = {"-e", "--expression-values"}, required = false, description = "path to expression values within the dataset; if not given, the standard path is assumed")
	private String exprValPath = null;

	@Option(names = {"-a", "--annotations"}, required = false, description = "path to annotations within the dataset; if not given, the standard path is assumed")
	private String annotationPath = null;


	@Override
	public Void call() throws Exception {
		if (containerPath == null) {
			logger.error("No container defined. Stopping.");
			return null;
		}

		if (inputDatasetPath == null) {
			logger.error("No dataset defined. Stopping.");
			return null;
		}

		final File containerFile = new File(containerPath);
		final boolean containerExists = (containerFile.exists());
		logger.info("Container '{}' {}", containerFile.getAbsolutePath(), containerExists ? "exists" : "is new ...");

		ExecutorService service = Executors.newFixedThreadPool(1);
		SpatialDataContainer container = containerExists
				? SpatialDataContainer.openExisting(containerPath, service)
				: SpatialDataContainer.createNew(containerPath, service);

		if (shouldBeMoved)
			container.addExistingDataset(inputDatasetPath, locationPath, exprValPath, annotationPath, null);
		else
			container.linkExistingDataset(inputDatasetPath, locationPath, exprValPath, annotationPath, null);

		final String operation = shouldBeMoved ? "Moved" : "Linked";
		logger.info("{} dataset '{}' to container '{}'.", operation, inputDatasetPath, containerPath);

		service.shutdown();
		return null;
	}

	public static void main(final String... args) {
		final CommandLine cmd = new CommandLine(new AddSlice());
		cmd.execute(args);
	}
}
