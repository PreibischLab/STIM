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

import org.joml.Math;

import align.AlignTools;
import align.Pairwise;
import align.PairwiseSIFT;
import align.SIFTParam;
import align.SIFTParam.SIFTPreset;
import align.SiftMatch;
import analyze.ExtractGeneLists;
import data.STData;
import filter.FilterFactory;
import gui.STDataAssembly;
import gui.bdv.AddedGene.Rendering;
import ij.ImageJ;
import io.SpatialDataContainer;
import mpicbg.models.RigidModel2D;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import util.Threads;
import org.apache.logging.log4j.Logger;
import util.LoggerUtil;

@Command(name = "st-align-pairs", mixinStandardHelpOptions = true, version = "0.3.0", description = "Spatial Transcriptomics as IMages project - align pairs of slices")
public class PairwiseSectionAligner implements Callable<Void> {
	private static final Logger logger = LoggerUtil.getLogger();

	@Option(names = {"-c", "--container"}, required = true, description = "input N5 container path, e.g. -c /home/ssq.n5.")
	private String containerPath = null;

	@Option(names = {"-d", "--datasets"}, required = false, description = "ordered, comma separated list of one or more datasets, e.g. -d 'Puck_180528_20,Puck_180528_22' (default: all, in order as saved in N5 metadata)")
	private String datasets = null;

	@Option(names = {"-o", "--overwrite"}, required = false, description = "overwrite existing pairwise matches (default: false)")
	private boolean overwrite = false;

	//
	// rendering parameters
	//
	@Option(names = {"-s", "--scale"}, required = false, description = "initial scaling factor for rendering the coordinates into images, can be changed interactively (default: 0.05 for slideseq data)")
	private double scale = 0.05;

	@Option(names = {"-bmin", "--brightnessMin"}, required = false, description = "min initial brightness relative to the maximal value + overall min intensity (default: 0.0)")
	private double brightnessMin = 0.0;

	@Option(names = {"-bmax", "--brightnessMax"}, required = false, description = "max initial brightness relative to the maximal value (default: 0.5)")
	private double brightnessMax = 0.5;

	@Option(names = {"-rf", "--renderingFactor"}, required = false, description = "factor for the amount of filtering or radius used for rendering, corresponds to smoothness for Gauss, e.g -rf 2.0 (default: 1.0)")
	private double renderingFactor = 1.0;

	@Option(names = {"--rendering"}, required = false, description = "inital rendering type (Gauss, Mean, NearestNeighbor, Linear), e.g --rendering Gauss (default: Gauss)")
	private Rendering rendering = Rendering.Gauss;

	@Option(names = {"-sk", "--skip"}, required = false, description = "skips the first N genes when selecting by highest entropy, as they can be outliers (default: 10)")
	private int skipFirstNGenes = 10;

	@Option(names = {"--ffSingleSpot"}, required = false, description = "filter single spots using the median distance between all spots as threshold, e.g. --ffSingleSpot 1.5 (default: no filtering)")
	private Double ffSingleSpot = null;

	@Option(names = {"--ffMedian"}, required = false, description = "median-filter all spots using a given radius, e.g --ffMedian 5.0 (default: no filtering)")
	private Double ffMedian = null;

	@Option(names = {"--ffGauss"}, required = false, description = "Gauss-filter all spots using a given radius, e.g --ffGauss 2.0 (default: no filtering)")
	private Double ffGauss = null;

	@Option(names = {"--ffMean"}, required = false, description = "mean/avg-filter all spots using a given radius, e.g --ffMean 2.5 (default: no filtering)")
	private Double ffMean = null;

	//
	// alignment parameters
	//
	@Option(names = {"-r", "--range"}, required = false, description = "range in which pairs of datasets will be aligned, therefore the order in -d is important (default: 2)")
	private int range = 2;

	@Option(names = {"-g", "--genes"}, required = false, description = "comma separated list of one or more genes to be used (on top of numGenes, which can be set to 0 if only selected genes should be used), e.g. -g 'Calm2,Hpca,Ptgds'")
	private String genes = null;

	@Option(names = {"-n", "--numGenes"}, required = false, description = "initial number of genes for alignment that have the highest entropy (default: 10)")
	private int numGenes = 10;

