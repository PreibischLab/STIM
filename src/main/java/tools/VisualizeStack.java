package tools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.janelia.saalfeldlab.n5.N5FSReader;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import data.STDataN5;
import data.STDataStatistics;
import data.STDataUtils;
import filter.Filters;
import filter.GaussianFilterFactory;
import filter.GaussianFilterFactory.WeightType;
import filter.MeanFilterFactory;
import filter.MedianFilterFactory;
import gui.STDataAssembly;
import imglib2.StackedIterableRealInterval;
import imglib2.TransformedIterableRealInterval;
import io.N5IO;
import io.Path;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converters;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.numeric.real.DoubleType;
import render.Render;

public class VisualizeStack
{
	protected static double minRange = 0;
	protected static double maxRange = 100;
	protected static double min = 0.1;
	protected static double max = 25;

	public static void render2d( final STDataAssembly stdata )
	{
		final String gene = "Ubb";
		final RealRandomAccessible< DoubleType > renderRRA = Render.getRealRandomAccessible( stdata, gene );

		final Interval interval =
				STDataUtils.getIterableInterval(
						new TransformedIterableRealInterval<>(
								stdata.data(),
								stdata.transform() ) );

		final BdvOptions options = BdvOptions.options().is2D().numRenderingThreads( Runtime.getRuntime().availableProcessors() / 2 );

		/*
		new ImageJ();
		final RandomAccessibleInterval< DoubleType > rendered = Views.interval( Views.raster( renderRRA ), interval );
		ImageJFunctions.show( rendered, Threads.createFixedExecutorService() );
		final int geneIndex = puckData.getIndexForGene( "Pcp4" );
		expr.setValueIndex( geneIndex );
		ImageJFunctions.show( rendered, Threads.createFixedExecutorService() );
		//SimpleMultiThreading.threadHaltUnClean();
		*/

		BdvStackSource<?> bdv = BdvFunctions.show( renderRRA, interval, gene, options );
		bdv.setDisplayRange( min, max );
		bdv.setDisplayRangeBounds( minRange, maxRange );
		//bdv.setColor( new ARGBType( ARGBType.rgba( 255, 0, 0, 0 ) ) );
		//bdv.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.SINGLE );
		bdv.setCurrent();

		final List< String > genesToTest = new ArrayList<>();
		genesToTest.add( "Calm1" );
		genesToTest.add( "Calm2" );
		genesToTest.add( "Hpca" );
		genesToTest.add( "Fth1" );
		genesToTest.add( "Ubb" );
		genesToTest.add( "Pcp4" );

		final Random rnd = new Random();
		double medianRadius = 0;
		double gaussRadius = 0;
		double avgRadius = 0;

		do
		{
			SimpleMultiThreading.threadWait( 500 );

			if ( medianRadius < 100 )
				medianRadius += 3;

			if ( gaussRadius < 0 )
				gaussRadius += 3;

			if ( avgRadius < 0 )
				avgRadius += 3;

			String showGene = genesToTest.get( rnd.nextInt( genesToTest.size() ) );
			showGene = gene;
			System.out.println( showGene + ", " + medianRadius + ", " + gaussRadius + ", " + avgRadius );

			BdvStackSource<?> old = bdv;
			bdv = BdvFunctions.show(
					Render.getRealRandomAccessible( stdata, showGene, 1.0, medianRadius, gaussRadius, avgRadius ),
					interval,
					showGene,
					options.addTo( old ) );
			bdv.setDisplayRange( min, max );
			bdv.setDisplayRangeBounds( minRange, maxRange );

			old.removeFromBdv();

		} while ( System.currentTimeMillis() > 0 );

	}

	public static void render3d( final List< STDataAssembly > stdata )
	{
		final String gene = "Ubb";
		final DoubleType outofbounds = new DoubleType( 0 );

		final ArrayList< IterableRealInterval< DoubleType > > slices = new ArrayList<>();

		for ( int i = 0; i < stdata.size(); ++i )
			slices.add( Render.getRealIterable( stdata.get( i ), gene ) );

		final double medianDistance = stdata.get( 0 ).statistics().getMedianDistance();

		// gauss crisp
		double gaussRenderSigma = medianDistance * 1.0;
		//double gaussRenderRadius = medianDistance * 4;

		final double spacing = medianDistance * 1;

		final Interval interval2d = STDataUtils.getCommonIterableInterval( slices );
		final long[] minI = new long[] { interval2d.min( 0 ), interval2d.min( 1 ), 0 - Math.round( Math.ceil( gaussRenderSigma * 3 ) ) };
		final long[] maxI = new long[] { interval2d.max( 0 ), interval2d.max( 1 ), Math.round( ( stdata.size() - 1 ) * spacing ) + Math.round( Math.ceil( gaussRenderSigma * 3 ) ) };
		final Interval interval = new FinalInterval( minI, maxI );

		final StackedIterableRealInterval< DoubleType > stack = new StackedIterableRealInterval<>( slices, spacing );

		final RealRandomAccessible< DoubleType > renderRRA = Render.render( stack, new GaussianFilterFactory<>( outofbounds, gaussRenderSigma, WeightType.NONE ) );

		final BdvOptions options = BdvOptions.options().numRenderingThreads( Runtime.getRuntime().availableProcessors() / 2 );
		BdvStackSource<?> bdv = BdvFunctions.show( renderRRA, interval, gene, options/*.addTo( old )*/ );
		bdv.setDisplayRange( min, max );
		bdv.setDisplayRangeBounds( minRange, maxRange );
		bdv.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.SINGLE );
		bdv.setCurrent();
	}

	public static void main( String[] args ) throws IOException
	{
		final String path = Path.getPath();
		final File n5Path = new File( path + "slide-seq-normalized.n5" );
		final N5FSReader n5 = N5IO.openN5( n5Path );
		final List< String > pucks = N5IO.listAllDatasets( n5 );

		final ArrayList< STDataAssembly > puckData = new ArrayList<>();

		for ( final String puck : pucks )
		{
			final STDataN5 data = N5IO.readN5( n5, puck );
			final STDataStatistics stats = new STDataStatistics( data );

			final AffineTransform2D t = new AffineTransform2D();
			t.set( n5.getAttribute( n5.groupPath( puck ), "transform", double[].class ) );

			final AffineTransform i = new AffineTransform( 1 );
			double[] values =  n5.getAttribute( n5.groupPath( puck ), "intensity_transform", double[].class );
			i.set( values[ 0 ], values[ 1 ] );

			puckData.add( new STDataAssembly( data, stats, t, i ) );
		}

		if ( puckData.size() >= 1 )
			render2d( puckData.get( 0 ) );
		else
			render3d( puckData );
	}
}
