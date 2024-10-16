package cmd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import align.SiftMatch;
import io.SpatialDataContainer;

import align.AlignTools;
import data.STData;
import ij.ImageJ;
import mpicbg.models.AffineModel2D;
import mpicbg.models.InterpolatedAffineModel2D;
import mpicbg.models.PointMatch;
import mpicbg.models.RigidModel2D;
import net.imglib2.realtransform.AffineTransform2D;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Command;
import org.apache.logging.log4j.Logger;

import util.LoggerUtil;

@Command(name = "st-align-pairs-view", mixinStandardHelpOptions = true, version = "0.3.2-SNAPSHOT", description = "Spatial Transcriptomics as IMages project - view and check pairwise alignments")
public class ViewPairwiseAlignment implements Callable<Void> {
	
	private static final Logger logger = LoggerUtil.getLogger();

	@Option(names = {"-c", "--container"}, required = true, description = "input N5 container path, e.g. -i /home/ssq.n5.")
	private String containerPath = null;

	@Option(names = {"-d", "--datasets"}, required = false, description = "comma separated list of datasets, e.g. -d 'Puck_180528_20,Puck_180528_22' (default: open all datasets)")
	private String datasets = null;

	@Option(names = {"-g", "--gene"}, required = true, description = "gene to use for rendering, e.g. -g Calm2")
	private String gene = null;

	@Option(names = {"-s", "--scale"}, required = false, description = "scaling factor rendering the coordinates into images, which highly sample-dependent (default: 0.05 for slideseq data)")
	private double scale = 0.05;

	@Option(names = {"-rf", "--renderingFactor"}, required = false, description = "factor for the amount of filtering or radius used for rendering, corresponds to smoothness for Gauss, e.g -rf 2.0 (default: 1.0)")
	private double smoothnessFactor = 1.0;

	@Option(names = {"-l", "--lambda"}, required = false, description = "for rendering we use a 2D interpolated model (affine/rigid). The number defines the degree of rigidity, fully affine is 0.0, fully rigid is 1.0 (default: 1.0 - rigid)")
	private double model = 1.0;

	@Override
	public Void call() throws Exception {
		if (! SpatialDataContainer.exists(containerPath)) {
			logger.error("Container '{}' does not exist. Stopping.", containerPath);
			return null;
		}

		if (! SpatialDataContainer.isCompatibleContainer(containerPath)) {
			logger.error("Pairwise visualization does not work for single dataset '{}'. Stopping.", containerPath);
			return null;
		}

		final ExecutorService service = Executors.newFixedThreadPool(8);
		SpatialDataContainer container = SpatialDataContainer.openExisting(containerPath, service);

		final List<String> datasetNames;
		if (datasets != null && !datasets.trim().isEmpty()) {
			datasetNames = Arrays.stream(datasets.split(","))
					.map(String::trim)
					.collect(Collectors.toList());
		}
		else {
			logger.warn("No input datasets specified. Trying to open all datasets in '{}' ...", containerPath);
			datasetNames = container.getDatasets();
		}

		final List<STData> dataToVisualize = new ArrayList<>();
		for (final String dataset : datasetNames) {
			logger.info("Opening dataset '{}' in '{}' ...", dataset, containerPath);
			dataToVisualize.add(container.openDatasetReadOnly(dataset).readData().data());
		}

		if ( gene != null && !gene.isEmpty())
			AlignTools.defaultGene = gene;

		new ImageJ();

		for (int i = 0; i < dataToVisualize.size() - 1; ++i)
		{
			for (int j = i + 1; j < dataToVisualize.size(); ++j)
			{
				final String dataset1 = datasetNames.get(i);
				final String dataset2 = datasetNames.get(j);
				final String matchName = container.constructMatchName(dataset1, dataset2);
				if (container.getMatches().contains(matchName))
				{
					final STData stData1 = dataToVisualize.get(i);
					final STData stData2 = dataToVisualize.get(j);

					final InterpolatedAffineModel2D< AffineModel2D, RigidModel2D > m =
							new InterpolatedAffineModel2D<>( new AffineModel2D(), new RigidModel2D(), model );

					SiftMatch loadedMatch = container.loadPairwiseMatch(dataset1, dataset2);

					// reset world coordinates
					for (final PointMatch pm : loadedMatch.getInliers())
					{
						for ( int d = 0; d < pm.getP1().getL().length; ++d )
						{
							pm.getP1().getW()[ d ] = pm.getP1().getL()[ d ];
							pm.getP2().getW()[ d ] = pm.getP2().getL()[ d ];
						}
					}

					m.fit(loadedMatch.getInliers());

					AlignTools.visualizePair(
							stData1, stData2,
							new AffineTransform2D(),
							AlignTools.modelToAffineTransform2D( m ).inverse(),
							smoothnessFactor).setTitle(matchName + "-inliers-" + loadedMatch.getNumInliers());
				}
			}
		}

		service.shutdown();
		return null;
	}

	public static void main(final String... args) {
		final CommandLine cmd = new CommandLine(new ViewPairwiseAlignment());
		cmd.execute(args);
	}

}
