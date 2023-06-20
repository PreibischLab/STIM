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

public class Normalize implements Callable<Void> {

		@Option(names = {"-c", "--container"}, required = false, description = "N5 container; if given, all datasets are taken from and added to that container")
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
				System.out.println("No input paths defined: " + input + ". Stopping.");
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
				System.out.println("Size of input datasets " + inputDatasets + " not equal to size of output datasets " + outputDatasets + ". Stopping.");
				return null;
			}

			final boolean isStandaloneDataset = (containerPath == null || containerPath.trim().isEmpty());
			final ExecutorService service = Executors.newFixedThreadPool(8);
			SpatialDataContainer container = isStandaloneDataset ? null : SpatialDataContainer.openExisting(containerPath, service);
			SpatialDataIO sdin = null;

			for (int i = 0; i < inputDatasets.size(); i++) {
				final String inputPath = inputDatasets.get(i);
				final String outputPath = outputDatasets.get(i);

				sdin = isStandaloneDataset ? SpatialDataIO.inferFromName(inputPath, service) : container.openDataset(inputPath);
				STDataAssembly stData = sdin.readData();

				if (stData == null) {
					System.out.println("Could not load dataset '" + inputPath + "'. Stopping.");
					return null;
				}

				STDataAssembly normalizedData = new STDataAssembly(new NormalizingSTData(stData.data()),
																   stData.statistics(),
																   stData.transform(),
																   stData.intensityTransform());

				SpatialDataIO sdout = SpatialDataIO.inferFromName(outputPath, service);
				sdout.writeData(normalizedData);
				if (!isStandaloneDataset)
					container.addExistingDataset(outputPath);
			}

			System.out.println("Done.");
			service.shutdown();

			return null;
		}

		public static final void main(final String... args) {
			CommandLine.call(new Normalize(), args);
		}
}