	@Option(names = {"--entropyPath"}, required = false, description = "path where the entropy is stored as gene annotations (default: compute standard deviation from scratch)")
	private String entropyPath = null;

	@Option(names = {"-e", "--maxEpsilon"}, required = false, description = "maximally allowed alignment error (in global space, independent of scaling factor) for SIFT on a 2D rigid model (default: 10 times the average distance between sequenced locations)")
	private double maxEpsilon = -Double.MAX_VALUE;

	@Option(names = {"--minNumInliers"}, required = false, description = "minimal number of inliers across all tested genes that support the same 2D rigid model (default: 30 for slideseq)")
	private int minNumInliers = 30;

	@Option(names = {"--minNumInliersGene"}, required = false, description = "minimal number of inliers for each gene that support the same 2D rigid model (default: 5 for slideseq)")
	private int minNumInliersGene = 5;

	@Option(names = {"--hidePairwiseRendering"}, required = false, description = "do not show pairwise renderings that apply the 2D rigid models (default: false - showing them)")
	private boolean hidePairwiseRendering = false;

	@Option(names = {"--numThreads"}, required = false, description = "number of threads for parallel processing")
	private int numThreads = 128;

	//-c /Users/spreibi/Documents/BIMSB/Publications/imglib2-st/slide-seq-test.n5 -d 'Puck_180602_20,Puck_180602_18,Puck_180602_17,Puck_180602_16,Puck_180602_15,Puck_180531_23,Puck_180531_22,Puck_180531_19,Puck_180531_18,Puck_180531_17,Puck_180531_13,Puck_180528_22,Puck_180528_20' -n 100 --overwrite

