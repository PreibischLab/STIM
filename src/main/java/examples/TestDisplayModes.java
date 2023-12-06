package examples;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import data.STData;
import data.STDataStatistics;
import filter.GaussianFilterFactory;
import filter.GaussianFilterFactory.WeightType;
import io.Path;
import io.SpatialDataContainer;
import net.imglib2.IterableRealInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.type.numeric.real.DoubleType;
import render.MaxDistanceParam;
import render.Render;

public class TestDisplayModes
{
	public static void main( String[] args ) throws IOException
	{
		final String path = Path.getPath();

		long time = System.currentTimeMillis();

		final ExecutorService service = Executors.newFixedThreadPool(8);
		final STData stdata = SpatialDataContainer.openForReading(path + "slide-seq-test.n5", service).openDataset("Puck_180531_19").readData().data();

		System.out.println( System.currentTimeMillis() - time + " ms." );

		System.out.println( stdata );

		final STDataStatistics stStats = new STDataStatistics( stdata );

		System.out.println( stStats );


		final IterableRealInterval< DoubleType > data = stdata.getExprData( "Calm2" );

		final DoubleType outofbounds = new DoubleType( 0 );
		final double smoothness = 1.0;

		final RealRandomAccessible< DoubleType > renderNN =
				Render.renderNN( data );

		final RealRandomAccessible< DoubleType > renderNNT =
				Render.renderNN(data, outofbounds, new MaxDistanceParam( stStats.getMedianDistance() ) );

		final RealRandomAccessible< DoubleType > renderLinear =
				Render.renderLinear(data, 20, 2.0 );

		final RealRandomAccessible< DoubleType > renderLinearT =
				Render.renderLinear(data, 20, 2.0, outofbounds, new MaxDistanceParam( stStats.getMedianDistance() * 5 ) );

		final RealRandomAccessible< DoubleType > renderGauss =
				Render.render(
						data,
						new GaussianFilterFactory<>(
								outofbounds,
								stStats.getMedianDistance() * smoothness,
								WeightType.PARTIAL_BY_SUM_OF_WEIGHTS ) );

		BdvOptions options = new BdvOptions().is2D();
		BdvStackSource<?> bdv = null;

		bdv = BdvFunctions.show( renderNN, stdata.getRenderInterval(), "Calm2-NN", options.addTo(bdv) );
		bdv.setDisplayRange(0, 10);
		bdv = BdvFunctions.show( renderNNT, stdata.getRenderInterval(), "Calm2-NN-Thresholded", options.addTo(bdv) );
		bdv.setDisplayRange(0, 10);
		bdv = BdvFunctions.show( renderLinear, stdata.getRenderInterval(), "Calm2-Linear", options.addTo(bdv) );
		bdv.setDisplayRange(0, 10);
		bdv = BdvFunctions.show( renderLinearT, stdata.getRenderInterval(), "Calm2-Linear-Thresholded", options.addTo(bdv) );
		bdv.setDisplayRange(0, 10);
		bdv = BdvFunctions.show( renderGauss, stdata.getRenderInterval(), "Calm2-Gauss", options.addTo(bdv) );
		bdv.setDisplayRange(0, 10);

		bdv.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.SINGLE );
		service.shutdown();

		/*
		final IterableRealInterval< DoubleType > medianFiltered = Filters.filter( data, new MedianFilterFactory<>( outofbounds, medianRadius ) );//outofbounds, medianRadius );

		final RealRandomAccessible< DoubleType > median = new MedianRealRandomAccessible<>( data, outofbounds, medianRadius );

		//BdvFunctions.show( Render.render( data, outofbounds, displayRadius ), stdata.renderInterval, "Pcp4_raw", BdvOptions.options().is2D() ).setDisplayRange( 0, 6 );
		//BdvFunctions.show( Render.renderAvg( data, outofbounds, displayRadius * 3 ), stdata.renderInterval, "Pcp4_rawavg", BdvOptions.options().is2D() ).setDisplayRange( 0, 6 );

		BdvFunctions.show( Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, WeightType.PARTIAL_BY_SUM_OF_WEIGHTS ) ), stdata.getRenderInterval(), "Pcp4_gauss1", BdvOptions.options().is2D() ).setDisplayRange( 0, 4 );
		BdvFunctions.show( Render.render( medianFiltered, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, WeightType.NONE ) ), stdata.getRenderInterval(), "Pcp4_median_gauss1", BdvOptions.options().is2D() ).setDisplayRange( 0, 4 );

		// gauss smooth
		gaussRenderSigma = displayRadius;
		gaussRenderRadius = displayRadius * 4;

		BdvFunctions.show( Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, WeightType.NONE ) ), stdata.getRenderInterval(), "Pcp4_gauss2", BdvOptions.options().is2D() ).setDisplayRange( 0, 9 );
		BdvFunctions.show( Render.render( medianFiltered, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, WeightType.NONE ) ), stdata.getRenderInterval(), "Pcp4_median_gauss2", BdvOptions.options().is2D() ).setDisplayRange( 0, 9 );

		//BdvFunctions.show( Render.render( medianFiltered, outofbounds, displayRadius ), stdata.renderInterval, "Pcp4_median", BdvOptions.options().is2D() ).setDisplayRange( 0, 6 );
		BdvFunctions.show( median, stdata.getRenderInterval(), "Pcp4_median_full", BdvOptions.options().is2D() ).setDisplayRange( 0, 6 );

		//final RandomAccessibleInterval< FloatType > img = Render.render( data, stdata.renderInterval, outofbounds, displayRadius );
		//new ImageJ();
		//ImageJFunctions.show( img );*/
	}
}
