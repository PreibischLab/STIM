package test;

import java.io.File;

import bvv.util.Bvv;
import bvv.util.BvvFunctions;
import bvv.util.BvvSource;
import data.STData;
import importer.Parser;
import net.imglib2.RealPointSampleList;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;
import render.Render;
import transform.TransformCoordinates;
import transform.TransformIntensities;
import util.ImgLib2Util;

public class Vistools3d
{
	public static void main( String[] args )
	{
		final float offset = 1;

		final STData stdata = Parser.read(
				new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/fly_3d_data/geometry.txt" ),
				new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/fly_3d_data/sdge_1297_cells_3039_locations_84_markers.txt" ),
				new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/fly_3d_data/gene_names.txt" ) );

		TransformIntensities.mul( stdata, 100000 );// *100000 + offset
		TransformIntensities.add( stdata, 1 );// *100000 + offset

		//TransformCoordinates.scale( stdata, 2.0 );
		TransformCoordinates.zeroMin( stdata );

		stdata.printInfo();

		final double displayRadius = stdata.distanceStats.median / 2.0;
		final double medianRadius = stdata.distanceStats.median * 2.0;

		stdata.expandInterval( medianRadius );

		// gauss crisp
		double gaussRenderSigma = stdata.distanceStats.median / 4; 
		double gaussRenderRadius = displayRadius;

		final UnsignedShortType outofbounds = new UnsignedShortType( 0 );

		final Pair< Double, Double > minmax = TransformIntensities.minmax( stdata );

		final RealPointSampleList< UnsignedShortType > ftz = ImgLib2Util.wrapUnsignedShort( stdata.coordinates, stdata.genes.get( "ftz" ), minmax.getA(), minmax.getB(), offset );
		final RealPointSampleList< UnsignedShortType > eve = ImgLib2Util.wrapUnsignedShort( stdata.coordinates, stdata.genes.get( "eve" ), minmax.getA(), minmax.getB(), offset );
		final RealPointSampleList< UnsignedShortType > rrp4 = ImgLib2Util.wrapUnsignedShort( stdata.coordinates, stdata.genes.get( "Rrp4" ), minmax.getA(), minmax.getB(), offset );

		//BdvFunctions.show( Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) ), stdata.renderInterval, "ftz_gauss1" ).setDisplayRange( minmax.getA().get(), minmax.getB().get()  );
		//BdvFunctions.show( Render.renderNN( data, outofbounds, gaussRenderRadius ), stdata.renderInterval, "ftz_gauss1" ).setDisplayRange( minmax.getA().get(), minmax.getB().get()  );

		//ImageJFunctions.show( Views.interval( Views.raster( Render.renderNN( data, outofbounds, gaussRenderRadius ) ), stdata.renderInterval ) );

		final BvvSource source0 = BvvFunctions.show( Views.interval( Views.raster( Render.renderNN( ftz, outofbounds, gaussRenderRadius ) ), stdata.renderInterval ), "ftz" );
		source0.setDisplayRange( 0, 65535 );
		source0.setColor( new ARGBType( 0xff00ff00 ) );

		BvvSource source1 = BvvFunctions.show( Views.interval( Views.raster( Render.renderNN( eve, outofbounds, gaussRenderRadius ) ), stdata.renderInterval ), "eve", Bvv.options().addTo( source0 ) );
		source1.setDisplayRange( 0, 25535 );
		source1.setColor( new ARGBType( 0xffff0000 ) );

		BvvSource source2 = BvvFunctions.show( Views.interval( Views.raster( Render.renderNN( rrp4, outofbounds, gaussRenderRadius ) ), stdata.renderInterval ), "eve", Bvv.options().addTo( source0 ) );
		source2.setDisplayRange( 0, 25535 );
		source2.setColor( new ARGBType( 0xff0000ff ) );


		// gauss smooth
		gaussRenderSigma = displayRadius / 2;
		gaussRenderRadius = displayRadius * 2;

		//new ImageJ();
		//ImageJFunctions.show( Views.interval( Views.raster( Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) ) ), stdata.renderInterval ) );
		//BvvFunctions.show( Views.interval( Views.raster( Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) ) ), stdata.renderInterval ), "ftz" );

	}
}
