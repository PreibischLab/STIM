package cmd;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.n5.N5FSReader;

import align.GlobalOptSIFT;
import data.STData;
import io.N5IO;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import util.Threads;

public class GlobalOpt implements Callable<Void> {

	@Option(names = {"-i", "--input"}, required = true, description = "input N5 container, e.g. -i /home/ssq.n5")
	private String input = null;

	@Option(names = {"-d", "--datasets"}, required = false, description = "ordered, comma separated list of one or more datasets, e.g. -d 'Puck_180528_20,Puck_180528_22' (default: all, in order as saved in N5 metadata)")
	private String datasets = null;

	// alignment options

	@Override
	public Void call() throws Exception {

		final File n5File = new File( input );

		if ( !n5File.exists() )
		{
			System.out.println( "N5 '" + n5File.getAbsolutePath() + "'not found. stopping.");
			return null;
		}

		final N5FSReader n5 = N5IO.openN5( n5File );
		final List< String > inputDatasets;

		if ( datasets == null || datasets.trim().length() == 0 )
			inputDatasets = N5IO.listAllDatasets( n5 );
		else
			inputDatasets = Arrays.asList( datasets.split( "," ) );

		if ( inputDatasets.size() == 0 )
		{
			System.out.println( "no input datasets available. stopping.");
			return null;
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

		final boolean useQuality = true;
		final double lambdaGlobal = 0.1; // rigid only
		final double maxAllowedError = 300;
		final int maxIterations = 500;
		final int maxPlateauwidth = 500;
		final double relativeThreshold = 3.0;
		final double absoluteThreshold = 160;

		final boolean doICP = true;
		final double lambdaICP = 0.1;
		final double icpErrorFraction = 1.0 / 10.0;
		final double maxAllowedErrorICP = 140;
		final int numIterationsICP = 3000;
		final int maxPlateauwhidthICP = 500;

		GlobalOptSIFT.globalOpt(
				n5File,
				inputDatasets,
				useQuality,
				lambdaGlobal,
				maxAllowedError,
				maxIterations,
				maxPlateauwidth,
				relativeThreshold,
				absoluteThreshold,
				doICP,
				lambdaICP,
				icpErrorFraction,
				maxAllowedErrorICP,
				numIterationsICP,
				maxPlateauwhidthICP,
				Threads.numThreads() );

		return null;
	}

	public static void main(final String... args) {
		CommandLine.call(new GlobalOpt(), args);
	}

}