	@Override
	public Void call() throws Exception {
		if (!(new File(containerPath)).exists()) {
			logger.error("Container '" + containerPath + "' does not exist. Stopping.");
			return null;
		}

		if (!SpatialDataContainer.isCompatibleContainer(containerPath)) {
			logger.error("Pairwise alignment does not work for single dataset '" + containerPath + "'. Stopping.");
			return null;
		}

		final int threadsUse = Math.min(Threads.numThreads(), numThreads);

		final ExecutorService service = Executors.newFixedThreadPool(8);
		SpatialDataContainer container = SpatialDataContainer.openExisting(containerPath, service);

		final List<String> datasetNames;
		if (datasets != null && datasets.trim().length() != 0) {
			datasetNames = Arrays.stream(datasets.split(","))
					.map(String::trim)
					.collect(Collectors.toList());

			if (datasetNames.size() == 1) {
				logger.error("Only one single dataset found (" + datasetNames.get(0) + "). Stopping.");
				return null;
			}
		}
		else {
			// TODO: should this really continue? the order of the datasets matters, but getDatasets() returns random order
			logger.warn("No input datasets specified. Trying to open all datasets in '" + containerPath + "' ...");
			datasetNames = container.getDatasets();
		}

		final List<STDataAssembly> dataToAlign = new ArrayList<>();
		for (final String dataset : datasetNames) {
			logger.warn("Opening dataset '" + dataset + "' in '" + containerPath + "' ...");
			dataToAlign.add(container.openDataset(dataset).readData());
		}

		if (maxEpsilon <= 0.0) {
			maxEpsilon = 10 * dataToAlign.stream()
					.mapToDouble((data) -> data.statistics().getMeanDistance())
					.summaryStatistics().getAverage();
			logger.warn("Parameter maxEpsilon is unset or negative; using 10 * average distance between sequenced locations = " + maxEpsilon);
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
						logger.info("Overwriting previous results for: " + pairwiseMatchName);
						container.deleteMatch(pairwiseMatchName);
					}
					else {
						logger.error("Previous results exist '" + pairwiseMatchName + "', stopping. [Rerun with --overwrite for automatic deletion of previouse results]");
						return null;
					}
				}
				else {
					logger.debug("To align: " + pairwiseMatchName);
				}
			}
		}

		final boolean saveResult = true;
		final boolean visualizeResult = !hidePairwiseRendering;
		if (visualizeResult)
			new ImageJ();

		HashSet<String> all_genes = new HashSet<String>(dataToAlign.get(0).data().getGeneNames());

		for ( int i = 1; i < dataToAlign.size(); ++i ) {
			all_genes.retainAll(new HashSet<String>(dataToAlign.get(i).data().getGeneNames()));
		}

		if (numGenes > 0 && entropyPath == null) {
			logger.info( "Computing standard deviation of genes for all sections (will take a while)" );
			for ( int i = 0; i < dataToAlign.size(); ++i ) {
				final String dataset_name = datasetNames.get( i );
				logger.debug( "Now processing " + dataset_name );

				final ArrayImg<DoubleType, DoubleArray> entropy_values_rai;
				final STDataAssembly stData = dataToAlign.get( i );
				if (stData.data().getGeneAnnotations().containsKey("stdev")) {
					logger.debug("Gene annotation 'stdev' was found for " + dataset_name + ". Omitting.");
					continue;
				}
				final double[] entropy_values = ExtractGeneLists.computeEntropy("stdev", stData.data(), numThreads);
	
				entropy_values_rai = ArrayImgs.doubles(entropy_values, (long) stData.data().numGenes());
				stData.data().getGeneAnnotations().put("stdev", entropy_values_rai);
				container.openDataset(dataset_name).updateStoredGeneAnnotations(stData.data().getGeneAnnotations());
			}
			entropyPath = "stdev";
		} else if (entropyPath != null) {
			logger.debug( "Will take genes from '" + entropyPath + "' property in gene annotation" );
		}

		for ( int i = 0; i < dataToAlign.size() - 1; ++i ) {
			for ( int j = i + 1; j < dataToAlign.size(); ++j ) {
				if ( Math.abs( j - i ) > range )
					continue;

				final STData stData1 = dataToAlign.get( i ).data();
				final STData stData2 = dataToAlign.get( j ).data();
				final AffineTransform2D t1 = dataToAlign.get( i ).transform();
				final AffineTransform2D t2 = dataToAlign.get( j ).transform();
				final String dataset1 = datasetNames.get( i );
				final String dataset2 = datasetNames.get( j );

				// assemble gene set for alignment
				final HashSet< String > genesToTest = new HashSet<>( Pairwise.genesToTest( stData1, stData2, entropyPath, numGenes ) );
		
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
							logger.warn( "Gene '" + name + "' is not present in both datasets, omitting.");
					}
				}

				logger.debug( "Gene set for pairwise alignment: " + genesToTest.toString() );
				//
				// start alignment
				//
				logger.info( "Aligning " + dataset1 + " <> " + dataset2 + " on " + genesToTest.size() + "genes (" + threadsUse + " threads)");
				final SIFTParam p = new SIFTParam();
				final List< FilterFactory< DoubleType, DoubleType > > filterFactories = null;
				p.setDatasetParameters(maxEpsilon, scale, 1024, filterFactories, Rendering.Gauss, renderingFactor, brightnessMin, brightnessMax); 
				p.setIntrinsicParameters( SIFTPreset.VERYTHOROUGH );
				p.minInliersGene = minNumInliersGene;
				p.minInliersTotal = minNumInliers;

				if ( visualizeResult )
				{
					String renderingGene = genesToTest.iterator().next();
					AlignTools.defaultGene = renderingGene;
					AlignTools.defaultScale = scale;
					logger.info( "Gene used for rendering: " + renderingGene );
				}
				
				logger.debug("Processing SIFT with parameters: " + p.toString());
				long time = System.currentTimeMillis();
				SiftMatch match = PairwiseSIFT.pairwiseSIFT(
						stData1, t1, dataset1, stData2, t2, dataset2,
						new RigidModel2D(), new RigidModel2D(),
						new ArrayList<>( genesToTest ),
						p, visualizeResult, threadsUse );

				if (saveResult && match.getNumInliers() >= minNumInliers) {
					container.savePairwiseMatch(match);
				}
				logger.info("Aligned " + dataset1 + " <> " + dataset2 + " in " + (System.currentTimeMillis() - time)/1000 + " s");

			}
		}

		service.shutdown();
		return null;
	}

	public static void main(final String... args) {
		CommandLine.call(new PairwiseSectionAligner(), args);
	}

}
