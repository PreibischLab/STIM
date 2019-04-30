package test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import ij.ImageJ;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealPointSampleList;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import test.ImgLib2.SimpleStats;

public class Test
{
	public static void main( String[] args )
	{
		final ArrayList< double[] > coordinates = Reader.readCoordinates( new File( "/Users/spreibi/Downloads/patterns_examples/locations.txt" ) );

		System.out.println( "Loaded " + coordinates.size() + " coordinates." );

		final RealInterval interval = Reader.getInterval( coordinates );

		System.out.println( "Interval: " + Util.printRealInterval( interval ) );

		final SimpleStats distanceStats = ImgLib2.distanceStats( coordinates );

		System.out.println( "Median Distance: " + distanceStats.median );
		System.out.println( "Average Distance: " + distanceStats.avg );
		System.out.println( "Min Distance: " + distanceStats.min );
		System.out.println( "Max Distance: " + distanceStats.max );

		System.out.println( "Interval: " + Util.printRealInterval( interval ) );

		final HashMap< String, double[] > values = Reader.readGenes( new File("/Users/spreibi/Downloads/patterns_examples/dge_normalized_small.txt" ), coordinates.size() );

		System.out.println( "Loaded: " + values.keySet().size() + " genes with " + coordinates.size() + " values each." );

		for ( final String gene : values.keySet() )
			System.out.println( gene );

		System.out.println( "Computing ... " );

		final RealPointSampleList< FloatType > data = ImgLib2.wrapFloat( coordinates, values.get( "Pcp4" ) );
		final RealPointSampleList< FloatType > median = Filters.filterMedian( data, distanceStats.median * 2 );
		//final RealPointSampleList< FloatType > median2 = Filters.filterMedian( data, new IntervalIterator( ImgLib2.roundRealInterval( interval ) ), distanceStats.median * 2 );
		final RealPointSampleList< DoubleType > avg = Filters.filterAverage( data, distanceStats.median * 2 );

		System.out.println( "Rendering ... " );

		final RandomAccessibleInterval< FloatType > img = ImgLib2.render( data, ImgLib2.roundRealInterval( interval ), new FloatType( -1 ), distanceStats.median / 2.0 );
		final RandomAccessibleInterval< FloatType > medianImg = ImgLib2.render( median, ImgLib2.roundRealInterval( interval ), new FloatType( -1 ), distanceStats.median / 2.0 );
		final RandomAccessibleInterval< DoubleType > avgImg = ImgLib2.render( avg, ImgLib2.roundRealInterval( interval ), new DoubleType( -1 ), distanceStats.median / 2.0 );

		new ImageJ();
		ImageJFunctions.show( img );
		ImageJFunctions.show( medianImg );
		ImageJFunctions.show( avgImg );
	}
}
