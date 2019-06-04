package test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import filter.Filters;
import filter.MedianFilterFactory;
import importer.Parser;
import net.imglib2.RealPointSampleList;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import util.ImgLib2Util;
import util.ImgLib2Util.SimpleStats;

public class Test3d
{
	public static void main( String[] args )
	{
		final ArrayList< double[] > coordinates = Parser.readCoordinates( new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/fly_3d_data/geometry.txt" ) );

		System.out.println( "Loaded " + coordinates.size() + " coordinates." );

		SimpleStats distanceStats = ImgLib2Util.distanceStats( coordinates );

		System.out.println( "Median Distance: " + distanceStats.median );
		System.out.println( "Average Distance: " + distanceStats.avg );
		System.out.println( "Min Distance: " + distanceStats.min );
		System.out.println( "Max Distance: " + distanceStats.max );

		final ArrayList< String > geneNameList = Parser.readGeneNames( new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/fly_3d_data/gene_names.txt" ) );

		System.out.println( "Read " + geneNameList.size() + " gene names." );

		final HashMap< String, double[] > values = Parser.readGenes( new File("/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/fly_3d_data/sdge_1297_cells_3039_locations_84_markers.txt" ), geneNameList, coordinates.size() );

		System.out.println( "Loaded: " + values.keySet().size() + " genes with " + coordinates.size() + " locations each." );

		//for ( final String gene : values.keySet() )
		//	System.out.println( gene );

		System.out.println( "ftz: " + values.containsKey( "ftz" ) );

		for ( int i = 0; i < coordinates.size(); ++i )
			System.out.println( i + ": " + Util.printCoordinates( coordinates.get( i ) ) + " == " + values.get( "ftz" )[ i ] );

		final double medianRadius = distanceStats.median * 2.0;
		final FloatType outofbounds = new FloatType( 0 );

		final RealPointSampleList< FloatType > data = ImgLib2Util.wrapFloat( coordinates, values.get( "ftz" ) );
		final RealPointSampleList< FloatType > medianFiltered = Filters.filter( data, new MedianFilterFactory<>( outofbounds, medianRadius ) );//outofbounds, medianRadius );
	}
}
