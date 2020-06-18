package align;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import analyze.ExtractGeneLists;
import data.STData;
import data.STDataStatistics;
import filter.GaussianFilterFactory;
import filter.GaussianFilterFactory.WeightType;
import ij.ImageJ;
import imglib2.ImgLib2Util;
import imglib2.phasecorrelation.PhaseCorrelation2;
import imglib2.phasecorrelation.PhaseCorrelation2Util;
import imglib2.phasecorrelation.PhaseCorrelationPeak2;
import io.N5IO;
import io.Path;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.complex.ComplexDoubleType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import render.Render;
import util.Threads;

public class Pairwise
{
	public static class Result
	{
		final double[] histogram = new double[ 360 ];
		final double[] tx = new double[ 360 ];
		final double[] ty = new double[ 360 ];
	}

	public static void align( final STData stdataA, final STData stdataB )
	{
		final STDataStatistics statA = new STDataStatistics( stdataA );
		final STDataStatistics statB = new STDataStatistics( stdataB );

		final boolean doGradientDescent = true;
		final int topG = 50;
		final int topN = 5;

		/*
		final ArrayList< Pair< String, Double > > listA = ExtractGeneLists.sortByStDevIntensity( stdataA );
		final ArrayList< Pair< String, Double > > listB = ExtractGeneLists.sortByStDevIntensity( stdataB );

		final ArrayList< String > genesA = new ArrayList<>();
		final ArrayList< String > genesB = new ArrayList<>();

		for ( int i = 0; i < topG; ++i )
		{
			genesA.add( listA.get( i ).getA() );
			genesB.add( listB.get( i ).getA() );
		}

		final List< String > genesToTest = commonGeneNames( genesA, genesB );
		System.out.println( "testing " + genesToTest.size() + " genes." );
		*/
		final List< String > genesToTest = new ArrayList<>();
		genesToTest.add( "Hpca" );
		genesToTest.add( "Fth1" );
		genesToTest.add( "Ubb" );
		genesToTest.add( "Pcp4" );

		final AffineTransform2D scalingTransform = new AffineTransform2D();
		scalingTransform.scale( 0.025 );

		final Interval interval = getCommonInterval( stdataA, stdataB );

		final int numThreads = Threads.numThreads();
		final ExecutorService serviceGlobal = Threads.createFixedExecutorService( numThreads );

		final List< Callable< Void > > tasks = new ArrayList<>();
		final AtomicInteger nextGene = new AtomicInteger();

		final Result result = new Result();

		for ( int threadNum = 0; threadNum < numThreads; ++threadNum )
		{
			tasks.add( () -> {
				final ExecutorService serviceLocal = Threads.createFixedExecutorService( 1 );

				for ( int i = nextGene.getAndIncrement(); i < genesToTest.size(); i = nextGene.getAndIncrement() )
				{
					final String gene = genesToTest.get( i );

					final List< Pair< PhaseCorrelationPeak2, Double > > alignParamList = alignPairwise( stdataA, statA, stdataB, statB, gene, topN, doGradientDescent, scalingTransform, interval, serviceLocal );

					for ( final Pair< PhaseCorrelationPeak2, Double > alignParams : alignParamList )
					{
						final int deg = (int)Math.round( alignParams.getB() ) % 360; // e.g. 359.6 should be 0
						final double weight = alignParams.getA().getCrossCorr();

						synchronized ( result )
						{
							result.histogram[ deg ] += weight;
							result.tx[ deg ] += alignParams.getA().getShift().getDoublePosition( 0 ) * weight;
							result.ty[ deg ] += alignParams.getA().getShift().getDoublePosition( 1 ) * weight;
						}

						System.out.println( "TOP ("+gene+"): " + alignParams.getB() + ", " + alignParams.getA().getCrossCorr() + ", " + Util.printCoordinates( alignParams.getA().getShift() ) );
					}

					System.out.println();

					//final Pair< PhaseCorrelationPeak2, Double > alignParams = alignParamList.get( 0 );
					//System.out.println( "TOP ("+gene+"): " + alignParams.getB() + ", " + alignParams.getA().getCrossCorr() + ", " + Util.printCoordinates( alignParams.getA().getShift() ) );			
				}

				serviceLocal.shutdown();

				return null;
			} );
		}

		try
		{
			final List< Future< Void > > futures = serviceGlobal.invokeAll( tasks );
			for ( final Future< Void > future : futures )
				future.get();
		}
		catch ( final InterruptedException | ExecutionException e )
		{
			e.printStackTrace();
			throw new RuntimeException( e );
		}

		System.out.println( );

		int bestDegree = -1;
		double degreeWeight = 0;

		for ( int i = 0; i < result.histogram.length; ++i )
		{
			if ( result.histogram[ i ] > degreeWeight )
			{
				degreeWeight = result.histogram[ i ];
				bestDegree = i;
			}

			result.tx[ i ] /= result.histogram[ i ];
			result.ty[ i ] /= result.histogram[ i ];

			System.out.println( i + "\t" + result.histogram[ i ] + "\t" + result.tx[ i ] + "\t" + result.ty[ i ] );
		}

		System.out.println( "Best degree = " + bestDegree + ": " + result.tx[ bestDegree ] + ", " + result.ty[ bestDegree ] );

		// assemble a final affine transform that maps B to A in world coordinates

		// we need the offsets (as zero-min is input to the PCM)
		final AffineTransform2D transformB = scalingTransform.copy();
		transformB.rotate( Math.toRadians( bestDegree ) );

		final Interval intervalA = ImgLib2Util.transformInterval( interval, scalingTransform );
		final Interval intervalB = ImgLib2Util.transformInterval( interval, transformB );

		System.out.println( "Interval A: " + Util.printInterval( intervalA ) );
		System.out.println( "Interval B: " + Util.printInterval( intervalB ) );

		final double tx = ( intervalA.realMin( 0 ) - intervalB.realMin( 0 ) ) + result.tx[ bestDegree ];
		final double ty = ( intervalA.realMin( 1 ) - intervalB.realMin( 1 ) ) + result.ty[ bestDegree ];

		// assemble the transform
		final AffineTransform2D finalTransform = scalingTransform.copy();
		finalTransform.rotate( bestDegree );
		finalTransform.translate( tx, ty );
		finalTransform.preConcatenate( scalingTransform.inverse() );

		System.out.println( "Final transform: " + finalTransform );

		// visualize result for Calm1
		final long[] shiftL = new long[ 2 ];
		shiftL[ 0 ] = Math.round( result.tx[ bestDegree ] );
		shiftL[ 1 ] = Math.round( result.ty[ bestDegree ] );

		//shiftL[ 0 ] = Math.round( -11.63879058 );
		//shiftL[ 1 ] = Math.round( -23.01504033 );
		//transformB.rotate( Math.toRadians( 289 ) );

		final RandomAccessibleInterval< DoubleType > imgB = Views.zeroMin( display( stdataB, statB, "Calm1", ImgLib2Util.transformInterval( interval, transformB ), transformB ) );

		new ImageJ();

		Pair< RandomAccessibleInterval<FloatType>, RandomAccessibleInterval<FloatType>> res =
				PhaseCorrelation2Util.dummyFuse(
						Views.zeroMin( display( stdataA, statA, "Calm1", ImgLib2Util.transformInterval( interval, scalingTransform ), scalingTransform ) ),
						imgB, new Point( shiftL ), serviceGlobal );

		ImageJFunctions.show(res.getA());
		ImageJFunctions.show(res.getB());

		serviceGlobal.shutdown();
	}

