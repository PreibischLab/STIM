package cmd;

import io.SpatialDataContainer;
import io.SpatialDataIO;
import net.imglib2.realtransform.AffineTransform2D;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import util.LoggerUtil;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Extract transformations from container with multiple slices. This is used to extract the transformations from the
 * containers to generate plots for the STIM paper.
 */
@Command(name = "st-extract-transformations", mixinStandardHelpOptions = true, version = "0.3.2-SNAPSHOT", description = "Spatial Transcriptomics as IMages project - extract transformations from slice-dataset")
public class ExtractTransformations implements Callable<Void> {

	private static final Logger logger = LoggerUtil.getLogger();

	@Option(names = {"-c", "--container"}, required = true, description = "input container for which to extract transformations, e.g. -i /home/openst.n5")
	private String containerPath = null;

	@Option(names = {"-o", "--output"}, required = true, description = "output file to store transformations in, e.g. -i /home/transformations-mi.dat")
	private String outputPath = null;

	@Override
	public Void call() throws Exception {
		final ExecutorService executor = Executors.newFixedThreadPool(8);
		final SpatialDataContainer container = SpatialDataContainer.openForReading(containerPath, executor);

		try (final BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
			for (final String dataset : container.getDatasets()) {
				logger.info("Extracting transformations for dataset: {}", dataset);
				final SpatialDataIO sdio = container.openDatasetReadOnly(dataset);
				final AffineTransform2D transform = sdio.readData().transform();

				writer.write(dataset + ";" + transform.toString());
				writer.newLine();
			}
		}

		return null;
	}

	public static void main(final String... args) {
		final CommandLine cmd = new CommandLine(new ExtractTransformations());
		cmd.execute(args);
	}
}
