package cmd;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.n5.N5FSReader;

import gui.RenderThread;
import gui.STDataAssembly;
import gui.STDataExplorer;
import io.N5IO;
import picocli.CommandLine;
import picocli.CommandLine.Option;

public class View implements Callable<Void> {

	@Option(names = {"-i", "--container"}, required = true, description = "N5 container paths, e.g. -i /home/ssq.n5 ...")
	private String containerPath = null;

	@Option(names = {"-d", "--datasets"}, required = false, description = "comma separated list of datasets, e.g. -d 'Puck_180528_20,Puck_180528_22' (default: open all datasets)")
	private List<String> datasets = null;

	@Option(names = {"-c", "--contrast"}, description = "comma separated contrast range for BigDataViewer display, e.g. -c '0,255' (default 0.1,5)" )
	private String contrastString = null;

	@Override
	public Void call() throws Exception {

		final File n5Path = new File( containerPath );

		if ( !n5Path.exists() )
		{
			System.out.println( "'" + n5Path.getAbsolutePath() + "' does not exist. Stopping." );
			return null;
		}

		if ( contrastString != null && contrastString.length() > 0 )
		{
			String[] contrastStrings = contrastString.trim().split( "," );

			if ( contrastStrings.length != 2 )
			{
				System.out.println( "contrast string could not parsed " + Arrays.asList( contrastStrings ) + ", ignoring - setting default range (" + RenderThread.min + "," + RenderThread.max + ")" );
			}
			else
			{
			RenderThread.min = Double.parseDouble( contrastStrings[ 0 ] );
			RenderThread.max = Double.parseDouble( contrastStrings[ 1 ] );

			System.out.println( "contrast range set to (" + RenderThread.min + "," + RenderThread.max + ")" );
			}
		}

		final ArrayList< STDataAssembly > data;

		if ( datasets == null || datasets.size() == 0 )
		{
			data = N5IO.openAllDatasets( new File( containerPath ) );
		}
		else
		{
			data = new ArrayList<>();

			final N5FSReader n5 = N5IO.openN5( new File( containerPath ) );
			for ( final String dataset : datasets )
				data.add( N5IO.openDataset( n5, dataset ) );
		}

		// ignore potentially saved intensity adjustments
		for ( final STDataAssembly d : data )
			d.intensityTransform().set( 1.0, 0.0 );

		new STDataExplorer( data );

		return null;
	}

	public static final void main(final String... args) {
		CommandLine.call(new View(), args);
	}
}
