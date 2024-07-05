package align;

import java.io.IOException;
import java.rmi.UnexpectedException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import analyze.ExtractGeneLists;
import data.STData;
import data.STDataStatistics;
import data.STDataUtils;
import gui.STDataAssembly;
import gui.bdv.AddedGene.Rendering;
import ij.ImageJ;
import imglib2.ImgLib2Util;
import imglib2.icp.ICP;
import imglib2.icp.PointMatchIdentification;
import imglib2.icp.StDataPointMatchIdentification;
import imglib2.phasecorrelation.PhaseCorrelation2;
import imglib2.phasecorrelation.PhaseCorrelationPeak2;
import io.Path;
import io.SpatialDataContainer;
import mpicbg.models.AffineModel2D;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.complex.ComplexDoubleType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import util.Threads;
import org.apache.logging.log4j.Logger;
import util.LoggerUtil;

public class Pairwise
{
	private static final Logger logger = LoggerUtil.getLogger();
	// how many peaks to test in the PCM
	public static final int nHighest = 5;

	public static class Result
	{
		final Img< DoubleType > histogram = ArrayImgs.doubles( 360 );
		final Img< DoubleType > tx = ArrayImgs.doubles( 360 );
		final Img< DoubleType > ty = ArrayImgs.doubles( 360 );

		public Result copy()
		{
			final Result copy = new Result();

			for ( int i = 0; i < histogram.dimension( 0 ); ++i )
			{
				copy.histogram.getAt( i ).set( this.histogram.getAt( i ) );
				copy.tx.getAt( i ).set( this.tx.getAt( i ) );
				copy.ty.getAt( i ).set( this.ty.getAt( i ) );
			}

			return copy;
		}
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

		//System.out.println( "listA (reference): " + listA.size() );

		/*
		// tmp
		System.out.println( "listB (target) sampling: " + StDataPointMatchIdentification.sampling );
		StDataPointMatchIdentification.sampling = 4.0;//Math.min( stStatsDataA.getMedianDistance(), stStatsDataB.getMedianDistance() ) / 2.0;
		IterableRealInterval< DoubleType > dataB = TransformCoordinates.sample( stdataB.getExprData( genesToUse.get( 0 ) ), StDataPointMatchIdentification.sampling );
		final RealCursor< DoubleType > c = dataB.localizingCursor();

		while ( c.hasNext() )
		{
			c.fwd();
			listB.add( new RealPoint( c ) );
		}
		*/
		for ( final RealLocalizable p : stdataB )
			listB.add( new RealPoint( p ) );

		//System.out.println( "listB (target): " + listB.size() );

		final double[] m = initialModel.getRowPackedCopy(); //a.m00, a.m01, a.m02, a.m10, a.m11, a.m12

		final AffineModel2D model = new AffineModel2D();
		//model.set( m00, m10, m01, m11, m02, m12 );
		model.set( m[ 0 ], m[ 3 ], m[ 1 ], m[ 4 ], m[ 2 ], m[ 5 ] );

		final ExecutorService service = Executors.newFixedThreadPool( Threads.numThreads() );

		final PointMatchIdentification< RealPoint > pmi = new StDataPointMatchIdentification<>( stdataB, stdataA, genesToUse, maxDistance, null, null, null, null, service );
		final ICP< RealPoint > icp = new ICP<>( listB, listA, pmi, Double.NaN );

		service.shutdown();

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
				//System.out.println( "Failed with e: " + e );
				e.printStackTrace();
				return null;
			}

			if ( lastNumCorresponding == icp.getNumPointMatches() && lastAvgError == icp.getAverageError() )
				converged = true;

			lastNumCorresponding = icp.getNumPointMatches();
			lastAvgError = icp.getAverageError();
			
			//System.out.println( i + ": " + icp.getNumPointMatches() + " matches, avg error [px] " + icp.getAverageError() + ", max error [px] " + icp.getMaximalError() );
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

