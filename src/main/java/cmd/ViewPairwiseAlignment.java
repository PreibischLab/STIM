package cmd;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5FSReader;

import align.AlignTools;
import data.STData;
import ij.ImageJ;
import io.N5IO;
import mpicbg.models.AffineModel2D;
import mpicbg.models.InterpolatedAffineModel2D;
import mpicbg.models.PointMatch;
import mpicbg.models.RigidModel2D;
import net.imglib2.realtransform.AffineTransform2D;
import picocli.CommandLine;
import picocli.CommandLine.Option;

public class ViewPairwiseAlignment implements Callable<Void> {

	@Option(names = {"-i", "--input"}, required = true, description = "input N5 container, e.g. -i /home/ssq.n5")
	private String input = null;

	@Option(names = {"-d", "--datasets"}, required = true, description = "ordered, comma separated list of one or more datasets, e.g. -d 'Puck_180528_20,Puck_180528_22' (default: all)")
	private String datasets = null;

	@Option(names = {"-g", "--gene"}, required = true, description = "gene to use for rendering, e.g. -g Calm2")
	private String gene = null;

	@Option(names = {"-s", "--scale"}, required = false, description = "scaling factor rendering the coordinates into images, which highly sample-dependent (default: 0.05 for slideseq data)")
	private double scale = 0.05;

	@Option(names = {"-sf", "--smoothnessFactor"}, required = false, description = "factor for the sigma of the gaussian used for rendering, corresponds to smoothness, e.g -sf 2.0 (default: 4.0)")
	private double smoothnessFactor = 4.0;

	@Option(names = {"-m", "--model"}, required = false, description = "for rendering we use a 2D interpolated model (affine/rigid). The number defines the degree of rigidity, fully affine is 0.0, fully rigid is 1.0 (default: 1.0 - rigid)")
	private double model = 1.0;

	@Override
	public Void call() throws Exception {

		final File n5File = new File( input );

		if ( !n5File.exists() )
		{
			System.out.println( "N5 '" + n5File.getAbsolutePath() + "'not found. stopping.");
			return null;
		}

		final N5FSReader n5 = N5IO.openN5( n5File );

		if ( datasets == null || datasets.trim().length() == 0 )
		{
			System.out.println( "no input datasets specified. stopping.");
			return null;
		}

		final List< String > inputDatasets = Arrays.asList( datasets.split( "," ) );

		if ( inputDatasets.size() == 0 )
		{
			System.out.println( "no input datasets defined. stopping.");
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

		new ImageJ();

		for ( int i = 0; i < stdata.size() - 1; ++i )
		{
			for ( int j = i + 1; j < stdata.size(); ++j )
			{
				// test if matches exist
				final String pairwiseGroupName = n5.groupPath( "matches", inputDatasets.get( i ) + "-" + inputDatasets.get( j ) );
				if ( new File( n5File, pairwiseGroupName ).exists() )// n5.exists( pairwiseGroupName ) )
				{
					final STData stData1 = stdata.get( i );
					final STData stData2 = stdata.get( j );
					final String dataset1 = inputDatasets.get( i );
					final String dataset2 = inputDatasets.get( j );

					final InterpolatedAffineModel2D< AffineModel2D, RigidModel2D > m =
							new InterpolatedAffineModel2D<AffineModel2D, RigidModel2D>( new AffineModel2D(), new RigidModel2D(), model );

					final DatasetAttributes datasetAttributes = n5.getDatasetAttributes( pairwiseGroupName );
					final ArrayList< PointMatch > inliers = n5.readSerializedBlock( pairwiseGroupName, datasetAttributes, new long[]{0} );

					// reset world coordinates
					for ( final PointMatch pm : inliers )
					{
						for ( int d = 0; d < pm.getP1().getL().length; ++d )
						{
							pm.getP1().getW()[ d ] = pm.getP1().getL()[ d ];
							pm.getP2().getW()[ d ] = pm.getP2().getL()[ d ];
						}
					}

					m.fit( inliers );

					AlignTools.visualizePair(
							stData1, stData2,
							new AffineTransform2D(),
							AlignTools.modelToAffineTransform2D( m ).inverse(),
							smoothnessFactor ).setTitle( dataset1 + "-" + dataset2 + "-inliers-" + inliers.size() );
				}
			}
		}

		return null;
	}

	public static void main(final String... args) {
		CommandLine.call(new ViewPairwiseAlignment(), args);
	}

}
