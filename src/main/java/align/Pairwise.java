package align;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import analyze.ExtractGeneLists;
import data.STData;
import data.STDataStatistics;
import data.STDataUtils;
import filter.GaussianFilterFactory;
import filter.GaussianFilterFactory.WeightType;
import ij.ImageJ;
import imglib2.ImgLib2Util;
import imglib2.icp.ICP;
import imglib2.icp.NoSuitablePointsException;
import imglib2.icp.PointMatchIdentification;
import imglib2.icp.StDataPointMatchIdentification;
import imglib2.phasecorrelation.PhaseCorrelation2;
import imglib2.phasecorrelation.PhaseCorrelationPeak2;
import io.N5IO;
import io.Path;
import mpicbg.models.AffineModel2D;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.Model;
import mpicbg.models.NotEnoughDataPointsException;
import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealCursor;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.complex.ComplexDoubleType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import render.Render;
import transform.TransformCoordinates;
import util.Threads;

public class Pairwise
{
	// how many peaks to test in the PCM
	public static final int nHighest = 5;

	public static class Result
	{
		final double[] histogram = new double[ 360 ];
		final double[] tx = new double[ 360 ];
		final double[] ty = new double[ 360 ];
	}

	/**
	 * @param stdataA - data for A
	 * @param stdataB - data for B
	 * @param genesToUse - list of genes
	 * @param initialModel - maps B to A
	 * @param maxDistance - max search radius for corresponding point
	 * @param maxIterations - max num of ICP iterations
	 */
	public static AffineTransform2D alignICP( final STData stdataA, final STData stdataB, final List< String > genesToUse, final AffineTransform2D initialModel, final double maxDistance, final int maxIterations )
	{
		// regularly sample the reference dataset
		final STDataStatistics stStatsDataA = new STDataStatistics( stdataA );
		final STDataStatistics stStatsDataB = new STDataStatistics( stdataB );

		final ArrayList< RealPoint > listA = new ArrayList<>(); // reference
		final ArrayList< RealPoint > listB = new ArrayList<>(); // target

		for ( final RealLocalizable p : stdataA )
			listA.add( new RealPoint( p ) );

		System.out.println( "listA (reference): " + listA.size() );

		// tmp
		StDataPointMatchIdentification.sampling = 4.0;//Math.min( stStatsDataA.getMedianDistance(), stStatsDataB.getMedianDistance() ) / 2.0;
		IterableRealInterval< DoubleType > dataB = TransformCoordinates.sample( stdataB.getExprData( genesToUse.get( 0 ) ), StDataPointMatchIdentification.sampling );
		final RealCursor< DoubleType > c = dataB.localizingCursor();

		while ( c.hasNext() )
		{
			c.fwd();
			listB.add( new RealPoint( c ) );
		}

		//for ( final RealLocalizable p : stdataB )
		//	listB.add( new RealPoint( p ) );

		System.out.println( "listB (target): " + listB.size() );
		System.out.println( "listB (target) sampling: " + StDataPointMatchIdentification.sampling );

		final double[] m = initialModel.getRowPackedCopy(); //a.m00, a.m01, a.m02, a.m10, a.m11, a.m12

		final AffineModel2D model = new AffineModel2D();
		//model.set( m00, m10, m01, m11, m02, m12 );
		model.set( m[ 0 ], m[ 3 ], m[ 1 ], m[ 4 ], m[ 2 ], m[ 5 ] );

		final PointMatchIdentification< RealPoint > pmi = new StDataPointMatchIdentification<>( stdataB, stdataA, genesToUse, maxDistance );
		final ICP< RealPoint > icp = new ICP<>( listB, listA, pmi );

		int i = 0;
		double lastAvgError = 0;
		int lastNumCorresponding = 0;

		boolean converged = false;

		do
		{
			try
			{
				icp.runICPIteration( model, model );
			}
			catch ( Exception e )
			{
				System.out.println( "Failed with e: " + e );
				e.printStackTrace();
				return null;
			}

			if ( lastNumCorresponding == icp.getNumPointMatches() && lastAvgError == icp.getAverageError() )
				converged = true;

			lastNumCorresponding = icp.getNumPointMatches();
			lastAvgError = icp.getAverageError();
			
			System.out.println( i + ": " + icp.getNumPointMatches() + " matches, avg error [px] " + icp.getAverageError() + ", max error [px] " + icp.getMaximalError() );
		}
		while ( !converged && ++i < maxIterations );

		if ( icp.getPointMatches() == null )
		{
			return null;
		}
		else
		{
			model.toArray( m ); // The order is: m00, m10, m01, m11, m02, m12
			final AffineTransform2D result = new AffineTransform2D();
			// a.m00, a.m01, a.m02, a.m10, a.m11, a.m12
			result.set( m[ 0 ], m[ 2 ], m[ 4 ], m[ 1 ], m[ 3 ], m[ 5 ] );

			return result;
		}
	}

