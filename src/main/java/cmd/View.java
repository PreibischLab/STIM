package cmd;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.n5.N5FSReader;

import gui.STDataAssembly;
import gui.STDataExplorer;
import io.N5IO;
import picocli.CommandLine;
import picocli.CommandLine.Option;

public class View implements Callable<Void> {
	//TODO: fix viewer brightness
	//TODO: build function, makes fatjar and sh scripts

	@Option(names = {"-i", "--container"}, required = true, description = "N5 container paths, e.g. -i /home/ssq.n5 ...")
	private String containerPath = null;

	@Option(names = {"-d", "--datasets"}, required = false, description = "comma separated list of datasets, e.g. -d '/Puck_180528_20,Puck_180528_22' (default: open all datasets)")
	private List<String> datasets = null;

	@Override
	public Void call() throws Exception {

		final File n5Path = new File( containerPath );

		if ( !n5Path.exists() )
		{
			System.out.println( "'" + n5Path.getAbsolutePath() + "' does not exist. Stopping." );
			return null;
		}

		final ArrayList< STDataAssembly > data;

		if ( datasets == null || datasets.size() == 0 )
		{
			data = N5IO.openAllDatasets( new File( containerPath ), true );
		}
		else
		{
			data = new ArrayList<>();

			final N5FSReader n5 = N5IO.openN5( new File( containerPath ) );
			for ( final String dataset : datasets )
				data.add( N5IO.openDataset( n5, dataset, true ) );
		}

		new STDataExplorer( data );

		return null;
	}

	public static final void main(final String... args) {
		CommandLine.call(new View(), args);
	}
}
