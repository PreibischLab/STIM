package test;

import java.io.File;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import filter.Filters;
import filter.GaussianFilterFactory;
import filter.MedianFilterFactory;
import importer.Reader;
import importer.STData;
import net.imglib2.RealPointSampleList;
import net.imglib2.type.numeric.real.FloatType;
import render.Render;
import util.ImgLib2Util;

public class Vistools3d
{
	public static void main( String[] args )
	{
		final STData stdata = Reader.read(
				new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/fly_3d_data/geometry.txt" ),
				new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/fly_3d_data/sdge_1297_cells_3039_locations_84_markers.txt" ),
				new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/fly_3d_data/gene_names.txt" ),
				1.0 );

		final double displayRadius = stdata.distanceStats.median / 2.0;
		final double medianRadius = stdata.distanceStats.median * 2.0;

		// gauss crisp
		double gaussRenderSigma = stdata.distanceStats.median / 4; 
		double gaussRenderRadius = displayRadius;

		final FloatType outofbounds = new FloatType( 0 );

		final RealPointSampleList< FloatType > data = ImgLib2Util.wrapFloat( stdata.coordinates, stdata.genes.get( "ftz" ) );
		final RealPointSampleList< FloatType > medianFiltered = Filters.filter( data, new MedianFilterFactory<>( outofbounds, medianRadius ) );//outofbounds, medianRadius );

		BdvFunctions.show( Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) ), stdata.renderInterval, "ftz_gauss1" ).setDisplayRange( 0, 4 );
		BdvFunctions.show( Render.render( medianFiltered, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) ), stdata.renderInterval, "ftz_median_gauss1" ).setDisplayRange( 0, 4 );

		// gauss smooth
		gaussRenderSigma = displayRadius;
		gaussRenderRadius = displayRadius * 4;

		BdvFunctions.show( Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) ), stdata.renderInterval, "Pcp4_gauss2" ).setDisplayRange( 0, 9 );
		BdvFunctions.show( Render.render( medianFiltered, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) ), stdata.renderInterval, "Pcp4_median_gauss2" ).setDisplayRange( 0, 9 );

	}
}