	public static List< Pair< PhaseCorrelationPeak2, Double > > alignPairwise(
			final STData stdataA, final STDataStatistics statA,
			final STData stdataB, final STDataStatistics statB,
			final String gene, final int topN,
			final boolean doGradientDescent,
			final AffineTransform2D scalingTransform,
			final Interval interval,
			final ExecutorService service )
	{
		final ArrayList< Pair< PhaseCorrelationPeak2, Double > > topPeaks = new ArrayList<>();

		final RandomAccessibleInterval< DoubleType > imgA = ImgLib2Util.copyImg( display( stdataA, statA, gene, ImgLib2Util.transformInterval( interval, scalingTransform ), scalingTransform ), new ArrayImgFactory<>( new DoubleType() ), service );

		// initial scouting
		System.out.println( "Scouting: " + gene );

		for ( int deg = 0; deg < 360; deg += 1 )
		{
			final AffineTransform2D transformB = scalingTransform.copy();
			transformB.rotate( Math.toRadians( deg ) );

			final RandomAccessibleInterval< DoubleType > imgB = display( stdataB, statB, gene, ImgLib2Util.transformInterval( interval, transformB ), transformB );

			final PhaseCorrelationPeak2 shiftPeak = testPair( Views.zeroMin( imgA ), Views.zeroMin( imgB ), service );
			insertIntoList( topPeaks, topN, shiftPeak, deg );
			//System.out.println( deg + ": " + shiftPeak.getCrossCorr() + ", " + Util.printCoordinates( shiftPeak.getShift() ) );
		}

		if ( !doGradientDescent )
			return topPeaks;

		// gradient descent for every inital Peak
		System.out.println( "Gradient descent: " + gene );

		final ArrayList< Pair< PhaseCorrelationPeak2, Double > > revisedTopPeaks = new ArrayList<>();

		for ( final Pair< PhaseCorrelationPeak2, Double > peak : topPeaks )
		{
			Pair< PhaseCorrelationPeak2, Double > bestPeak = peak;

			for ( double step = 2; step > 0.001; step /= 1.5 )
			{
				boolean updated;

				do
				{
					updated = false;
					double deg = bestPeak.getB();

					AffineTransform2D transformB = scalingTransform.copy();
					transformB.rotate( Math.toRadians( deg + step ) );

					PhaseCorrelationPeak2 shiftPeak = testPair( imgA, Views.zeroMin( display( stdataB, statB, gene, ImgLib2Util.transformInterval( interval, transformB ), transformB ) ), service );

					if ( shiftPeak.getCrossCorr() > bestPeak.getA().getCrossCorr() )
					{
						updated = true;
						if ( deg + step >= 360 )
							deg -= 360;
						bestPeak = new ValuePair< PhaseCorrelationPeak2, Double >( shiftPeak, deg + step );
					}

					transformB = scalingTransform.copy();
					transformB.rotate( Math.toRadians( deg - step ) );

					shiftPeak = testPair( imgA, Views.zeroMin( display( stdataB, statB, gene, ImgLib2Util.transformInterval( interval, transformB ), transformB ) ), service );

					if ( shiftPeak.getCrossCorr() > bestPeak.getA().getCrossCorr() )
					{
						updated = true;
						if ( deg - step < 0 )
							deg += 360;
						bestPeak = new ValuePair< PhaseCorrelationPeak2, Double >( shiftPeak, deg - step );
					}
				} while ( updated );
			}

			// How is it possible that only 4 locations are in here with topN == 5 (e.g. mt-Nd1)
			insertIntoList( revisedTopPeaks, topN, bestPeak.getA(), bestPeak.getB() );
		}

		//for ( int i = 0; i < revisedTopPeaks.size(); ++i )
		//	System.out.println( "TOP " + i + " ("+gene+"): " + revisedTopPeaks.get( i ).getB() + ", " + revisedTopPeaks.get( i ).getA().getCrossCorr() + ", " + Util.printCoordinates( revisedTopPeaks.get( i ).getA().getShift() ) );

		return revisedTopPeaks;
	}

