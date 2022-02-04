package cmd;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.joml.Math;

import align.AlignTools;
import align.Pairwise;
import align.PairwiseSIFT;
import align.PairwiseSIFT.SIFTParam;
import data.STData;
import ij.ImageJ;
import io.N5IO;
import mpicbg.models.RigidModel2D;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import util.Threads;

public class PairwiseSectionAligner implements Callable<Void> {

	@Option(names = {"-i", "--input"}, required = true, description = "input N5 container, e.g. -i /home/ssq.n5")
	private String input = null;

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

	@Option(names = {"-e", "--maxEpsilon"}, required = false, description = "maximally allowed alignment error (in global space, independent of scaling factor) for SIFT on a 2D rigid model (default: 250.0 for slideseq)")
	private double maxEpsilon = 250.0;

	@Option(names = {"--minNumInliers"}, required = false, description = "minimal number of inliers across all tested genes that support the same 2D rigid model (default: 30 for slideseq)")
	private int minNumInliers = 30;

	@Option(names = {"--minNumInliersGene"}, required = false, description = "minimal number of inliers for each gene that support the same 2D rigid model (default: 5 for slideseq)")
	private int minNumInliersGene = 5;

	@Option(names = {"--hidePairwiseRendering"}, required = false, description = "do not show pairwise renderings that apply the 2D rigid models (default: false - showing them)")
	private boolean hidePairwiseRendering = false;

	@Option(names = {"--spark"}, required = false, description = "use spark")
	private boolean sparkProcessing = true;

	//-i /Users/spreibi/Documents/BIMSB/Publications/imglib2-st/slide-seq-test.n5 -d 'Puck_180602_20,Puck_180602_18,Puck_180602_17,Puck_180602_16,Puck_180602_15,Puck_180531_23,Puck_180531_22,Puck_180531_19,Puck_180531_18,Puck_180531_17,Puck_180531_13,Puck_180528_22,Puck_180528_20' -n 100 --overwrite

	@Override
	public Void call() throws Exception {

		final File n5File = new File( input );

		if ( !n5File.exists() )
		{
			System.out.println( "N5 '" + n5File.getAbsolutePath() + "'not found. stopping.");
			return null;
		}

		final N5FSWriter n5 = N5IO.openN5write( n5File );

		final List< String > inputDatasets;

		if ( datasets == null || datasets.trim().length() == 0 )
		{
			System.out.println( "no input datasets specified. Trying to load all of them.");
			inputDatasets = N5IO.listAllDatasets( n5 );
		}
		else
		{
			inputDatasets = Arrays.asList( datasets.split( "," ) );
		}

		if ( inputDatasets.size() == 0 )
		{
			System.out.println( "no input datasets available. stopping.");
			return null;
		}
		else
		{
			System.out.println( "Following datasets were found: ");
			for ( String s : inputDatasets )
				System.out.println( s );
		}

		final List< STData > stdata = new ArrayList<>();

		for ( final String dataset : inputDatasets )
		{
			if ( !n5.exists( n5.groupPath( dataset ) ) )
			{
				System.out.println( "dataset '" + dataset + "' not found. stopping.");
				return null;
			}
			else
			{
				stdata.add( N5IO.readN5( n5, dataset ) );
			}
		}
		JavaSparkContext sc = null;

		if(sparkProcessing){
			final SparkConf conf = new SparkConf().setAppName("PairwiseSectionAligner").setMaster("local");
			sc = new JavaSparkContext(conf);
			sc.setLogLevel("ERROR");
		}

		// iterate once just to be sure we will not crash half way through because something exists
		for ( int i = 0; i < stdata.size() - 1; ++i )
		{
			for ( int j = i + 1; j < stdata.size(); ++j )
			{
				if ( Math.abs( j - i ) > range )
					continue;

				// clear the alignment metadata
				final String matchesGroupName = n5.groupPath( "matches" );

				if ( !n5.exists(matchesGroupName) )
				{
					System.out.println( "Creating new dataset '" + matchesGroupName + "'" );
					n5.createGroup( matchesGroupName );
				}

				final String pairwiseGroupName = n5.groupPath( "matches", inputDatasets.get( i ) + "-" + inputDatasets.get( j ) );
				if ( new File( n5File, pairwiseGroupName ).exists() )// n5.exists( pairwiseGroupName ) )
				{
					if ( overwrite )
					{
						System.out.println( "Overwriting previous results for: " + pairwiseGroupName );
						n5.remove( pairwiseGroupName );
					}
					else
					{
						System.out.println( "Previous results exist '" + pairwiseGroupName + "', stopping. [Rerun with --overwrite for automatic deletion of previouse results]");
						return null;
					}
				}
				else
				{
					System.out.println( "To align: " + pairwiseGroupName );
				}
			}
		}

		if ( !hidePairwiseRendering )
			new ImageJ();

		for ( int i = 0; i < stdata.size() - 1; ++i )
		{
			for ( int j = i + 1; j < stdata.size(); ++j )
			{
				if ( Math.abs( j - i ) > range )
					continue;

				final STData stData1 = stdata.get( i );
				final STData stData2 = stdata.get( j );
				final String dataset1 = inputDatasets.get( i );
				final String dataset2 = inputDatasets.get( j );

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
				}

				System.out.println( "Gene used for rendering: " + renderingGene );

				System.out.println( "Aligning ... ");

				long time = System.currentTimeMillis();

				// hard case: -i /Users/spreibi/Documents/BIMSB/Publications/imglib2-st/slide-seq-test.n5 -d1 Puck_180602_15 -d2 Puck_180602_16 -n 30
				// even harder: -i /Users/spreibi/Documents/BIMSB/Publications/imglib2-st/slide-seq-test.n5 -d1 Puck_180602_20 -d2 Puck_180602_18 -n 100 --overwrite
				if (sparkProcessing){
					PairwiseSIFT.sparkPairwiseSIFT(
							input, dataset1, dataset2,
							new RigidModel2D(), new RigidModel2D(),
							new ArrayList<>(genesToTest),
							p, scale, smoothnessFactor, maxEpsilon,
							minNumInliers, minNumInliersGene,
							saveResult, visualizeResult, sc);
				}else {
					PairwiseSIFT.pairwiseSIFT(
							stData1, dataset1, stData2, dataset2,
							new RigidModel2D(), new RigidModel2D(),
							n5File, new ArrayList<>(genesToTest),
							p, scale, smoothnessFactor, maxEpsilon,
							minNumInliers, minNumInliersGene,
							saveResult, visualizeResult, Threads.numThreads());
				}
				System.out.println( "Took " + (System.currentTimeMillis() - time)/1000 + " sec." );

			}
		}

		return null;
	}

	public static void main(final String... args) {
		CommandLine.call(new PairwiseSectionAligner(), args);
	}

}
