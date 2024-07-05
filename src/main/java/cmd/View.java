package cmd;

import java.io.File;
import java.util.ArrayList;
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
import util.LoggerUtil;

@Command(name = "st-explorer", mixinStandardHelpOptions = true, version = "0.3.0", description = "Spatial Transcriptomics as IMages project - interactive viewer for ST data")
public class View implements Callable<Void> {
	
	private static final Logger logger = LoggerUtil.getLogger();
	@Option(names = {"-i", "--input"}, required = true, description = "input file or N5 container path, e.g. -i /home/ssq.n5.")
	private String inputPath = null;

	@Option(names = {"-d", "--datasets"}, required = false, description = "if input is a container: comma separated list of datasets, e.g. -d 'Puck_180528_20,Puck_180528_22' (default: open all datasets)")
	private String datasets = null;

	@Override
	public Void call() throws Exception {
		if (!(new File(inputPath)).exists()) {
			logger.error("Container / dataset '" + inputPath + "' does not exist. Stopping.");
			return null;
		}

		final ExecutorService service = Executors.newFixedThreadPool(8);
		final List<STDataAssembly> dataToVisualize = new ArrayList<>();
		if (SpatialDataContainer.isCompatibleContainer(inputPath)) {
			SpatialDataContainer container = SpatialDataContainer.openExisting(inputPath, service);

			if (datasets != null && datasets.trim().length() != 0) {
				for (String dataset : datasets.split(",")) {
					logger.info("Opening dataset '" + dataset + "' in '" + inputPath + "' ...");
					dataToVisualize.add(container.openDataset(dataset.trim()).readData());
				}
			}
			else {
				logger.info("Opening all datasets in '" + inputPath + "' ...");
				for (SpatialDataIO sdio : container.openAllDatasets())
					dataToVisualize.add(sdio.readData());
			}
		}
		else {
			logger.info("Opening dataset '" + inputPath + "' ...");
			dataToVisualize.add(SpatialDataIO.openReadOnly(inputPath, service).readData());
		}

		new STDataExplorer( dataToVisualize, inputPath, SpatialDataContainer.openExisting(inputPath, service).getDatasets() );

		//service.shutdown();
		return null;
	}

	public static final void main(final String... args) {
		CommandLine.call(new View(), args);
	}
}
