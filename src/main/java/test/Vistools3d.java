package test;

import java.io.File;

import bvv.util.BvvFunctions;
import filter.Filters;
import filter.GaussianFilterFactory;
import filter.MedianFilterFactory;
import ij.ImageJ;
import importer.Reader;
import importer.STData;
import net.imglib2.RealPointSampleList;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;
import render.Render;
import transform.TransformCoordinates;
import util.ImgLib2Util;

public class Vistools3d
{
	public static void main( String[] args )
	{
		final float offset = 1;

		final STData stdata = Reader.read(
				new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/fly_3d_data/geometry.txt" ),
				new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/fly_3d_data/sdge_1297_cells_3039_locations_84_markers.txt" ),
				new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/fly_3d_data/gene_names.txt" ),
				offset );

		TransformCoordinates.scale( stdata, 2.0 );
		TransformCoordinates.zeroMin( stdata );

		stdata.printInfo();

		final double displayRadius = stdata.distanceStats.median / 2.0;
		final double medianRadius = stdata.distanceStats.median * 2.0;


		stdata.expandInterval( medianRadius );

		// gauss crisp
		double gaussRenderSigma = stdata.distanceStats.median / 4; 
		double gaussRenderRadius = displayRadius;

		final UnsignedShortType outofbounds = new UnsignedShortType( 0 );

		final Pair< Double, Double > minmax = ImgLib2Util.minmax( stdata.genes.get( "ftz" ) );
		final RealPointSampleList< UnsignedShortType > data = ImgLib2Util.wrapUnsignedShort( stdata.coordinates, stdata.genes.get( "ftz" ), minmax.getA(), minmax.getB() * 2.0, offset );


		//BdvFunctions.show( Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) ), stdata.renderInterval, "ftz_gauss1" ).setDisplayRange( minmax.getA().get(), minmax.getB().get()  );
		//BdvFunctions.show( Render.renderNN( data, outofbounds, gaussRenderRadius ), stdata.renderInterval, "ftz_gauss1" ).setDisplayRange( minmax.getA().get(), minmax.getB().get()  );

		//ImageJFunctions.show( Views.interval( Views.raster( Render.renderNN( data, outofbounds, gaussRenderRadius ) ), stdata.renderInterval ) );

		//BvvFunctions.show( Views.interval( Views.raster( Render.renderNN( data, outofbounds, gaussRenderRadius ) ), stdata.renderInterval ), "" );
		//BvvFunctions.show( Views.interval( Views.raster( Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) ) ), stdata.renderInterval ), "" );


		// gauss smooth
		gaussRenderSigma = displayRadius / 2;
		gaussRenderRadius = displayRadius * 2;

		//new ImageJ();
		//ImageJFunctions.show( Views.interval( Views.raster( Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) ) ), stdata.renderInterval ) );
		BvvFunctions.show( Views.interval( Views.raster( Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) ) ), stdata.renderInterval ), "ftz" );

	}
}