	public static List< String > genesToTest( final STData stdataA, final STData stdataB, final int numGenes )
	{
		final ArrayList< Pair< String, Double > > listA = ExtractGeneLists.sortByStDevIntensity( stdataA );
		final ArrayList< Pair< String, Double > > listB = ExtractGeneLists.sortByStDevIntensity( stdataB );

		final ArrayList< String > genesA = new ArrayList<>();
		final ArrayList< String > genesB = new ArrayList<>();

		for ( int i = 0; i < numGenes; ++i )
		{
			genesA.add( listA.get( i ).getA() );
			genesB.add( listB.get( i ).getA() );
		}

		final List< String > genesToTest = STDataUtils.commonGeneNames( genesA, genesB );

		System.out.println( "testing " + genesToTest.size() + " genes." );

		return genesToTest;
	}

	public static AffineTransform2D align(
			final STData stdataA,
			final STData stdataB,
			final List< String > genesToTest,
			final int topN,
			final boolean doGradientDescent  )
	{
		final STDataStatistics statA = new STDataStatistics( stdataA );
		final STDataStatistics statB = new STDataStatistics( stdataB );

		final AffineTransform2D scalingTransform = new AffineTransform2D();
		scalingTransform.scale( 0.025 );

		final Interval interval = STDataUtils.getCommonInterval( stdataA, stdataB );

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

					final List< Pair< PhaseCorrelationPeak2, Double > > alignParamList =
							alignGenePairwise( stdataA, statA, stdataB, statB, gene, topN, doGradientDescent, scalingTransform, interval, serviceLocal );

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

		/*
		// TODO: hack
		int bestDegree = 289;
		Result result = new Result();
		result.tx[ bestDegree ] = -11.63879058;
		result.ty[ bestDegree ] = -23.01504033;
		*/

		System.out.println( "Best degree = " + bestDegree + ": " + result.tx[ bestDegree ] + ", " + result.ty[ bestDegree ] );

		// assemble a final affine transform that maps B to A in world coordinates

		// we need the offsets (as zero-min is input to the PCM)
		final AffineTransform2D transformB = scalingTransform.copy();
		transformB.rotate( Math.toRadians( bestDegree ) );

		final Interval intervalA = ImgLib2Util.transformInterval( interval, scalingTransform );
		final Interval intervalB = ImgLib2Util.transformInterval( interval, transformB );

		//System.out.println( "Interval A: " + Util.printInterval( intervalA ) );
		//System.out.println( "Interval B: " + Util.printInterval( intervalB ) );

		final double tx = ( intervalA.realMin( 0 ) - intervalB.realMin( 0 ) ) + result.tx[ bestDegree ];
		final double ty = ( intervalA.realMin( 1 ) - intervalB.realMin( 1 ) ) + result.ty[ bestDegree ];

		// assemble the transform
		final AffineTransform2D finalTransform = scalingTransform.copy();

		final AffineTransform2D rotation = new AffineTransform2D();
		rotation.rotate( Math.toRadians( bestDegree ) );
		finalTransform.preConcatenate( rotation );

		final AffineTransform2D trans = new AffineTransform2D();
		trans.translate( tx, ty );
		finalTransform.preConcatenate( trans );

		finalTransform.preConcatenate( scalingTransform.inverse() );

		/*
		// visualize result for Calm1
		final long[] shiftL = new long[ 2 ];
		shiftL[ 0 ] = Math.round( result.tx[ bestDegree ] );
		shiftL[ 1 ] = Math.round( result.ty[ bestDegree ] );

		//shiftL[ 0 ] = Math.round( -11.63879058 );
		//shiftL[ 1 ] = Math.round( -23.01504033 );
		//transformB.rotate( Math.toRadians( 289 ) );

		Pair< RandomAccessibleInterval<FloatType>, RandomAccessibleInterval<FloatType>> res =
				PhaseCorrelation2Util.dummyFuse(
						Views.zeroMin( display( stdataA, statA, "Calm1", ImgLib2Util.transformInterval( interval, scalingTransform ), scalingTransform ) ),
						Views.zeroMin( display( stdataB, statB, "Calm1", ImgLib2Util.transformInterval( interval, transformB ), transformB ) ),
						new Point( shiftL ), serviceGlobal );

		ImageJFunctions.show(res.getA());
		ImageJFunctions.show(res.getB());
		*/

		serviceGlobal.shutdown();

		return finalTransform;
	}

