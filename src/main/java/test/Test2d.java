package test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import filter.Filters;
import filter.MeanFilterFactory;
import filter.MedianFilterFactory;
import ij.ImageJ;
import importer.Reader;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealPointSampleList;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import render.Render;
import util.ImgLib2Util;
import util.ImgLib2Util.SimpleStats;

public class Test2d
{
	public static void main( String[] args )
	{
		final ArrayList< double[] > coordinates = Reader.readCoordinates( new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/patterns_examples_2d/locations.txt" ) );

		System.out.println( "Loaded " + coordinates.size() + " coordinates." );

		final RealInterval interval = Reader.getInterval( coordinates );
		final Interval renderInterval = ImgLib2Util.roundRealInterval( interval );

		System.out.println( "Interval: " + ImgLib2Util.printRealInterval( interval ) );

		final SimpleStats distanceStats = ImgLib2Util.distanceStats( coordinates );

		System.out.println( "Median Distance: " + distanceStats.median );
		System.out.println( "Average Distance: " + distanceStats.avg );
		System.out.println( "Min Distance: " + distanceStats.min );
		System.out.println( "Max Distance: " + distanceStats.max );

		System.out.println( "Interval: " + ImgLib2Util.printRealInterval( interval ) );

		final HashMap< String, double[] > values = Reader.readGenes( new File("/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/patterns_examples_2d/dge_normalized_small.txt" ), coordinates.size(), 0 );

		System.out.println( "Loaded: " + values.keySet().size() + " genes with " + coordinates.size() + " locations each." );

		for ( final String gene : values.keySet() )
			System.out.println( gene );

		System.out.println( "Computing ... " );

		final FloatType outofboundsFloat = new FloatType( -1 );
		final DoubleType outofboundsDouble = new DoubleType( -1 );

		final RealPointSampleList< FloatType > data = ImgLib2Util.wrapFloat( coordinates, values.get( "Pcp4" ) );
		final RealPointSampleList< FloatType > median = Filters.filter( data, new MedianFilterFactory<>( outofboundsFloat, distanceStats.median * 2 ) );
		final RealPointSampleList< DoubleType > avg = Filters.filter( data, new MeanFilterFactory<>( outofboundsDouble, distanceStats.median * 2 ) );

		final RandomAccessibleInterval< FloatType > upImg = Views.translate( ArrayImgs.floats( ImgLib2Util.dimensions( renderInterval ) ), ImgLib2Util.min( renderInterval ) );
		Filters.filter( data, upImg, new MedianFilterFactory<>( outofboundsFloat, distanceStats.median * 2 ) );

		System.out.println( "Rendering ... " );

		final RandomAccessibleInterval< FloatType > img = Render.raster( Render.renderNN( data, outofboundsFloat, distanceStats.median / 2.0 ), renderInterval );
		final RandomAccessibleInterval< FloatType > medianImg = Render.raster( Render.renderNN( median, outofboundsFloat, distanceStats.median / 2.0 ), renderInterval );
		final RandomAccessibleInterval< DoubleType > avgImg = Render.raster( Render.renderNN( avg, outofboundsDouble, distanceStats.median / 2.0 ), renderInterval );

		new ImageJ();
		ImageJFunctions.show( img );
		ImageJFunctions.show( medianImg );
		ImageJFunctions.show( upImg );
		ImageJFunctions.show( avgImg );
	}
}
