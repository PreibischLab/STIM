package examples;

import java.util.Random;

import cmd.RenderImage;
import data.STData;
import data.STDataStatistics;
import filter.FilterFactory;
import filter.Filters;
import filter.GaussianFilterFactory;
import gui.bdv.AddedGene.Rendering;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import net.imglib2.IterableRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.numeric.real.DoubleType;

public class RunFilter
{

	public static void main( String[] args )
	{
		final Rendering rendering = Rendering.Gauss;// Rendering.Linear;
		final double renderFactor = 1.5;// 15;

		// a pseudo-random number generator
		final Random rnd = new Random( 124 );

		// create random STData object
		final STData data = CreateSTData.createRandomSTDATA( rnd );

		// render an image for the raw data
		final RandomAccessibleInterval<DoubleType> raw = RenderImage.display(
				data,
				new STDataStatistics( data ),
				new AffineTransform2D(),
				rendering,
				renderFactor,
				null,
				data.getGeneNames().get( 0 ),
				data.getRenderInterval( 10 ) );

		// show raw image
		new ImageJ();
		ImageJFunctions.show( raw );

		// filter with an increasing radius
		ImageStack stack = new ImageStack();

		for ( int radius = 1; radius <= 15; ++radius )
		{
			System.out.println( "Radius = " + radius );

			// create Gaussian filter
			final FilterFactory<DoubleType, DoubleType> filter =
					new GaussianFilterFactory< DoubleType, DoubleType >( new DoubleType( 0 ), radius );

			// filter first gene
			final IterableRealInterval<DoubleType> filteredInterval =
					Filters.filter( data.getExprData( data.getGeneNames().get( 0 ) ), filter );
	
			// render an image for the raw data
			final RandomAccessibleInterval<DoubleType> filterRendering = RenderImage.display(
					filteredInterval,
					new STDataStatistics( data ).getMedianDistance(),
					new AffineTransform2D(),
					rendering,
					renderFactor,
					data.getRenderInterval( 10 ) );

			stack.addSlice( ImageJFunctions.wrap( filterRendering, "r=" + radius ).getProcessor() );
		}

		// show stack
		ImagePlus imp = new ImagePlus( "increasing filtering", stack );
		imp.resetDisplayRange();
		imp.show();
	}
}