	public static void insertIntoList( final List< Pair< PhaseCorrelationPeak2, Double > > topPeaks, final int topN, final PhaseCorrelationPeak2 newPeak, final double deg )
	{
		if ( topPeaks.size() == 0 )
			topPeaks.add( new ValuePair< PhaseCorrelationPeak2, Double >( newPeak, deg ) );
		else
		{
			boolean inserted = false;
			for ( int i = 0; i < Math.min( topN, topPeaks.size() ); ++i )
			{
				if ( newPeak.getCrossCorr() > topPeaks.get( i ).getA().getCrossCorr() )
				{
					topPeaks.add( i, new ValuePair< PhaseCorrelationPeak2, Double >( newPeak, deg ) );

					if ( topPeaks.size() >= topN )
						topPeaks.remove( topPeaks.size() - 1 );

					inserted = true;
					break;
				}
			}

			if ( !inserted && topPeaks.size() < topN )
				topPeaks.add( new ValuePair< PhaseCorrelationPeak2, Double >( newPeak, (double)deg ) );
		}
	}

	public static PhaseCorrelationPeak2 testPair( final RandomAccessibleInterval< DoubleType > imgA, final RandomAccessibleInterval< DoubleType > imgB, final ExecutorService service  )
	{
		RandomAccessibleInterval<DoubleType> pcm = PhaseCorrelation2.calculatePCM(
				imgA, imgB,
				new ArrayImgFactory<DoubleType>( new DoubleType() ),
				new ArrayImgFactory<ComplexDoubleType>( new ComplexDoubleType() ),
				service );

		//List<PhaseCorrelationPeak2> peaks = PhaseCorrelation2Util.getPCMMaxima(pcm, service, 5, false );
		//for ( final PhaseCorrelationPeak2 p : peaks )
		//	System.out.println( "PCM: " + p.getPhaseCorr() + ", " + Util.printCoordinates( p.getPcmLocation() ) );

		return PhaseCorrelation2.getShift(pcm, imgA, imgB, 5, 10000, false, false, service);// Threads.createFixedExecutorService( 1 ));
	}

