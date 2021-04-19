package cmd;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.n5.N5FSWriter;

import align.Pairwise;
import align.PairwiseSIFT;
import align.PairwiseSIFT.SIFTParam;
import data.STData;
import ij.ImageJ;
import io.N5IO;
import io.TextFileIO;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import util.Threads;

public class PairwiseSectionAligner implements Callable<Void> {

	@Option(names = {"-i", "--input"}, required = true, description = "input N5 container, e.g. -i /home/ssq.n5")
	private String input = null;

	@Option(names = {"-d1", "--dataset1"}, required = true, description = "first N5 dataset, e.g. -d1 Puck_180528_20")
	private String dataset1 = null;

	@Option(names = {"-d2", "--dataset2"}, required = true, description = "second N5 dataset, e.g. -d1 Puck_180528_20")
	private String dataset2 = null;

	@Option(names = {"-g", "--genes"}, required = false, description = "comma separated list of one or more genes to be used (on top of numGenes, which can be set to 0 if only selected genes should be used), e.g. -g 'Calm2,Hpca,Ptgds'")
	private String genes = null;

	@Option(names = {"-n", "--numGenes"}, required = false, description = "use N number of genes that have the highest entropy (default: 100)")
	private int numGenes = 100;

	@Option(names = {"-l", "--loadGenes"}, required = false, description = "load a plain text file with gene names")
	private String loadGenes = null;

	@Option(names = {"-s", "--saveGenes"}, required = false, description = "save a plain text file with gene names that were used (can be later imported with -l)")
	private String saveGenes = null;

	@Option(names = {"-o", "--overwrite"}, required = false, description = "overwrite existing pairwise matches (default: false)")
	private boolean overwrite = false;

	@Override
	public Void call() throws Exception {

		final File n5File = new File( input );

		if ( !n5File.exists() )
		{
			System.out.println( "N5 '" + n5File.getAbsolutePath() + "'not found. stopping.");
			return null;
		}

		final N5FSWriter n5 = N5IO.openN5write( n5File );

		if ( !n5.exists( n5.groupPath( dataset1 ) ) )
		{
			System.out.println( "dataset1 '" + dataset1 + "' not found. stopping.");
			return null;
		}

		if ( !n5.exists( n5.groupPath( dataset2 ) ) )
		{
			System.out.println( "dataset1 '" + dataset2 + "' not found. stopping.");
			return null;
		}

		final STData stData1 = N5IO.readN5( n5, dataset1 );
		final STData stData2 = N5IO.readN5( n5, dataset2 );

		// clear the alignment metadata
		final String matchesGroupName = n5.groupPath( "matches" );

		if ( !n5.exists(matchesGroupName) )
		{
			System.out.println( "Creating new dataset '" + matchesGroupName + "'" );
			n5.createGroup( matchesGroupName );
		}

		final String pairwiseGroupName = n5.groupPath( "matches", dataset1 + "-" + dataset2 );
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
					System.out.println( "Gene " + name + " is not present in both datasets, omitting.");
			}

			System.out.println( "Added desired genes, number of genes now " + genesToTest.size() );
		}

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

		//
		// start alignment
		//

		final double scale = 0.1; //global scaling
		final double maxEpsilon = 200;
		final int minNumInliers = 25;
		final int minNumInliersPerGene = 10;

		final SIFTParam p = new SIFTParam();
		final boolean saveResult = true;
		final boolean visualizeResult = true;

		new ImageJ();

		PairwiseSIFT.pairwiseSIFT(stData1, dataset1, stData2, dataset2, n5File, new ArrayList<>( genesToTest ), p, scale, maxEpsilon,
				minNumInliers, minNumInliersPerGene, saveResult, visualizeResult, Threads.numThreads() );

		return null;
	}

	public static void main(final String... args) {
		CommandLine.call(new PairwiseSectionAligner(), args);
	}

}
