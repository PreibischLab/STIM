package align;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import data.STData;
import data.STDataStatistics;
import filter.Filters;
import filter.GaussianFilterFactory;
import filter.MedianFilterFactory;
import filter.realrandomaccess.MedianRealRandomAccessible;
import ij.ImageJ;
import io.N5IO;
import io.Path;
import net.imglib2.IterableRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.converter.read.ConvertedIterableRealInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Util;
import render.Render;
import util.Threads;

public class Pairwise
{
	public static void display( final STData stdata, final STDataStatistics stStats, final String gene )
	{
		final double displayRadius = stStats.getMedianDistance() / 2.0;
		final double medianRadius = stStats.getMedianDistance() * 2.0;

		System.out.println( "Mean distance: " + stStats.getMeanDistance());
		System.out.println( "Median distance: " + stStats.getMedianDistance() );
		System.out.println( "Max distance: " + stStats.getMaxDistance() );
		System.out.println( "Interval:"  + Util.printInterval( stdata.getRenderInterval() ) );

		// gauss crisp
		double gaussRenderSigma = stStats.getMedianDistance();
		double gaussRenderRadius = displayRadius * 8;

		final DoubleType outofbounds = new DoubleType( 0 );

		//final IterableRealInterval< DoubleType > data = stdata.getExprData( gene );

		final IterableRealInterval< DoubleType > data =
				Converters.convert(
						stdata.getExprData( gene ),
						new Converter< DoubleType, DoubleType >()
						{
							@Override
							public void convert( final DoubleType input, final DoubleType output )
							{
								output.set( input.get() + 1.0 );
								
							}
						},
						new DoubleType() );

		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;

		for ( final DoubleType t : data )
		{
			min = Math.min( min, t.get() );
			max = Math.max( max, t.get() );
		}

		System.out.println( "Min intensity: " + min );
		System.out.println( "Max intensity: " + max );

		final RealRandomAccessible< DoubleType > renderRRA = Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) );
		BdvFunctions.show( renderRRA, stdata.getRenderInterval(), gene, BdvOptions.options().is2D() ).setDisplayRange( 0, max );

		System.out.println( new Date(System.currentTimeMillis()) + ": Rendering full resolution with " + Threads.numThreads() + " threads ... " );

		final RandomAccessibleInterval< DoubleType > renderFull = Render.raster( renderRRA, stdata.getRenderInterval() );
		ImageJFunctions.show( renderFull, Threads.createFixedExecutorService() );

		System.out.println( new Date(System.currentTimeMillis()) + ": Done..." );

		//final IterableRealInterval< DoubleType > medianFiltered = Filters.filter( data, new MedianFilterFactory<>( outofbounds, medianRadius * 4 ) );//outofbounds, medianRadius );
		//BdvFunctions.show( Render.renderNN( data, outofbounds, 1000 ), stdata.getRenderInterval(), gene, BdvOptions.options().is2D() ).setDisplayRange( 0, 4 );
		//final RealRandomAccessible< DoubleType > median = new MedianRealRandomAccessible<>( data, outofbounds, stStats.getMedianDistance() );
		//BdvFunctions.show( median, stdata.getRenderInterval(), gene + "_median_full", BdvOptions.options().is2D() ).setDisplayRange( 0, 4 );
		System.out.println();
	}

	public static List< String > commonGeneNames( final List< String > names0, final List< String > names1 )
	{
		final ArrayList< String > commonGeneNames = new ArrayList<>();
		final HashSet< String > names0Hash = new HashSet<>( names0 );

		for ( final String name1 : names1 )
			if ( names0Hash.contains( name1 ) )
				commonGeneNames.add( name1 );

		return commonGeneNames;
	}

	public static void main( String[] args ) throws IOException
	{
		final String path = Path.getPath();

		final STData slide0 = N5IO.readN5( new File( path + "slide-seq/Puck_180531_22.n5" ) );
		final STData slide1 = N5IO.readN5( new File( path + "slide-seq/Puck_180531_23.n5" ) );

		System.out.println( "Genes 0: " + slide0.getGeneNames().size() );
		System.out.println( "Genes 1: " + slide1.getGeneNames().size() );

		List< String > commonGeneNames = commonGeneNames( slide0.getGeneNames(), slide1.getGeneNames() );

		System.out.println( "common genes: " + commonGeneNames.size() );

		final STDataStatistics stat0 = new STDataStatistics( slide0 );
		final STDataStatistics stat1 = new STDataStatistics( slide1 );

		new ImageJ();
		display( slide0, stat0, "Pcp4" );
		display( slide1, stat1, "Pcp4" );
	}
}
