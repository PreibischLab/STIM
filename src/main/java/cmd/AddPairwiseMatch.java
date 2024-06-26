package cmd;

import align.AlignTools;
import align.PointST;
import align.SiftMatch;
import gui.STDataAssembly;
import ij.ImageJ;
import io.SpatialDataContainer;
import mpicbg.models.AffineModel2D;
import mpicbg.models.InterpolatedAffineModel2D;
import mpicbg.models.PointMatch;
import mpicbg.models.RigidModel2D;
import net.imglib2.realtransform.AffineTransform2D;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Command(name = "st-align-pairs-add", mixinStandardHelpOptions = true, version = "0.3.0", description = "Spatial Transcriptomics as IMages project - add manual landmarks to align pairs of slices")
public class AddPairwiseMatch implements Callable<Void> {

	@Option(names = {"-c", "--container"}, required = true, description = "input N5 container path, e.g. -i /home/ssq.n5.")
	private String containerPath = null;

	@Option(names = {"-d", "--datasets"}, required = true, description = "two datasets separated by a comma, e.g. -d 'Puck_180528_20,Puck_180528_22'")
	private String datasets = null;

	@Option(names = {"-m", "--matches"}, required = true, description = "path to csv file containing pairwise point matches, e.g. -m /home/matches.csv; the rows of the file should be: gene,x1,y1,x2,y2, where dataset1 and dataset2 are given by -d (in order)")
	private String csvPath = null;

	@Option(names = {"-o", "--overwrite"}, required = false, description = "overwrite existing pairwise matches (default: false)")
	private boolean overwrite = false;

	@Option(names = {"--hidePairwiseRendering"}, required = false, description = "do not show pairwise renderings that apply the 2D rigid models based on the given point matches(default: false - showing them)")
	private boolean hidePairwiseRendering = false;

	@Option(names = {"--renderingGene"}, required = false, description = "gene used for visualizing the results, e.g. renderingGene Calm2 (default: Calm2 if present, else first common gene)")
	private String renderingGene = null;

	@Option(names = {"-s", "--scale"}, required = false, description = "scaling factor rendering the coordinates into images, which is highly sample-dependent (default: 0.05 for slideseq data)")
	private double scale = 0.05;

	@Option(names = {"-sf", "--smoothnessFactor"}, required = false, description = "factor for the sigma of the gaussian used for rendering, corresponds to smoothness, e.g -sf 2.0 (default: 4.0)")
	private double smoothnessFactor = 4.0;

	@Option(names = {"-l", "--lambda"}, required = false, description = "for rendering we use a 2D interpolated model (affine/rigid). The number defines the degree of rigidity, fully affine is 0.0, fully rigid is 1.0 (default: 1.0 - rigid)")
	private double lambda = 1.0;

	@Override
	public Void call() throws Exception {
		if (!(new File(containerPath)).exists()) {
			System.out.println("Container '" + containerPath + "' does not exist. Stopping.");
			return null;
		}

		if (!SpatialDataContainer.isCompatibleContainer(containerPath)) {
			System.out.println("Pairwise alignment does not work for single dataset '" + containerPath + "'. Stopping.");
			return null;
		}

		final ExecutorService service = Executors.newFixedThreadPool(8);
		SpatialDataContainer container = SpatialDataContainer.openExisting(containerPath, service);

		final String[] datasetAB = datasets.split(",");
		if (datasetAB.length != 2) {
			System.out.println("Exactly two datasets must be specified, separated by a comma. Instead, '" + datasets + "' was given. Stopping.");
			return null;
		}
		final String datasetA = datasetAB[0].trim();
		final String datasetB = datasetAB[1].trim();
		final String matchName = container.constructMatchName(datasetA, datasetB);

		for (final String dataset : datasetAB) {
			if (!container.getDatasets().contains(dataset)) {
				System.out.println("Container does not contain dataset '" + dataset + "'. Stopping.");
				return null;
			}
		}

		ArrayList<PointMatch> pointMatches = new ArrayList<>();
		try (final BufferedReader reader = new BufferedReader(new FileReader(csvPath))) {
			String line, gene;
			while ((line = reader.readLine()) != null) {
				final String[] coords = line.split(",");
				if (coords.length != 5) {
					System.out.println("Line '" + line + "' does not contain exactly 5 comma-separated coordinates. Stopping.");
					return null;
				}

				gene = coords[0].trim();
				pointMatches.add(
						new PointMatch(
								new PointST(new double[]{Double.parseDouble(coords[1]), Double.parseDouble(coords[2])}, gene),
								new PointST(new double[]{Double.parseDouble(coords[3]), Double.parseDouble(coords[4])}, gene)));
			}
		} catch (final Exception e) {
			System.out.println("Could not read csv file '" + csvPath + "':\n" + e.getMessage() + "\nStopping.");
			return null;
		}

		final SiftMatch match = new SiftMatch(datasetA, datasetB, pointMatches.size(), pointMatches);

		final boolean visualizeResult = !hidePairwiseRendering;
		if (visualizeResult) {
			final STDataAssembly stDataA = container.openDataset(datasetA).readData();
			final STDataAssembly stDataB = container.openDataset(datasetB).readData();

			if (renderingGene == null) {
				final List<String> genesA = stDataA.data().getGeneNames();
				final List<String> genesB = stDataB.data().getGeneNames();
				renderingGene = chooseRenderingGene(genesA, genesB);
			}

			if (renderingGene == null) {
				System.out.println("Could not find a common gene to visualize between the two datasets. Stopping.");
				return null;
			}

			final InterpolatedAffineModel2D<AffineModel2D, RigidModel2D > m =
					new InterpolatedAffineModel2D<>(new AffineModel2D(), new RigidModel2D(), lambda);
			m.fit(match.getInliers());

			new ImageJ();

			AlignTools.defaultGene = renderingGene;
			AlignTools.defaultScale = scale;
			AlignTools.visualizePair(
							stDataA.data(), stDataB.data(),
							new AffineTransform2D(),
							AlignTools.modelToAffineTransform2D(m).inverse(),
							smoothnessFactor)
					.setTitle(matchName + "-inliers-" + match.getNumInliers());
		}

		System.out.println("Adding pairwise match '" + matchName + "' to container '" + containerPath + "'...");
		if (container.getMatches().contains(matchName)) {
			if (overwrite) {
				System.out.println("Overwriting existing pairwise match '" + matchName + "'...");
				container.deleteMatch(matchName);
			} else {
				System.out.println("Pairwise match '" + matchName + "' already exists. Stopping.");
				return null;
			}
		}
		container.savePairwiseMatch(match);

		service.shutdown();
		return null;
	}

	protected static String chooseRenderingGene(List<String> genesA, List<String> genesB) {
		String renderingGene = null;
		if (genesA.contains("Calm2") && genesB.contains("Calm2")) {
			renderingGene = "Calm2";
		} else {
			for (String gene : genesA) {
				if (genesB.contains(gene)) {
					renderingGene = gene;
					break;
				}
			}
		}
		return renderingGene;
	}

	public static void main(final String... args) {
		CommandLine.call(new AddPairwiseMatch(), args);
	}

}
