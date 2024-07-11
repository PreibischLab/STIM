package cmd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import gui.STDataAssembly;
import io.SpatialDataContainer;
import io.SpatialDataIO;

import data.NormalizingSTData;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Command;
import org.apache.logging.log4j.Logger;
import util.LoggerUtil;

@Command(name = "st-normalize", mixinStandardHelpOptions = true, version = "0.3.0", description = "Spatial Transcriptomics as IMages project - normalize dataset")
public class Normalize implements Callable<Void> {
	
		private static final Logger logger = LoggerUtil.getLogger();

		@Option(names = {"-c", "--container"}, required = false, description = "N5 container; if given, datasets are taken from and added to that container")
		private String containerPath = null;

		@Option(names = {"-o", "--output"}, required = false, description = "comma separated list of output datasets (default: same as input)")
		private String output = null;

		@Option(names = {"-i", "--input"}, required = true, description = "comma separated list of input datasets, e.g. -i /home/ssq.n5")
		private String input = null;

		@Override
		public Void call() throws Exception {
			List<String> inputDatasets = (input == null) ? new ArrayList<>() :
					Arrays.stream(input.split(",")).map(String::trim).collect(Collectors.toList());
			if (inputDatasets.isEmpty()) {
				logger.error("No input paths defined: {}. Stopping.", input);
				return null;
			}

			final boolean outputNamesMissing = (output == null || output.trim().isEmpty());
			List<String> outputDatasets = new ArrayList<>();
			if (outputNamesMissing) {
				for (final String dataset : inputDatasets) {
					int indexOfLastDot = dataset.lastIndexOf(".");
					if (indexOfLastDot == -1 || indexOfLastDot == 0)
						outputDatasets.add(dataset + "-normed");
					else
						outputDatasets.add(dataset.substring(0, indexOfLastDot) + "-normed" + dataset.substring(indexOfLastDot));
				}
			}
			else {
				outputDatasets = Arrays.stream(output.split(",")).map(String::trim).collect(Collectors.toList());
			}

			if (outputDatasets.size() != inputDatasets.size()) {
				logger.error("Size of input datasets {} not equal to size of output datasets {}. Stopping.", inputDatasets, outputDatasets);
				return null;
			}

			final boolean isStandaloneDataset = (containerPath == null || containerPath.trim().isEmpty());
			final ExecutorService service = Executors.newFixedThreadPool(8);
			SpatialDataContainer container = isStandaloneDataset ? null : SpatialDataContainer.openExisting(containerPath, service);
			SpatialDataIO sdin;

			for (int i = 0; i < inputDatasets.size(); i++) {
				final String inputPath = inputDatasets.get(i);
				final String outputPath = outputDatasets.get(i);

				sdin = isStandaloneDataset ? SpatialDataIO.openReadOnly(inputPath, service) : container.openDatasetReadOnly(inputPath);
				STDataAssembly stData = sdin.readData();

				if (stData == null) {
					logger.error("Could not load dataset '{}'. Stopping.", inputPath);
					return null;
				}

				STDataAssembly normalizedData = new STDataAssembly(new NormalizingSTData(stData.data()),
																   stData.statistics(),
																   stData.transform() );

				SpatialDataIO sdout = SpatialDataIO.open(outputPath, service);
				sdout.writeData(normalizedData);
				if (!isStandaloneDataset)
					container.addExistingDataset(outputPath);
			}

			logger.debug("Done.");
			service.shutdown();

			return null;
		}

		public static void main(final String... args) {
			final CommandLine cmd = new CommandLine(new Normalize());
			cmd.execute(args);
		}
}
