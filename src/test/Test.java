package test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import ij.ImageJ;
import net.imglib2.KDTree;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealPointSampleList;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.neighborsearch.NearestNeighborSearchInterpolatorFactory;
import net.imglib2.neighborsearch.NearestNeighborSearch;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

public class Test
{
	public static void main( String[] args )
	{
		final ArrayList< double[] > coordinates = Reader.readCoordinates( new File( "/Users/spreibi/Downloads/patterns_examples/locations.txt" ) );

		System.out.println( "Loaded " + coordinates.size() + " coordinates." );

		final RealInterval interval = Reader.getInterval( coordinates );

		System.out.println( "Interval: " + Util.printRealInterval( interval ) );

		final HashMap< String, double[] > values = Reader.readGenes( new File("/Users/spreibi/Downloads/patterns_examples/dge_normalized_small.txt" ), coordinates.size() );

		System.out.println( "Loaded: " + values.keySet().size() + " genes with " + coordinates.size() + " values each." );

		for ( final String gene : values.keySet() )
			System.out.println( gene );

		final RealPointSampleList< FloatType > list = ImgLib2.wrapFloat( coordinates, values.get( "Pcp4" ) );

		// using nearest neighbor search we will be able to return a value an any position in space
		NearestNeighborSearch< FloatType > search =
			new NearestNeighborSearchOnKDTree< FloatType >(
				new KDTree< FloatType > ( list ) );

		// make it into RealRandomAccessible using nearest neighbor search
		RealRandomAccessible< FloatType > realRandomAccessible =
			Views.interpolate( search, new NearestNeighborSearchInterpolatorFactory< FloatType >() );

		// convert it into a RandomAccessible which can be displayed
		RandomAccessible< FloatType > randomAccessible = Views.raster( realRandomAccessible );

		RandomAccessibleInterval< FloatType > view = Views.interval(
				randomAccessible,
				ImgLib2.roundRealInterval( interval ) );

		new ImageJ();

		ImageJFunctions.show( view );
	}
}