	public static List< Pair< String, Double > > allGenes( final STData stdataA, final STData stdataB, final int numThreads )
	{
		logger.info( "Sorting all genes of both datasets by stdev (this takes a bit) ... ");
		long time = System.currentTimeMillis();

		// from big to small
		final ArrayList< Pair< String, Double > > listA = ExtractGeneLists.sortByStDevIntensity( stdataA, numThreads );
		final ArrayList< Pair< String, Double > > listB = ExtractGeneLists.sortByStDevIntensity( stdataB, numThreads );

		logger.info( "Took " + (System.currentTimeMillis() - time) + " ms." );

		// now we want to find the combination of genes where both have high variance
		// we therefore sort them by the sum of ranks of both lists

		final HashMap<String, Integer > geneToIndexB = new HashMap<>();

		for ( int i = 0; i < listB.size(); ++i )
			geneToIndexB.put( listB.get( i ).getA(), i );

		final ArrayList< Pair< Integer, Integer > > entries = new ArrayList<>();

		for ( int i = 0; i < listA.size(); ++i )
			if ( geneToIndexB.containsKey( listA.get( i ).getA() ) )
				entries.add( new ValuePair<>( i, geneToIndexB.get( listA.get( i ).getA() ) ) );

		Collections.sort( entries, (o1,o2) -> (o1.getA()+o1.getB()) - (o2.getA()+o2.getB()) ); 

		//for ( int i = 0; i < 10; ++i )
		//	System.out.println( entries.get( i ).getA() + " (" + listA.get( entries.get( i ).getA() ).getA() +"), " + entries.get( i ).getB() + " ("  + listB.get( entries.get( i ).getB() ).getA() + ")" );
		//System.out.println( entries.get( entries.size() - 1 ).getA() + ", " + entries.get( entries.size() - 1 ).getB() );

		final ArrayList< Pair< String, Double > > toTest = new ArrayList<>();

		for ( int i = 0; i < entries.size(); ++i )
		{
			final String geneName = listA.get( entries.get( i ).getA() ).getA(); // gene names are identical in the matched lists
			if ( !listB.get( entries.get( i ).getB() ).getA().equals( geneName ) )
				throw new RuntimeException( "gene names do not match, that is a bug." );

			final double stDev1 = listA.get( entries.get( i ).getA() ).getB();
			final double stDev2 = listB.get( entries.get( i ).getB() ).getB();

			toTest.add( new ValuePair<String, Double>( geneName, (stDev1 + stDev2) / 2.0 ) ); // gene names are identical in the matched lists
		}

		return toTest;
	}

	public static ArrayList<Pair<String, Double>> getGenesEntropy( final STData stData, final String geneLabel ) throws UnexpectedException {
		Map< String, RandomAccessibleInterval< ? extends NativeType< ? > > > geneAnnotation = stData.getGeneAnnotations();
		if (!geneAnnotation.containsKey(geneLabel)) {
			throw new UnexpectedException("The property '" + geneLabel + "' was not found as gene annotation");
		}

		final RandomAccessibleInterval entropy = geneAnnotation.get(geneLabel);
		List<String> geneNames = stData.getGeneNames();

		ArrayList<Pair<String, Double>> list = new ArrayList<>();
		final IterableInterval< DoubleType > entropyValue = Views.flatIterable(entropy);
		final double[] entropyValueCopy = new double[ (int)entropyValue.size() ];
		final Cursor< DoubleType > cursor = entropyValue.localizingCursor();

		while ( cursor.hasNext() )
		{
			final DoubleType t = cursor.next();
			entropyValueCopy[ cursor.getIntPosition( 0 ) ] = t.get();
		}

		for (int i = 0; i < geneNames.size(); i++) {
			list.add(new ValuePair<>(geneNames.get(i), entropyValueCopy[i]));
		}
		return list;
	}

	public static List< String > genesToTest( final STData stdataA, final STData stdataB, final String geneLabel, final int numGenes ) throws UnexpectedException
	{
		if ( numGenes <= 0 )
			return new ArrayList<>();

		// this assumes that the "stdev" or similar has been computed
		final ArrayList<Pair<String, Double>> listA = getGenesEntropy( stdataA, geneLabel );
		final ArrayList<Pair<String, Double>> listB = getGenesEntropy( stdataB, geneLabel );
			
		// now we want to find the combination of genes where both have high variance
		// we therefore sort them by the sum of ranks of both lists
		Collections.sort( listA, (o1, o2) -> o2.getB().compareTo( o1.getB() ));
		Collections.sort( listB, (o1, o2) -> o2.getB().compareTo( o1.getB() ));

		final HashMap<String, Integer > geneToIndexB = new HashMap<>();

		for ( int i = 0; i < listB.size(); ++i )
			geneToIndexB.put( listB.get( i ).getA(), i );

		final ArrayList< Pair< Integer, Integer > > entries = new ArrayList<>();

		for ( int i = 0; i < listA.size(); ++i )
			if ( geneToIndexB.containsKey( listA.get( i ).getA() ) )
				entries.add( new ValuePair<>( i, geneToIndexB.get( listA.get( i ).getA() ) ) );

		Collections.sort( entries, (o1,o2) -> (o1.getA()+o1.getB()) - (o2.getA()+o2.getB()) ); 

		final ArrayList< String > toTest = new ArrayList<>();

		for ( int i = 0; i < Math.min( numGenes, entries.size() ); ++i )
			toTest.add( listA.get( entries.get( i ).getA() ).getA() ); // gene names are identical in the matched lists

		return toTest;
	}

