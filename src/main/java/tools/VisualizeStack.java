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
import data.STData;
import data.STDataImgLib2;
import data.STDataN5;
import data.STDataStatistics;
import data.STDataUtils;
import filter.Filters;
import filter.GaussianFilterFactory;
import filter.GaussianFilterFactory.WeightType;
import filter.MeanFilterFactory;
import filter.MedianFilterFactory;
import imglib2.ExpValueRealIterable;
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

	public static RealRandomAccessible< DoubleType > getRendered(
			final STData puckData,
			final String gene,
			final STDataStatistics puckDataStatistics,
			final AffineTransform2D transform,
			final AffineTransform intensityTransform )
	{
		return getRendered(puckData, gene, puckDataStatistics, transform, intensityTransform, 0, 0 );
	}

	public static RealRandomAccessible< DoubleType > getRendered(
			final STData puckData,
			final String gene,
			final STDataStatistics puckDataStatistics,
			final AffineTransform2D transform,
			final AffineTransform intensityTransform,
			final double medianRadius,
			final double gaussRadius )
	{
		final double m00 = intensityTransform.getRowPackedCopy()[ 0 ];
		final double m01 = intensityTransform.getRowPackedCopy()[ 1 ];

		IterableRealInterval< DoubleType > data =
				Converters.convert(
						 puckData.getExprData( gene ),
						(a,b) -> b.set( a.get() * m00 + m01 + 0.1 ),
						new DoubleType() );

		if ( transform != null )
			data = new TransformedIterableRealInterval<>(
					data,
					transform );

		// gauss crisp
		double gaussRenderSigma = puckDataStatistics.getMedianDistance();

		// filter the iterable
		if ( medianRadius > 0 )
			data = Filters.filter( data, new MedianFilterFactory<>( new DoubleType( 0 ), medianRadius ) );

		if ( gaussRadius > 0 )
			data = Filters.filter( data, new GaussianFilterFactory<>( new DoubleType( 0 ), gaussRadius,  WeightType.BY_SUM_OF_WEIGHTS ) );

		//return Render.renderNN( data );
		//return Render.renderNN( data, new DoubleType( 0 ), gaussRenderRadius );
		//return Render.render( data, new MeanFilterFactory<>( new DoubleType( 0 ), 2 * gaussRenderSigma ) );
		//return Render.render( data, new MedianFilterFactory<>( new DoubleType( 0 ), 2 * gaussRenderSigma ) );
		return Render.render( data, new GaussianFilterFactory<>( new DoubleType( 0 ), 4 * gaussRenderSigma, gaussRenderSigma, WeightType.NONE ) );
	}

	public static void render2d( final STData puckData, final STDataStatistics puckDataStatistics, final AffineTransform2D transform, final AffineTransform intensityTransform )
	{
		final String gene = "Ubb";
		final RealRandomAccessible< DoubleType > renderRRA = getRendered( puckData, gene, puckDataStatistics, transform, intensityTransform );

		final Interval interval = puckData.getRenderInterval();
		final BdvOptions options = BdvOptions.options().is2D().numRenderingThreads( Runtime.getRuntime().availableProcessors() );

		/*
		new ImageJ();
		final RandomAccessibleInterval< DoubleType > rendered = Views.interval( Views.raster( renderRRA ), interval );
		ImageJFunctions.show( rendered, Threads.createFixedExecutorService() );
		final int geneIndex = puckData.getIndexForGene( "Pcp4" );
		expr.setValueIndex( geneIndex );
		ImageJFunctions.show( rendered, Threads.createFixedExecutorService() );
		//SimpleMultiThreading.threadHaltUnClean();
		*/

		BdvStackSource<?> bdv = BdvFunctions.show( renderRRA, interval, gene, options/*.addTo( old )*/ );
		bdv.setDisplayRange( min, max );
		bdv.setDisplayRangeBounds( minRange, maxRange );
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

		do
		{
			SimpleMultiThreading.threadWait( 500 );

			if ( medianRadius < 30 )
				medianRadius += 3;

			if ( gaussRadius < 0 )
				gaussRadius += 3;

			String showGene = genesToTest.get( rnd.nextInt( genesToTest.size() ) );
			showGene = gene;
			System.out.println( showGene + ", " + medianRadius + ", " + gaussRadius );

			BdvStackSource<?> old = bdv;
			bdv = BdvFunctions.show(
					getRendered( puckData, showGene, puckDataStatistics, transform, intensityTransform, medianRadius, gaussRadius ),
					interval,
					showGene,
					options.addTo( old ) );
			bdv.setDisplayRange( min, max );
			bdv.setDisplayRangeBounds( minRange, maxRange );

			old.removeFromBdv();

		} while ( System.currentTimeMillis() > 0 );

	}

	public static void render3d(
			final ArrayList< STData > puckData,
			final ArrayList< STDataStatistics > puckDataStatistics,
			final ArrayList< AffineTransform2D > transforms,
			final ArrayList< AffineTransform > intensityTransforms )
	{
		final String gene = "Ubb";
		final ArrayList< IterableRealInterval< DoubleType > > slices = new ArrayList<>();
		final ArrayList< ExpValueRealIterable< DoubleType > > exprDates = new ArrayList<>();

		for ( int i = 0; i < puckData.size(); ++i )
		{
			final AffineTransform intensityTransform = intensityTransforms.get( i );
			final double m00 = intensityTransform.getRowPackedCopy()[ 0 ];
			final double m01 = intensityTransform.getRowPackedCopy()[ 1 ];

			System.out.println( m00 + ", " + m01 );

			// store the ExpValueRealIterables
			exprDates.add( ((STDataN5)puckData.get( i )).getExprData( gene ) );
			
			IterableRealInterval< DoubleType > data = 
					Converters.convert(
							exprDates.get( exprDates.size() - 1 ), //puckData.get( i ).getExprData( gene ),
							(a,b) -> b.set( a.get() * m00 + m01 + 0.1 ),
							new DoubleType() );

			if ( transforms != null )
				data = new TransformedIterableRealInterval<>(
						data,
						transforms.get( i ) );

			slices.add( data );
		}

		final double spacing = puckDataStatistics.get( 0 ).getMedianDistance() * 1;
		final StackedIterableRealInterval< DoubleType > stack = new StackedIterableRealInterval<>( slices, spacing );

		final DoubleType outofbounds = new DoubleType( 0 );

		// gauss crisp
		double gaussRenderSigma = puckDataStatistics.get( 0 ).getMedianDistance() * 1.0;
		//double gaussRenderRadius = puckDataStatistics.get( 0 ).getMedianDistance() * 4;

		final RealRandomAccessible< DoubleType > renderRRA = Render.render( stack, new GaussianFilterFactory<>( outofbounds, gaussRenderSigma, WeightType.NONE ) );

		final Interval interval2d = STDataUtils.getCommonInterval( puckData );
		final long[] minI = new long[] { interval2d.min( 0 ), interval2d.min( 1 ), 0 };
		final long[] maxI = new long[] { interval2d.max( 0 ), interval2d.max( 1 ), Math.round( ( puckData.size() ) * spacing ) };
		final Interval interval = new FinalInterval( minI, maxI );
		final BdvOptions options = BdvOptions.options().numRenderingThreads( Runtime.getRuntime().availableProcessors() );

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

		final ArrayList< STData > puckData = new ArrayList<>();
		final ArrayList< STDataStatistics > puckDataStatistics = new ArrayList<>();
		final ArrayList< AffineTransform2D > transforms = new ArrayList<>();
		final ArrayList< AffineTransform > intensityTransforms = new ArrayList<>();

		for ( final String puck : pucks )
		{
			puckData.add( N5IO.readN5( n5, puck ) );
			puckDataStatistics.add( new STDataStatistics( puckData.get( puckData.size() - 1) ) );

			final AffineTransform2D t = new AffineTransform2D();
			t.set( n5.getAttribute( n5.groupPath( puck ), "transform", double[].class ) );
			transforms.add( t );

			final AffineTransform i = new AffineTransform( 1 );
			double[] values =  n5.getAttribute( n5.groupPath( puck ), "intensity_transform", double[].class );
			i.set( values[ 0 ], values[ 1 ] );
			intensityTransforms.add( i );
		}

		if ( puckData.size() >= 1 )
			render2d( puckData.get( 0 ), puckDataStatistics.get( 0 ), transforms.get( 0 ), intensityTransforms.get( 0 ) );
		else
			render3d( puckData, puckDataStatistics, transforms, intensityTransforms );
	}
}
