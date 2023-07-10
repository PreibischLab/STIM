package cmd;

import io.SpatialDataContainer;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Command;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Command(name = "st-add-dataset", mixinStandardHelpOptions = true, version = "0.2.0", description = "Spatial Transcriptomics as IMages project - add slice-dataset to a container")
public class AddSlice implements Callable<Void> {

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
			System.out.println("No container defined. Stopping.");
			return null;
		}

		if (inputDatasetPath == null) {
			System.out.println("No dataset defined. Stopping.");
			return null;
		}

		ExecutorService service = Executors.newFixedThreadPool(1);
		SpatialDataContainer container = new File(containerPath).exists()
				? SpatialDataContainer.openExisting(containerPath, service)
				: SpatialDataContainer.createNew(containerPath, service);

		if (shouldBeMoved)
			container.addExistingDataset(inputDatasetPath, locationPath, exprValPath, annotationPath);
		else
			container.linkExistingDataset(inputDatasetPath, locationPath, exprValPath, annotationPath);

		final String operation = shouldBeMoved ? "Moved" : "Linked";
		System.out.println(operation + " dataset '" + inputDatasetPath + "' to container '" + containerPath + "'.");

		return null;
	}

	public static void main(final String... args) {
		CommandLine.call(new AddSlice(), args);
	}
}
