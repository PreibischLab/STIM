package cmd;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import gui.STDataAssembly;
import io.SpatialDataContainer;
import org.joml.Math;

import align.AlignTools;
import align.Pairwise;
import align.PairwiseSIFT;
import align.PairwiseSIFT.SIFTParam;
import align.SiftMatch;
import data.STData;
import ij.ImageJ;
import mpicbg.models.RigidModel2D;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Command;
import util.Threads;

@Command(name = "st-align-pairs", mixinStandardHelpOptions = true, version = "0.2.0", description = "Spatial Transcriptomics as IMages project - align pairs of slices")
public class PairwiseSectionAligner implements Callable<Void> {

	@Option(names = {"-c", "--container"}, required = true, description = "input N5 container path, e.g. -i /home/ssq.n5.")
	private String containerPath = null;

	@Option(names = {"-d", "--datasets"}, required = false, description = "ordered, comma separated list of one or more datasets, e.g. -d 'Puck_180528_20,Puck_180528_22' (default: all, in order as saved in N5 metadata)")
	private String datasets = null;

	//@Option(names = {"-l", "--loadGenes"}, required = false, description = "load a plain text file with gene names")
	//private String loadGenes = null;

	//@Option(names = {"-s", "--saveGenes"}, required = false, description = "save a plain text file with gene names that were used (can be later imported with -l)")
	//private String saveGenes = null;

	@Option(names = {"-o", "--overwrite"}, required = false, description = "overwrite existing pairwise matches (default: false)")
	private boolean overwrite = false;

	// rendering parameters
	@Option(names = {"-s", "--scale"}, required = false, description = "scaling factor rendering the coordinates into images, which highly sample-dependent (default: 0.05 for slideseq data)")
	private double scale = 0.05;

	@Option(names = {"-sf", "--smoothnessFactor"}, required = false, description = "factor for the sigma of the gaussian used for rendering, corresponds to smoothness, e.g -sf 2.0 (default: 4.0)")
	private double smoothnessFactor = 4.0;

	@Option(names = {"--renderingGene"}, required = false, description = "genes used for visualizing the results, e.g. renderingGene Calm2 (default: first gene in the list)")
	private String renderingGene = null;

	// alignment parameters
	@Option(names = {"-r", "--range"}, required = false, description = "range in which pairs of datasets will be aligned, therefore the order in -d is important (default: 2)")
	private int range = 2;

	@Option(names = {"-g", "--genes"}, required = false, description = "comma separated list of one or more genes to be used (on top of numGenes, which can be set to 0 if only selected genes should be used), e.g. -g 'Calm2,Hpca,Ptgds'")
	private String genes = null;

	@Option(names = {"-n", "--numGenes"}, required = false, description = "use N number of genes that have the highest entropy (default: 100)")
	private int numGenes = 100;

	@Option(names = {"-e", "--maxEpsilon"}, required = false, description = "maximally allowed alignment error (in global space, independent of scaling factor) for SIFT on a 2D rigid model (default: 10 times the average distance between sequenced locations)")
	private double maxEpsilon = -Double.MAX_VALUE;

	@Option(names = {"--minNumInliers"}, required = false, description = "minimal number of inliers across all tested genes that support the same 2D rigid model (default: 30 for slideseq)")
	private int minNumInliers = 30;

	@Option(names = {"--minNumInliersGene"}, required = false, description = "minimal number of inliers for each gene that support the same 2D rigid model (default: 5 for slideseq)")
	private int minNumInliersGene = 5;

	@Option(names = {"--hidePairwiseRendering"}, required = false, description = "do not show pairwise renderings that apply the 2D rigid models (default: false - showing them)")
	private boolean hidePairwiseRendering = false;

	//-i /Users/spreibi/Documents/BIMSB/Publications/imglib2-st/slide-seq-test.n5 -d 'Puck_180602_20,Puck_180602_18,Puck_180602_17,Puck_180602_16,Puck_180602_15,Puck_180531_23,Puck_180531_22,Puck_180531_19,Puck_180531_18,Puck_180531_17,Puck_180531_13,Puck_180528_22,Puck_180528_20' -n 100 --overwrite

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

		final List<String> datasetNames;
		if (datasets != null && datasets.trim().length() != 0) {
			datasetNames = Arrays.stream(datasets.split(","))
					.map(String::trim)
					.collect(Collectors.toList());

			if (datasetNames.size() == 1) {
				System.out.println("Only one single dataset found (" + datasetNames.get(0) + "). Stopping.");
				return null;
			}
		}
		else {
			// TODO: should this really continue? the order of the datasets matters, but getDatasets() returns random order
			System.out.println("No input datasets specified. Trying to open all datasets in '" + containerPath + "' ...");
			datasetNames = container.getDatasets();
		}

		final List<STDataAssembly> dataToAlign = new ArrayList<>();
		for (final String dataset : datasetNames) {
			System.out.println("Opening dataset '" + dataset + "' in '" + containerPath + "' ...");
			dataToAlign.add(container.openDataset(dataset).readData());
		}

		if (maxEpsilon <= 0.0) {
			maxEpsilon = 10 * dataToAlign.stream()
					.mapToDouble((data) -> data.statistics().getMeanDistance())
					.summaryStatistics().getAverage();
			System.out.println("Parameter maxEpsilon is unset or negative; using 10 * average distance between sequenced locations = " + maxEpsilon);
		}