	public static Pair< AffineTransform2D, Double > align(
			final STData stdataA,
			final STData stdataB,
			final List< String > genesToTest,
			final double scaling,
			final int degreeSteps,
			final int topN,
			final boolean doGradientDescent  )
	{
		final STDataStatistics statA = new STDataStatistics( stdataA );
		final STDataStatistics statB = new STDataStatistics( stdataB );

		final AffineTransform2D scalingTransform = new AffineTransform2D();
		scalingTransform.scale( scaling );

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
							alignGenePairwise( stdataA, statA, stdataB, statB, gene, degreeSteps, topN, doGradientDescent, scalingTransform, interval, serviceLocal );

					for ( final Pair< PhaseCorrelationPeak2, Double > alignParams : alignParamList )
					{
						final int deg = (int)Math.round( alignParams.getB() ) % 360; // e.g. 359.6 should be 0
						final double weight = alignParams.getA().getCrossCorr();

						synchronized ( result )
						{
							result.histogram.getAt( deg ).add( new DoubleType( weight ) );
							result.tx.getAt( deg ).add( new DoubleType( alignParams.getA().getShift().getDoublePosition( 0 ) * weight ) );
							result.ty.getAt( deg ).add( new DoubleType( alignParams.getA().getShift().getDoublePosition( 1 ) * weight ) );
						}

						//System.out.println( "TOP ("+gene+"): " + alignParams.getB() + ", " + alignParams.getA().getCrossCorr() + ", " + Util.printCoordinates( alignParams.getA().getShift() ) );
					}

					//System.out.println();

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

		Gauss3.gauss( 2, Views.extendPeriodic( result.histogram ), result.histogram );
		Gauss3.gauss( 2, Views.extendPeriodic( result.tx ), result.tx );
		Gauss3.gauss( 2, Views.extendPeriodic( result.ty ), result.ty );

		/*
		// TODO: hack
		int bestDegree = 289;
		Result result = new Result();
		result.tx[ bestDegree ] = -11.63879058;
		result.ty[ bestDegree ] = -23.01504033;
		*/

		int bestDegreeGauss = -1;
		double degreeWeightGauss = 0;

		for ( int i = 0; i < result.histogram.dimension( 0 ); ++i )
		{
			final double weight = result.histogram.getAt( i ).get();
	
			if ( weight > degreeWeightGauss )
			{
				degreeWeightGauss = weight;
				bestDegreeGauss = i;
			}

			final DoubleType tx = result.tx.getAt( i );
			final DoubleType ty = result.ty.getAt( i );

			tx.set( weight > 0 ? tx.get() / weight : 0.0 );
			ty.set( weight > 0 ? ty.get() / weight : 0.0 );

			//System.out.println( i + "\t" + weight + "\t" + tx + "\t" + ty );
		}

		//System.out.println( "Best degree gauss = " + bestDegreeGauss + ": " + result.tx.getAt( bestDegreeGauss ).get() + ", " + result.ty.getAt( bestDegreeGauss ).get() );



		// assemble a final affine transform that maps B to A in world coordinates

		// we need the offsets (as zero-min is input to the PCM)
		final AffineTransform2D transformB = scalingTransform.copy();
		transformB.rotate( Math.toRadians( bestDegreeGauss ) );

		final Interval intervalA = ImgLib2Util.transformInterval( interval, scalingTransform );
		final Interval intervalB = ImgLib2Util.transformInterval( interval, transformB );

		//System.out.println( "Interval A: " + Util.printInterval( intervalA ) );
		//System.out.println( "Interval B: " + Util.printInterval( intervalB ) );

		final double tx = ( intervalA.realMin( 0 ) - intervalB.realMin( 0 ) ) + result.tx.getAt( bestDegreeGauss ).get();
		final double ty = ( intervalA.realMin( 1 ) - intervalB.realMin( 1 ) ) + result.ty.getAt( bestDegreeGauss ).get();

		// assemble the transform
		final AffineTransform2D finalTransform = scalingTransform.copy();

		final AffineTransform2D rotation = new AffineTransform2D();
		rotation.rotate( Math.toRadians( bestDegreeGauss ) );
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

		return new ValuePair<AffineTransform2D, Double>( finalTransform, degreeWeightGauss );
	}