	public static RandomAccessibleInterval< DoubleType > display(
			final STData stdata,
			final STDataStatistics stStats,
			final String gene,
			final Interval renderInterval,
			final AffineTransform2D transform )
	{
		//System.out.println( "Mean distance: " + stStats.getMeanDistance());
		//System.out.println( "Median distance: " + stStats.getMedianDistance() );
		//System.out.println( "Max distance: " + stStats.getMaxDistance() );

		// gauss crisp
		double gaussRenderSigma = stStats.getMedianDistance();

		final DoubleType outofbounds = new DoubleType( 0 );

		IterableRealInterval< DoubleType > data = stdata.getExprData( gene );

		data = Converters.convert(
				data,
				new Converter< DoubleType, DoubleType >()
				{
					@Override
					public void convert( final DoubleType input, final DoubleType output )
					{
						output.set( input.get() + 0.1 );
						
					}
				},
				new DoubleType() );

		// TODO: this might all make more sense after normalization now, yay!

		//data = TransformCoordinates.sample( data, stStats.getMedianDistance() );

		//data = Filters.filter( data, new DensityFilterFactory<>( new DoubleType(), medianDistance ) );
		//data = Filters.filter( data, new MedianFilterFactory<>( outofbounds, medianDistance * 2 ) );
		//data = Filters.filter( data, new MeanFilterFactory<>( outofbounds, medianDistance * 10 ) );
		//data = Filters.filter( data, new GaussianFilterFactory<>( outofbounds, medianDistance * 2, stStats.getMedianDistance(), true ) );

		//final Pair< DoubleType, DoubleType > minmax = ImgLib2Util.minmax( data );
		//System.out.println( "Min intensity: " + minmax.getA() );
		//System.out.println( "Max intensity: " + minmax.getB() );

		// for rendering the input pointcloud
		final RealRandomAccessible< DoubleType > renderRRA = Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderSigma, WeightType.NONE ) );

		// for rendering a 16x (median distance), regular sampled pointcloud
		//final RealRandomAccessible< DoubleType > renderRRA = Render.render( data, new GaussianFilterFactory<>( outofbounds, stStats.getMedianDistance() / 4.0, WeightType.NONE ) );

		//BdvOptions options = BdvOptions.options().is2D().numRenderingThreads( Runtime.getRuntime().availableProcessors() );
		//BdvStackSource< ? > bdv = BdvFunctions.show( renderRRA, stdata.getRenderInterval(), gene, options );
		//bdv.setDisplayRange( 0.1, minmax.getB().get() * 2 );
		//bdv.setDisplayRangeBounds( 0, minmax.getB().get() * 8 );

		//System.out.println( new Date(System.currentTimeMillis()) + ": Rendering interval " + Util.printInterval( interval ) + " with " + Threads.numThreads() + " threads ... " );
		
		final RandomAccessibleInterval< DoubleType > rendered = Views.interval( RealViews.affine( renderRRA, transform ), renderInterval );
		//ImageJFunctions.show( rendered, Threads.createFixedExecutorService() ).setTitle( stdata.toString() );
		//System.out.println( new Date(System.currentTimeMillis()) + ": Done..." );

		return rendered;
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

	public static Interval getCommonInterval( final STData stDataA, final STData stDataB )
	{
		final ArrayList< STData > list = new ArrayList<>();
		list.add( stDataA );
		list.add( stDataB );

		return getCommonInterval( list );
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

		//final String[] pucks = new String[] { "Puck_180602_20", "Puck_180602_18", "Puck_180602_17", "Puck_180602_16", "Puck_180602_15", "Puck_180531_23", "Puck_180531_22", "Puck_180531_19", "Puck_180531_18", "Puck_180531_17", "Puck_180531_13", "Puck_180528_22", "Puck_180528_20" };
		//final String[] pucks = new String[] { "Puck_180531_23" };
		final String[] pucks = new String[] { "Puck_180531_23", "Puck_180531_22" };
		//final String[] pucks = new String[] { "Puck_180531_18", "Puck_180531_17" };


		align(
				N5IO.readN5( new File( path + "slide-seq/" + pucks[ 0 ] + "-normalized.n5" ) ),
				N5IO.readN5( new File( path + "slide-seq/" + pucks[ 1 ] + "-normalized.n5" ) ) );

		SimpleMultiThreading.threadHaltUnClean();

		final ArrayList< Pair< STData, STDataStatistics > > slides = new ArrayList<>();

		for ( final String puck : pucks )
		{
			final STData slide = /*new NormalizingSTData*/( N5IO.readN5( new File( path + "slide-seq/" + puck + "-normalized.n5" ) ) );//.copy();
			final STDataStatistics stat = new STDataStatistics( slide );

			slides.add(  new ValuePair<>( slide, stat ) );
		}

		final Interval interval = getCommonInterval( slides.stream().map( pair -> pair.getA() ).collect( Collectors.toList() ) );

		new ImageJ();

		final AffineTransform2D transform = new AffineTransform2D();
		transform.scale( 0.25 );

		for ( final Pair< STData, STDataStatistics > slide : slides )
		{
			display( slide.getA(), slide.getB(), "Pcp4", ImgLib2Util.transformInterval( interval, transform ), transform );
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