		// iterate once just to be sure we will not crash half way through because something exists
		List<String> matches = container.getMatches();
		for ( int i = 0; i < dataToAlign.size() - 1; ++i ) {
			for ( int j = i + 1; j < dataToAlign.size(); ++j ) {
				if ( Math.abs( j - i ) > range )
					continue;

				// clear the alignment metadata
				final String pairwiseMatchName = container.constructMatchName(datasetNames.get( i ), datasetNames.get( j ));
				if (matches.contains(pairwiseMatchName)) {
					if ( overwrite ) {
						System.out.println("Overwriting previous results for: " + pairwiseMatchName);
						container.deleteMatch(pairwiseMatchName);
					}
					else {
						System.out.println("Previous results exist '" + pairwiseMatchName + "', stopping. [Rerun with --overwrite for automatic deletion of previouse results]");
						return null;
					}
				}
				else {
					System.out.println("To align: " + pairwiseMatchName);
				}
			}
		}

		if ( !hidePairwiseRendering )
			new ImageJ();

		for ( int i = 0; i < dataToAlign.size() - 1; ++i ) {
			for ( int j = i + 1; j < dataToAlign.size(); ++j ) {
				if ( Math.abs( j - i ) > range )
					continue;

				final STData stData1 = dataToAlign.get( i ).data();
				final STData stData2 = dataToAlign.get( j ).data();
				final String dataset1 = datasetNames.get( i );
				final String dataset2 = datasetNames.get( j );

				System.out.println( "Processing " + dataset1 + " <> " + dataset2 );

				//
				// assemble genes to test
				//
				System.out.println( "Assembling genes for alignment (" + numGenes + " genes)... ");
		
				final HashSet< String > genesToTest = new HashSet<>( Pairwise.genesToTest( stData1, stData2, numGenes, Threads.numThreads() ) );
		
				if ( numGenes > 0 )
					System.out.println( "Automatically identified " + genesToTest.size() + " genes for alignment" );
		
				if ( genes != null && genes.length() > 0 )
				{
					HashSet< String > genes1 = new HashSet<>( stData1.getGeneNames() );
					HashSet< String > genes2 = new HashSet<>( stData2.getGeneNames() );
		
					for ( final String gene : genes.trim().split( "," ) )
					{
						String name = gene.trim();
						
						if ( genes1.contains( name ) && genes2.contains( name ) )
							genesToTest.add( name );
						else
							System.out.println( "Gene '" + name + "' is not present in both datasets, omitting.");
					}
		
					System.out.println( "Added desired genes, number of genes now " + genesToTest.size() + ": " );
				}

				for ( String g : genesToTest )
					System.out.print( g + " ");
				System.out.println();

				/*
				if ( loadGenes != null && loadGenes.length() > 0 )
				{
					final File file = new File( loadGenes );
					if ( !file.exists() )
					{
						System.out.println( "File for loading genes '" + file.getAbsolutePath() + "' does not exist. stopping.");
						return null;
					}
		
					for ( final String gene : TextFileIO.loadGenes( file, new HashSet<>( stData1.getGeneNames() ), new HashSet<>( stData2.getGeneNames() ) ) )
						genesToTest.add( gene );
		
					System.out.println( "Loaded genes, number of genes now " + genesToTest.size() );
				}
		
				System.out.println( "Total number of genes: " + genesToTest.size() );
		
				if ( saveGenes != null && saveGenes.length() > 0 )
				{
					TextFileIO.saveGenes( new File( saveGenes ), genesToTest );
					System.out.println( "Saved genes to file " + new File( saveGenes ).getAbsolutePath() );
				}
				*/

				//
				// start alignment
				//

				//final double scale = 0.05; //global scaling
				//final double smoothnessFactor = 4.0;

				//final double maxEpsilon = 250;
				//final int minNumInliers = 30;
				//final int minNumInliersPerGene = 5;
		
				final SIFTParam p = new SIFTParam();
				final boolean saveResult = true;
				final boolean visualizeResult = !hidePairwiseRendering;

				if ( visualizeResult )
				{
					if ( renderingGene == null )
					{
						if ( genesToTest.contains( "Calm2" ) )
							renderingGene = "Calm2";
						else
							renderingGene = genesToTest.iterator().next();
					}

					AlignTools.defaultGene = renderingGene;
					AlignTools.defaultScale = scale;
				}

				System.out.println( "Gene used for rendering: " + renderingGene );

				System.out.println( "Aligning ... ");

				long time = System.currentTimeMillis();

				// hard case: -i /Users/spreibi/Documents/BIMSB/Publications/imglib2-st/slide-seq-test.n5 -d1 Puck_180602_15 -d2 Puck_180602_16 -n 30
				// even harder: -i /Users/spreibi/Documents/BIMSB/Publications/imglib2-st/slide-seq-test.n5 -d1 Puck_180602_20 -d2 Puck_180602_18 -n 100 --overwrite
				SiftMatch match = PairwiseSIFT.pairwiseSIFT(
						stData1, dataset1, stData2, dataset2,
						new RigidModel2D(), new RigidModel2D(),
						new ArrayList<>( genesToTest ),
						p, scale, smoothnessFactor, maxEpsilon,
						minNumInliers, minNumInliersGene,
						visualizeResult, Threads.numThreads() );

				if (saveResult && match.getNumInliers() >= minNumInliers) {
					container.savePairwiseMatch(match);
				}


				System.out.println( "Took " + (System.currentTimeMillis() - time)/1000 + " sec." );

			}
		}

		service.shutdown();
		return null;
	}

	public static void main(final String... args) {
		CommandLine.call(new PairwiseSectionAligner(), args);
	}

}
