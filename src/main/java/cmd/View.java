package cmd;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import gui.STDataAssembly;
import gui.STDataExplorer;
import io.SpatialDataContainer;
import io.SpatialDataIO;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.apache.logging.log4j.Logger;

import util.Cloud;
import util.LoggerUtil;
import util.Threads;

@Command(name = "st-explorer", mixinStandardHelpOptions = true, version = "0.3.1", description = "Spatial Transcriptomics as IMages project - interactive viewer for ST data")
public class View implements Callable<Void> {
	
	private static final Logger logger = LoggerUtil.getLogger();

	@Option(names = {"-i", "--input"}, required = true, description = "input file or N5 container path, e.g. -i /home/ssq.n5.")
	private String inputPath = null;

	@Option(names = {"-d", "--datasets"}, required = false, description = "if input is a container: comma separated list of datasets, e.g. -d 'Puck_180528_20,Puck_180528_22' (default: open all datasets)")
	private String datasets = null;

	@Override
	public Void call() throws IOException {
		if ( Cloud.isFile( URI.create( inputPath ) ) && !(new File(inputPath)).exists()) {
			logger.error("Container / dataset '{}' does not exist. Stopping.", inputPath);
			return null;
		}

		final ExecutorService service = Executors.newFixedThreadPool(Threads.numThreads());
		final List<STDataAssembly> dataToVisualize = new ArrayList<>();
		final List<String> datasetNames = new ArrayList<>();
		if (SpatialDataContainer.isCompatibleContainer(inputPath)) {
			SpatialDataContainer container = SpatialDataContainer.openExisting(inputPath, service);

			if (datasets != null && !datasets.trim().isEmpty()) {
				Arrays.stream(datasets.split(","))
						.map(String::trim)
						.forEach(datasetNames::add);
			} else {
				datasetNames.addAll(container.getDatasets());
			}

			for (final String dataset : datasetNames) {
				logger.info("Opening dataset '{}' in '{}' ...", dataset, inputPath);
				dataToVisualize.add(container.openDataset(dataset).readData());
			}
		}
		else {
			logger.info("Opening dataset '{}' ...", inputPath);
			dataToVisualize.add(SpatialDataIO.openReadOnly(inputPath, service).readData());
			datasetNames.add(inputPath);
		}

		new STDataExplorer(dataToVisualize, inputPath, datasetNames);

		service.shutdown();
		return null;
	}

	public static void main(final String... args) {
		final CommandLine cmd = new CommandLine(new View());
		cmd.execute(args);
	}
}