	public static List< Pair< PhaseCorrelationPeak2, Double > > alignGenePairwise(
			final STData stdataA, final STDataStatistics statA,
			final STData stdataB, final STDataStatistics statB,
			final String gene,
			final int degreeSteps,
			final int topN,
			final boolean doGradientDescent,
			final AffineTransform2D scalingTransform,
			final Interval interval,
			final ExecutorService service )
	{
		final ArrayList< Pair< PhaseCorrelationPeak2, Double > > topPeaks = new ArrayList<>();

		final RandomAccessibleInterval< DoubleType > imgA = ImgLib2Util.copyImg(
				AlignTools.display( stdataA, statA, gene, ImgLib2Util.transformInterval( interval, scalingTransform ), scalingTransform, null, Rendering.Gauss, AlignTools.defaultSmoothnessFactor ), new ArrayImgFactory<>( new DoubleType() ), service );

		// initial scouting
		//System.out.println( "Scouting: " + gene );

		for ( int deg = 0; deg < 360; deg += degreeSteps )
		{
			final AffineTransform2D transformB = scalingTransform.copy();
			transformB.rotate( Math.toRadians( deg ) );

			final RandomAccessibleInterval< DoubleType > imgB = ImgLib2Util.copyImg(
					AlignTools.display( stdataB, statB, gene, ImgLib2Util.transformInterval( interval, transformB ), transformB, null, Rendering.Gauss, AlignTools.defaultSmoothnessFactor ), new ArrayImgFactory<>( new DoubleType() ), service );

			final PhaseCorrelationPeak2 shiftPeak = testPair( Views.zeroMin( imgA ), Views.zeroMin( imgB ), nHighest, service );
			insertIntoList( topPeaks, topN, shiftPeak, deg );
			//System.out.println( deg + ": " + shiftPeak.getCrossCorr() + ", " + Util.printCoordinates( shiftPeak.getShift() ) );
		}

		if ( !doGradientDescent )
			return topPeaks;

		// gradient descent for every inital Peak
		//System.out.println( "Gradient descent: " + gene );

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

					PhaseCorrelationPeak2 shiftPeak = testPair( imgA, Views.zeroMin(
							AlignTools.display( stdataB, statB, gene, ImgLib2Util.transformInterval( interval, transformB ), transformB, null, Rendering.Gauss, AlignTools.defaultSmoothnessFactor ) ), nHighest, service );

					if ( shiftPeak.getCrossCorr() > bestPeak.getA().getCrossCorr() )
					{
						updated = true;
						if ( deg + step >= 360 )
							deg -= 360;
						bestPeak = new ValuePair< PhaseCorrelationPeak2, Double >( shiftPeak, deg + step );
					}

					transformB = scalingTransform.copy();
					transformB.rotate( Math.toRadians( deg - step ) );

					shiftPeak = testPair( imgA, Views.zeroMin(
							AlignTools.display( stdataB, statB, gene, ImgLib2Util.transformInterval( interval, transformB ), transformB, null, Rendering.Gauss, AlignTools.defaultSmoothnessFactor ) ), nHighest, service );

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
				topPeaks.add( new ValuePair< PhaseCorrelationPeak2, Double >( newPeak, deg ) );
		}
	}

	public static PhaseCorrelationPeak2 testPair( final RandomAccessibleInterval< DoubleType > imgA, final RandomAccessibleInterval< DoubleType > imgB, final int nHighest, final ExecutorService service  )
	{
		RandomAccessibleInterval<DoubleType> pcm = PhaseCorrelation2.calculatePCM(
				imgA, imgB,
				new ArrayImgFactory<DoubleType>( new DoubleType() ),
				new ArrayImgFactory<ComplexDoubleType>( new ComplexDoubleType() ),
				service );

		//ImageJFunctions.show( pcm );
		//List<PhaseCorrelationPeak2> peaks = PhaseCorrelation2Util.getPCMMaxima(pcm, service, 5, false );
		//for ( final PhaseCorrelationPeak2 p : peaks )
		//	System.out.println( "PCM: " + p.getPhaseCorr() + ", " + Util.printCoordinates( p.getPcmLocation() ) );

		return PhaseCorrelation2.getShift(pcm, imgA, imgB, nHighest, 1000, false, false, service);// Threads.createFixedExecutorService( 1 ));
	}

	public static void main( String[] args ) throws IOException
	{
		final String path = Path.getPath();

		//final String[] pucks = new String[] { "Puck_180602_20", "Puck_180602_18", "Puck_180602_17", "Puck_180602_16", "Puck_180602_15", "Puck_180531_23", "Puck_180531_22", "Puck_180531_19", "Puck_180531_18", "Puck_180531_17", "Puck_180531_13", "Puck_180528_22", "Puck_180528_20" };
		//final String[] pucks = new String[] { "Puck_180531_23" };
		//final String[] pucks = new String[] { "Puck_180531_23", "Puck_180531_22" };
		//final String[] pucks = new String[] { "Puck_180531_18", "Puck_180531_17" };
		//final String[] pucks = new String[] { "Puck_180602_20", "Puck_180602_18" };

		final ExecutorService service = Executors.newFixedThreadPool(8);
		final SpatialDataContainer container = SpatialDataContainer.openExisting(path + "slide-seq-normalized-gzip3.n5", service);
		final List<String> pucks = container.getDatasets();

		final List<STDataAssembly> puckData = container.openAllDatasets().stream()
				.map(sdio -> {
					try {return sdio.readData();} catch (IOException e) {throw new RuntimeException(e);}
				}).collect(Collectors.toList());

		for ( int i = 0; i < pucks.size() - 1; ++i )
		{
			for ( int j = i + 1; j < pucks.size(); ++j )
			{
				final STData stDataA = puckData.get(i).data();
				final STData stDataB = puckData.get(j).data();
		
				logger.info( new Date( System.currentTimeMillis() ) + ": Finding genes" );

				final List< String > genesToTest = genesToTest( stDataA, stDataB, "stdev", 50 );
		
				/*
				final List< String > genesToTest = new ArrayList<>();
				genesToTest.add( "Calm1" );
				genesToTest.add( "Calm2" );
				genesToTest.add( "Hpca" );
				genesToTest.add( "Fth1" );
				genesToTest.add( "Ubb" );
				genesToTest.add( "Pcp4" );
				*/

				logger.info( new Date( System.currentTimeMillis() ) + ": Running correlation" );
				final Pair< AffineTransform2D, Double > result = align( stDataA, stDataB, genesToTest, 0.025, 2, 5, true );
				final AffineTransform2D pcmTransform = result.getA();
		
				//System.out.println( i + "\t" + j + "\t" + Math.abs( i - j ) + "\t" + genesToTest.size() + "\t" + result.getB() + "\t" + pcmTransform );

		
				//final AffineTransform2D pcmTransform = new AffineTransform2D();
				//pcmTransform.set( 0.32556815445715637, 0.945518575599317, -465.5516232, -0.945518575599317, 0.32556815445715637, 4399.3983868 ); // "Puck_180531_23", "Puck_180531_22"
				//pcmTransform.set( 0.24192189559966745, 0.9702957262759967, -199.37562080565206, -0.9702957262759967, 0.24192189559966745, 4602.7163253270855 );
				//System.out.println( "PCM transform: " + pcmTransform );

				logger.info( new Date( System.currentTimeMillis() ) + ": Running ICP" );

				final AffineTransform2D icpTransform = alignICP( stDataA, stDataB, genesToTest, pcmTransform, 20, 50 );
				//System.out.println( "ICP transform: " + icpTransform );
		
				logger.info( i + "\t" + j + "\t" + Math.abs( i - j ) + "\t" + genesToTest.size() + "\t" + result.getB() + "\t" + pcmTransform + "\t" + icpTransform );

				if ( pucks.size() != 2 )
					continue;

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
		
				ImageJFunctions.show( AlignTools.display( stDataA, new STDataStatistics( stDataA ), "Calm1", finalInterval, tA, null, Rendering.Gauss, AlignTools.defaultSmoothnessFactor ) );
				ImageJFunctions.show( AlignTools.display( stDataB, new STDataStatistics( stDataB ), "Calm1", finalInterval, tB_PCM, null, Rendering.Gauss, AlignTools.defaultSmoothnessFactor ) ).setTitle( "Calm1-PCM" );
				ImageJFunctions.show( AlignTools.display( stDataB, new STDataStatistics( stDataB ), "Calm1", finalInterval, tB_ICP, null, Rendering.Gauss, AlignTools.defaultSmoothnessFactor ) ).setTitle( "Calm1-ICP" );
			}
		}
		service.shutdown();
	}
}