package cmd;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;

import data.NormalizingSTData;
import data.STDataN5;
import io.N5IO;
import picocli.CommandLine;
import picocli.CommandLine.Option;

public class Normalize implements Callable<Void> {

		@Option(names = {"-o", "--output"}, required = false, description = "output N5 container (default: same as input)")
		private String output = null;

		@Option(names = {"-i", "--input"}, required = true, description = "input N5 container, e.g. -i /home/ssq.n5")
		private String input = null;

		@Option(names = {"-d", "--datasets"}, required = false, description = "comma-separated list of datasets to be normalized, e.g. -d 'Puck_180528_20,Puck_180528_22' (default: all datasets)")
		private String datasets = null;

		@Option(names = {"-n", "--normdatasets"}, required = false, description = "comma-separated list of with the corresponding names of normalized datasets to be normalized, e.g. -n 'NormPuck_180528_20,NormPuck_180528_22' (default: old names extended with -norm if same N5)")
		private String normdatasets = null;

		@Override
		public Void call() throws Exception {

			final boolean sameN5;

			if ( output == null || output.length() == 0 )
			{
				output = input;
				sameN5 = true;
			}
			else
			{
				sameN5 = true;
			}

			final N5FSReader n5in = N5IO.openN5( new File( input ) );
			final File n5Path = new File( output );
			final N5FSWriter n5out = n5Path.exists() ? N5IO.openN5write( n5Path ) : N5IO.createN5( n5Path );

			List< String > inputDatasets, outputDatasets;

			if ( datasets == null || datasets.length() == 0 )
				inputDatasets = Arrays.asList( n5in.list( "/" ) );
			else
				inputDatasets = Arrays.asList( datasets.split( "," ) );

			if ( inputDatasets.size() == 0 )
			{
				System.out.println( "no input datasets available. stopping.");
				return null;
			}

			if ( normdatasets == null || normdatasets.length() == 0 )
				outputDatasets = inputDatasets.stream().map( in -> sameN5 ? in + "-norm" : in ).collect( Collectors.toList() );
			else
				outputDatasets = Arrays.asList( normdatasets.split( "," ) );

			if ( outputDatasets.size() == 0 )
			{
				System.out.println( "no output datasets available. stopping.");
				return null;
			}

			if ( inputDatasets.size() != outputDatasets.size() )
			{
				System.out.println( "different number of input datasets and output datasets specified. stopping.");
				return null;
			}

			for ( int i = 0; i < inputDatasets.size(); ++i )
			{
				STDataN5 data = N5IO.readN5( n5in, inputDatasets.get( i ) );
				if ( data != null )
					N5IO.writeN5(n5out, outputDatasets.get( i ), new NormalizingSTData( data ) );
			}

			System.out.println( "done." );

			return null;
		}

		public static final void main(final String... args) {
			CommandLine.call(new Normalize(), args);
		}
}