	public static List< Pair< PhaseCorrelationPeak2, Double > > alignGenePairwise(
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

			final PhaseCorrelationPeak2 shiftPeak = testPair( Views.zeroMin( imgA ), Views.zeroMin( imgB ), nHighest, service );
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

					PhaseCorrelationPeak2 shiftPeak = testPair( imgA, Views.zeroMin( display( stdataB, statB, gene, ImgLib2Util.transformInterval( interval, transformB ), transformB ) ), nHighest, service );

					if ( shiftPeak.getCrossCorr() > bestPeak.getA().getCrossCorr() )
					{
						updated = true;
						if ( deg + step >= 360 )
							deg -= 360;
						bestPeak = new ValuePair< PhaseCorrelationPeak2, Double >( shiftPeak, deg + step );
					}

					transformB = scalingTransform.copy();
					transformB.rotate( Math.toRadians( deg - step ) );

					shiftPeak = testPair( imgA, Views.zeroMin( display( stdataB, statB, gene, ImgLib2Util.transformInterval( interval, transformB ), transformB ) ), nHighest, service );

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

	protected static void insertIntoList( final List< Pair< PhaseCorrelationPeak2, Double > > topPeaks, final int topN, final PhaseCorrelationPeak2 newPeak, final double deg )
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

	public static PhaseCorrelationPeak2 testPair( final RandomAccessibleInterval< DoubleType > imgA, final RandomAccessibleInterval< DoubleType > imgB, final int nHighest, final ExecutorService service  )
	{
		RandomAccessibleInterval<DoubleType> pcm = PhaseCorrelation2.calculatePCM(
				imgA, imgB,
				new ArrayImgFactory<DoubleType>( new DoubleType() ),
				new ArrayImgFactory<ComplexDoubleType>( new ComplexDoubleType() ),
				service );

		//List<PhaseCorrelationPeak2> peaks = PhaseCorrelation2Util.getPCMMaxima(pcm, service, 5, false );
		//for ( final PhaseCorrelationPeak2 p : peaks )
		//	System.out.println( "PCM: " + p.getPhaseCorr() + ", " + Util.printCoordinates( p.getPcmLocation() ) );

		return PhaseCorrelation2.getShift(pcm, imgA, imgB, nHighest, 10000, false, false, service);// Threads.createFixedExecutorService( 1 ));
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


	public static void main( String[] args ) throws IOException
	{
		final String path = Path.getPath();

		//final String[] pucks = new String[] { "Puck_180602_20", "Puck_180602_18", "Puck_180602_17", "Puck_180602_16", "Puck_180602_15", "Puck_180531_23", "Puck_180531_22", "Puck_180531_19", "Puck_180531_18", "Puck_180531_17", "Puck_180531_13", "Puck_180528_22", "Puck_180528_20" };
		//final String[] pucks = new String[] { "Puck_180531_23" };
		final String[] pucks = new String[] { "Puck_180531_23", "Puck_180531_22" };
		//final String[] pucks = new String[] { "Puck_180531_18", "Puck_180531_17" };


		final STData stDataA = N5IO.readN5( new File( path + "slide-seq/" + pucks[ 0 ] + "-normalized.n5" ) );
		final STData stDataB = N5IO.readN5( new File( path + "slide-seq/" + pucks[ 1 ] + "-normalized.n5" ) );


		//final List< String > genesToTest = genesToTest( stDataA, stDataB, 50 );
		final List< String > genesToTest = new ArrayList<>();
		genesToTest.add( "Calm1" );
		genesToTest.add( "Hpca" );
		genesToTest.add( "Fth1" );
		genesToTest.add( "Ubb" );
		genesToTest.add( "Pcp4" );


		//final AffineTransform2D pcmTransform = align( stDataA, stDataB, genesToTest, 5, true );

		final AffineTransform2D pcmTransform = new AffineTransform2D();
		pcmTransform.set( 0.32556815445715637, 0.945518575599317, -465.5516232, -0.945518575599317, 0.32556815445715637, 4399.3983868 ); // "Puck_180531_23", "Puck_180531_22"
		System.out.println( "PCM transform: " + pcmTransform );

		final AffineTransform2D icpTransform = alignICP( stDataA, stDataB, genesToTest, pcmTransform, 8.0, 100 );
		System.out.println( "ICP transform: " + icpTransform );



		final Interval interval = STDataUtils.getCommonInterval( stDataA, stDataB );

		// visualize result using the global transform
		final AffineTransform2D tA = new AffineTransform2D();
		tA.scale( 0.1 );

		final AffineTransform2D tB_PCM = pcmTransform.copy();
		tB_PCM.preConcatenate( tA );

		final AffineTransform2D tB_ICP = icpTransform.copy();
		tB_ICP.preConcatenate( tA );

		final Interval finalInterval = Intervals.expand( ImgLib2Util.transformInterval( interval, tA ), 100 );

		new ImageJ();

		ImageJFunctions.show( display( stDataA, new STDataStatistics( stDataA ), "Calm1", finalInterval, tA ) );
		ImageJFunctions.show( display( stDataB, new STDataStatistics( stDataB ), "Calm1", finalInterval, tB_PCM ) ).setTitle( "Calm1-PCM" );
		ImageJFunctions.show( display( stDataB, new STDataStatistics( stDataB ), "Calm1", finalInterval, tB_ICP ) ).setTitle( "Calm1-ICP" );
	}
}