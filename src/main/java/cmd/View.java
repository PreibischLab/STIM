package cmd;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import io.SpatialDataContainer;
import io.SpatialDataIO;

import gui.RenderThread;
import gui.STDataAssembly;
import gui.STDataExplorer;
import picocli.CommandLine;
import picocli.CommandLine.Option;

public class View implements Callable<Void> {

	@Option(names = {"-i", "--container"}, required = true, description = "input file or N5 container path, e.g. -i /home/ssq.n5.")
	private String containerPath = null;

	@Option(names = {"-d", "--datasets"}, required = false, description = "if --container is given: comma separated list of datasets, e.g. -d 'Puck_180528_20,Puck_180528_22' (default: open all datasets)")
	private List<String> datasets = null;

	@Option(names = {"-c", "--contrast"}, description = "comma separated contrast range for BigDataViewer display, e.g. -c '0,255' (default 0.1,5)" )
	private String contrastString = null;

	@Override
	public Void call() throws Exception {
		if (!(new File(containerPath)).exists()) {
			System.out.println("Container / dataset '" + containerPath + "' does not exist. Stopping.");
			return null;
		}

		final List<STDataAssembly> dataToVisualize = new ArrayList<>();
		if (SpatialDataContainer.isCompatibleContainer(containerPath)) {
			SpatialDataContainer container = SpatialDataContainer.openExisting(containerPath);

			if (datasets != null) {
				for (String dataset : datasets) {
					System.out.println("Opening dataset '" + dataset + "' in '" + containerPath + "' ...");
					dataToVisualize.add(container.openDataset(dataset).readData());
				}
			}
			else {
				System.out.println("Opening all datasets in '" + containerPath + "' ...");
				for (SpatialDataIO sdio : container.openAllDatasets())
					dataToVisualize.add(sdio.readData());
			}
		}
		else {
			System.out.println("Opening dataset '" + containerPath + "' ...");
			dataToVisualize.add(SpatialDataIO.inferFromName(containerPath).readData());
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

		// ignore potentially saved intensity adjustments
		for ( final STDataAssembly d : dataToVisualize )
			d.intensityTransform().set( 1.0, 0.0 );

		new STDataExplorer( dataToVisualize );

		return null;
	}

	public static final void main(final String... args) {
		CommandLine.call(new View(), args);
	}
}
