package render;

import java.io.File;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import filter.Filters;
import filter.GaussianFilterFactory;
import filter.MedianFilterFactory;
import filter.realrandomaccess.MedianRealRandomAccessible;
import net.imglib2.RealPointSampleList;
import net.imglib2.RealRandomAccessible;
import net.imglib2.type.numeric.real.FloatType;
import test.ImgLib2;
import test.Reader;
import test.Reader.STData;

public class Vistools
{
	public static void main( String[] args )
	{
		final STData stdata = Reader.read(
				new File( "/Users/spreibi/Downloads/patterns_examples/locations.txt" ),
				new File( "/Users/spreibi/Downloads/patterns_examples/dge_normalized_small.txt" ),
				1.0 );

		final double displayRadius = stdata.distanceStats.median / 2.0;
		final double medianRadius = stdata.distanceStats.median * 2.0;

		// gauss crisp
		double gaussRenderSigma = stdata.distanceStats.median / 4; 
		double gaussRenderRadius = displayRadius;

		final FloatType outofbounds = new FloatType( 0 );

		final RealPointSampleList< FloatType > data = ImgLib2.wrapFloat( stdata.coordinates, stdata.genes.get( "Pcp4" ) );
		final RealPointSampleList< FloatType > medianFiltered = Filters.filter( data, new MedianFilterFactory<>( outofbounds, medianRadius ) );//outofbounds, medianRadius );

		final RealRandomAccessible< FloatType > median = new MedianRealRandomAccessible<>( data, outofbounds, medianRadius );

		//BdvFunctions.show( Render.render( data, outofbounds, displayRadius ), stdata.renderInterval, "Pcp4_raw", BdvOptions.options().is2D() ).setDisplayRange( 0, 6 );
		//BdvFunctions.show( Render.renderAvg( data, outofbounds, displayRadius * 3 ), stdata.renderInterval, "Pcp4_rawavg", BdvOptions.options().is2D() ).setDisplayRange( 0, 6 );

		BdvFunctions.show( Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) ), stdata.renderInterval, "Pcp4_gauss1", BdvOptions.options().is2D() ).setDisplayRange( 0, 4 );
		BdvFunctions.show( Render.render( medianFiltered, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) ), stdata.renderInterval, "Pcp4_median_gauss1", BdvOptions.options().is2D() ).setDisplayRange( 0, 4 );

		// gauss smooth
		gaussRenderSigma = displayRadius;
		gaussRenderRadius = displayRadius * 4;

		BdvFunctions.show( Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) ), stdata.renderInterval, "Pcp4_gauss2", BdvOptions.options().is2D() ).setDisplayRange( 0, 9 );
		BdvFunctions.show( Render.render( medianFiltered, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) ), stdata.renderInterval, "Pcp4_median_gauss2", BdvOptions.options().is2D() ).setDisplayRange( 0, 9 );

		//BdvFunctions.show( Render.render( medianFiltered, outofbounds, displayRadius ), stdata.renderInterval, "Pcp4_median", BdvOptions.options().is2D() ).setDisplayRange( 0, 6 );
		BdvFunctions.show( median, stdata.renderInterval, "Pcp4_median_full", BdvOptions.options().is2D() ).setDisplayRange( 0, 6 );

		//final RandomAccessibleInterval< FloatType > img = Render.render( data, stdata.renderInterval, outofbounds, displayRadius );
		//new ImageJ();
		//ImageJFunctions.show( img );
	}
}
