package cmd;

import java.util.concurrent.Callable;

import gui.STDataAssembly;
import io.SpatialDataContainer;
import io.SpatialDataIO;

import data.NormalizingSTData;
import picocli.CommandLine;
import picocli.CommandLine.Option;

public class Normalize implements Callable<Void> {

		@Option(names = {"-c", "--container"}, required = false, description = "N5 container; if given, all datasets are taken from and added to that container")
		private String containerPath = null;

		@Option(names = {"-o", "--output"}, required = false, description = "output dataset (default: same as input)")
		private String output = null;

		@Option(names = {"-i", "--input"}, required = true, description = "input dataset, e.g. -i /home/ssq.n5")
		private String input = null;

		@Override
		public Void call() throws Exception {
			if (input == null || input.trim().isEmpty()) {
				System.out.println("No input paths defined: " + input + ". Stopping.");
				return null;
			}

			final boolean outputNameMissing = (output == null || output.trim().isEmpty());
			if (outputNameMissing) {
				int indexOfLastDot = input.lastIndexOf(".");
				if (indexOfLastDot == -1 || indexOfLastDot == 0)
					output = input + "-normed";
				else
					output = input.substring(0, indexOfLastDot) + "-normed" + input.substring(indexOfLastDot);
			}

			SpatialDataContainer container = null;
			SpatialDataIO sdin = null;
			final boolean isStandaloneDataset = (containerPath == null || containerPath.trim().isEmpty());
			if (isStandaloneDataset) {
				sdin = SpatialDataIO.inferFromName(input);
			}
			else {
				container = SpatialDataContainer.openExisting(containerPath);
				sdin = container.openDataset(input);
			}

			STDataAssembly stData = sdin.readData();
			if (stData == null) {
				System.out.println("Could not load dataset '" + input + "'. Stopping.");
				return null;
			}

			STDataAssembly normalizedData = new STDataAssembly(new NormalizingSTData(stData.data()),
															   stData.statistics(),
															   stData.transform(),
															   stData.intensityTransform());

			SpatialDataIO sdout = SpatialDataIO.inferFromName(output);
			sdout.writeData(normalizedData);
			if (!isStandaloneDataset)
				container.addExistingDataset(sdout.getPath());

			System.out.println("Done.");

			return null;
		}

		public static final void main(final String... args) {
			CommandLine.call(new Normalize(), args);
		}
}
