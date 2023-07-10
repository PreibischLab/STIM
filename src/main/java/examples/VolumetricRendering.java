package examples;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import bvv.util.Bvv;
import bvv.util.BvvFunctions;
import bvv.util.BvvSource;
import data.STData;
import data.STDataStatistics;
import gui.STDataAssembly;
import imglib2.DoubleUnsignedShortConverter;
import io.SpatialDataContainer;
import io.Path;
import net.imglib2.IterableRealInterval;
import net.imglib2.converter.Converters;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;
import render.Render;
import transform.TransformCoordinates;
import transform.TransformIntensities;

public class VolumetricRendering
{
	public static void main( String[] args ) throws IOException
	{
		final String path = Path.getPath();

		long time = System.currentTimeMillis();

		final ExecutorService service = Executors.newFixedThreadPool(8);
		final STData stdata = SpatialDataContainer.openForReading(path + "examples.n5", service).openDatasetReadOnly("fly3d").readData().data();

		System.out.println( System.currentTimeMillis() - time + " ms." );

		System.out.println( stdata );

		TransformIntensities.add( stdata, 1 );
		TransformCoordinates.zeroMin( stdata );

		final STDataStatistics stStats = new STDataStatistics( stdata );

		System.out.println( stStats );

		final double displayRadius = stStats.getMedianDistance() / 2.0;
		final double medianRadius = stStats.getMedianDistance() * 2.0;

		// gauss crisp
		double gaussRenderSigma = stStats.getMedianDistance() / 4; 
		double gaussRenderRadius = displayRadius;

		final UnsignedShortType outofbounds = new UnsignedShortType( 0 );

		final Pair< Double, Double > minmax = TransformIntensities.minmax( stdata );
		final DoubleUnsignedShortConverter converter = new DoubleUnsignedShortConverter( minmax.getA(), minmax.getB() );

		final IterableRealInterval< UnsignedShortType > ftz = Converters.convert( stdata.getExprData( "ftz" ), converter, new UnsignedShortType() );
		final IterableRealInterval< UnsignedShortType > eve = Converters.convert( stdata.getExprData( "eve" ), converter, new UnsignedShortType() );
		final IterableRealInterval< UnsignedShortType > rrp4 = Converters.convert( stdata.getExprData( "Rrp4" ), converter, new UnsignedShortType() );

		//BdvFunctions.show( Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) ), stdata.renderInterval, "ftz_gauss1" ).setDisplayRange( minmax.getA().get(), minmax.getB().get()  );
		//BdvFunctions.show( Render.renderNN( data, outofbounds, gaussRenderRadius ), stdata.renderInterval, "ftz_gauss1" ).setDisplayRange( minmax.getA().get(), minmax.getB().get()  );

		//ImageJFunctions.show( Views.interval( Views.raster( Render.renderNN( data, outofbounds, gaussRenderRadius ) ), stdata.renderInterval ) );

		final BvvSource source0 = BvvFunctions.show( Views.interval( Views.raster( Render.renderNN( ftz, outofbounds, gaussRenderRadius ) ), stdata.getRenderInterval() ), "ftz" );
		source0.setDisplayRange( 0, 65535 );
		source0.setColor( new ARGBType( 0xff00ff00 ) );

		BvvSource source1 = BvvFunctions.show( Views.interval( Views.raster( Render.renderNN( eve, outofbounds, gaussRenderRadius ) ), stdata.getRenderInterval() ), "eve", Bvv.options().addTo( source0 ) );
		source1.setDisplayRange( 0, 25535 );
		source1.setColor( new ARGBType( 0xffff0000 ) );

		BvvSource source2 = BvvFunctions.show( Views.interval( Views.raster( Render.renderNN( rrp4, outofbounds, gaussRenderRadius ) ), stdata.getRenderInterval() ), "eve", Bvv.options().addTo( source0 ) );
		source2.setDisplayRange( 0, 25535 );
		source2.setColor( new ARGBType( 0xff0000ff ) );


		// gauss smooth
		gaussRenderSigma = displayRadius / 2;
		gaussRenderRadius = displayRadius * 2;
		service.shutdown();

		//new ImageJ();
		//ImageJFunctions.show( Views.interval( Views.raster( Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) ) ), stdata.renderInterval ) );
		//BvvFunctions.show( Views.interval( Views.raster( Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) ) ), stdata.renderInterval ), "ftz" );

	}
}
