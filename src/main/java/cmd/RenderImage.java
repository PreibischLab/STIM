package cmd;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.n5.N5FSReader;

import align.AlignTools;
import data.STData;
import gui.STDataAssembly;
import ij.ImageJ;
import ij.ImagePlus;
import io.N5IO;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import picocli.CommandLine;
import picocli.CommandLine.Option;

public class RenderImage implements Callable<Void> {

	@Option(names = {"-i", "--input"}, required = true, description = "input N5 container, e.g. -i /home/ssq.n5")
	private String input = null;

	@Option(names = {"-d", "--datasets"}, required = false, description = "comma separated list of one or more datasets, e.g. -d 'Puck_180528_20,Puck_180528_22' (default: all)")
	private String datasets = null;

	@Option(names = {"-g", "--genes"}, required = true, description = "comma separated list of one or more genes, e.g. -g 'Calm2,Pcp4,Ptgds'")
	private String genes = null;

	@Option(names = {"-s", "--scale"}, required = false, description = "scaling of the image, e.g. -s 0.5 (default: 0.05)")
	private double scale = 0.05;

	@Override
	public Void call() throws Exception {

		final boolean displayAsImageInstance = true;
		//final boolean ignoreIntensityAdjustment = true;
		final boolean useTransform = true;

		if ( displayAsImageInstance )
			new ImageJ();

		final N5FSReader n5 = N5IO.openN5( new File( input ) );

		final List< Pair< STData, AffineTransform2D > > data = new ArrayList<>();

		List< String > inputDatasets, geneList;

		if ( datasets == null || datasets.length() == 0 )
			inputDatasets = Arrays.asList( n5.list( "/" ) );
		else
			inputDatasets = Arrays.asList( datasets.split( "," ) );

		if ( inputDatasets.size() == 0 )
		{
			System.out.println( "no input datasets available. stopping.");
			return null;
		}

		if ( genes == null || genes.length() == 0 )
			geneList = Arrays.asList( n5.list( "/" ) );
		else
			geneList = Arrays.asList( genes.split( "," ) );

		if ( geneList.size() == 0 )
		{
			System.out.println( "no genes available. stopping.");
			return null;
		}

		for ( final String dataset : inputDatasets )
		{
			final STDataAssembly stAssembly =
					N5IO.openDataset(n5, dataset, true );

			if ( stAssembly != null )
				data.add( new ValuePair<STData, AffineTransform2D>(
						stAssembly.data(),
						useTransform ? stAssembly.transform() : new AffineTransform2D() ) );
		}

		if ( data.size() == 0 )
		{
			System.out.println( "No datasets that contain sequencing data. stopping." );
			return null;
		}

		for ( final String gene : geneList )
		{
			System.out.println( "Rendering gene " + gene );

			ImagePlus imp = AlignTools.visualizeList( data, scale, gene, displayAsImageInstance );
			imp.setTitle( gene );
		}

		return null;
	}

	public static final void main(final String... args) {
		CommandLine.call(new RenderImage(), args);
	}
}
