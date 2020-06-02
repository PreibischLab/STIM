package align;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import data.STData;
import data.STDataStatistics;
import filter.DensityFilterFactory;
import filter.FilterFactory;
import filter.Filters;
import filter.GaussianFilterFactory;
import filter.MeanFilterFactory;
import filter.MedianFilterFactory;
import ij.ImageJ;
import imglib2.ImgLib2Util;
import imglib2.SteppingIntervalIterator;
import io.N5IO;
import io.Path;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import render.Render;
import util.Threads;

public class Pairwise
{
	public static void display( final STData stdata, final STDataStatistics stStats, final String gene, final AffineTransform2D transform, final Interval commonInterval )
	{
		final double displayRadius = stStats.getMedianDistance() / 2.0;
		final double medianRadius = stStats.getMedianDistance() * 1.0;

		final Interval interval = ImgLib2Util.transformInterval( commonInterval == null ? stdata.getRenderInterval() : commonInterval, transform );

		System.out.println( "Mean distance: " + stStats.getMeanDistance());
		System.out.println( "Median distance: " + stStats.getMedianDistance() );
		System.out.println( "Max distance: " + stStats.getMaxDistance() );

		// gauss crisp
		double gaussRenderSigma = stStats.getMedianDistance();
		double gaussRenderRadius = displayRadius * 8;

		final DoubleType outofbounds = new DoubleType( 0 );

		IterableRealInterval< DoubleType > data = stdata.getExprData( gene );

		data = Converters.convert(
				data,
				new Converter< DoubleType, DoubleType >()
				{
					@Override
					public void convert( final DoubleType input, final DoubleType output )
					{
						output.set( input.get() + 1.0 );
						
					}
				},
				new DoubleType() );

		//data = sample( data, stStats.getMedianDistance() );

		//data = Filters.filter( data, new DensityFilterFactory<>( new DoubleType(), medianRadius ) );
		//data = Filters.filter( data, new MedianFilterFactory<>( outofbounds, medianRadius * 3 ) );
		//data = Filters.filter( data, new MeanFilterFactory<>( outofbounds, medianRadius * 10 ) );
		//data = Filters.filter( data, new GaussianFilterFactory<>( outofbounds, medianRadius * 2, stStats.getMedianDistance(), true ) );

		final Pair< DoubleType, DoubleType > minmax = ImgLib2Util.minmax( data );

		System.out.println( "Min intensity: " + minmax.getA() );
		System.out.println( "Max intensity: " + minmax.getB() );

		// for rendering the input pointcloud
		final RealRandomAccessible< DoubleType > renderRRA = Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) );

		// for rendering a 16x (median distance), regular sampled pointcloud
		//final RealRandomAccessible< DoubleType > renderRRA = Render.render( data, new GaussianFilterFactory<>( outofbounds, stStats.getMedianDistance() * 2.0, stStats.getMedianDistance() / 2.0, true ) );


		BdvOptions options = BdvOptions.options().is2D().numRenderingThreads( Runtime.getRuntime().availableProcessors() );
		BdvStackSource< ? > bdv = BdvFunctions.show( renderRRA, stdata.getRenderInterval(), gene, options );
		bdv.setDisplayRange( 0.9, minmax.getB().get() );
		bdv.setDisplayRangeBounds( 0, minmax.getB().get() );

		System.out.println( new Date(System.currentTimeMillis()) + ": Rendering interval " + Util.printInterval( interval ) + " with " + Threads.numThreads() + " threads ... " );

		ImageJFunctions.show( Views.interval( RealViews.affine( renderRRA, transform ), interval ), Threads.createFixedExecutorService() ).setTitle( stdata.toString() );

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

	public static Interval getCommonInterval( final Collection< STData > slides )
	{
		long[] min = null, max = null;

		for ( final STData slide : slides )
		{
			if ( min == null )
			{
				min = new long[ slide.getRenderInterval().numDimensions() ];
				max = new long[ slide.getRenderInterval().numDimensions() ];
				
				slide.getRenderInterval().min( min );
				slide.getRenderInterval().max( max );
			}
			else
			{
				for ( int d = 0; d < min.length; ++d )
				{
					min[ d ] = Math.min( min[ d ], slide.getRenderInterval().min( d ) );
					max[ d ] = Math.max( max[ d ], slide.getRenderInterval().max( d ) );
				}
			}
		}

		if ( min != null )
			return new FinalInterval( min, max );
		else
			return null;
	}

	public static void main( String[] args ) throws IOException
	{
		final String path = Path.getPath();

		final String[] pucks = new String[] { "Puck_180602_20", "Puck_180602_18", "Puck_180602_17", "Puck_180602_16", "Puck_180602_15", "Puck_180531_23", "Puck_180531_22", "Puck_180531_19", "Puck_180531_18", "Puck_180531_17", "Puck_180531_13", "Puck_180528_22", "Puck_180528_20" };

		final ArrayList< Pair< STData, STDataStatistics > > slides = new ArrayList<>();

		for ( final String puck : pucks )
		{
			final STData slide = N5IO.readN5( new File( path + "slide-seq/" + puck + ".n5" ) );
			final STDataStatistics stat = new STDataStatistics( slide );

			slides.add(  new ValuePair<>( slide, stat ) );
		}

		final Interval interval = getCommonInterval( slides.stream().map( pair -> pair.getA() ).collect( Collectors.toList() ) );

		new ImageJ();

		final AffineTransform2D transform = new AffineTransform2D();
		transform.scale( 0.25 );

		for ( final Pair< STData, STDataStatistics > slide : slides )
		{
			display( slide.getA(), slide.getB(), "Pcp4", transform, interval );
			SimpleMultiThreading.threadHaltUnClean();
		}

		/*
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
		*/
	}
}
