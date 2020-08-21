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

import data.STData;
import data.STDataStatistics;
import data.STDataUtils;
import ij.ImageJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import imglib2.ImgLib2Util;
import io.N5IO;
import io.Path;
import mpicbg.ij.FeatureTransform;
import mpicbg.ij.SIFT;
import mpicbg.ij.util.Util;
import mpicbg.imagefeatures.Feature;
import mpicbg.imagefeatures.FloatArray2DSIFT;
import mpicbg.models.AbstractModel;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.RigidModel2D;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.RealFloatConverter;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import util.Threads;

public class PairwiseSIFT
{
	static private class Param
	{
		public Param()
		{
			this.sift.fdSize = 8;
			this.sift.fdBins = 8;
			this.sift.steps = 10;
			this.rod = 0.92f;
			this.sift.minOctaveSize = 128;
		}

		final public FloatArray2DSIFT.Param sift = new FloatArray2DSIFT.Param();

		/**
		 * Closest/next closest neighbour distance ratio
		 */
		public float rod = 0.92f;

	}

	final static private Param p = new Param();

	public static List< PointMatch > extractCandidates( final ImageProcessor ip1, final ImageProcessor ip2 )
	{
		final List< Feature > fs1 = new ArrayList< Feature >();
		final List< Feature > fs2 = new ArrayList< Feature >();

		final FloatArray2DSIFT sift = new FloatArray2DSIFT( p.sift );

		final SIFT ijSIFT = new SIFT( sift );
		ijSIFT.extractFeatures( ip1, fs1 );
		ijSIFT.extractFeatures( ip2, fs2 );

		final List< PointMatch > candidates = new ArrayList< PointMatch >();
		FeatureTransform.matchFeatures( fs1, fs2, candidates, p.rod );

		return candidates;
	}

	public static List< PointMatch > consensus( final List< PointMatch > candidates, final AbstractModel< ? > model, final int minNumInliers, final double maxEpsilon )
	{
		final List< PointMatch > inliers = new ArrayList< PointMatch >();

		boolean modelFound;

		try
		{
			modelFound = model.filterRansac(
					candidates,
					inliers,
					10000,
					maxEpsilon,
					0.00f, //p.minInlierRatio,
					minNumInliers );
		}
		catch ( final NotEnoughDataPointsException e )
		{
			modelFound = false;
		}

		if ( modelFound )
			PointMatch.apply( inliers, model );
		else
			inliers.clear();

		return inliers;
	}

	public static void visualizeInliers( final ImagePlus imp1, final ImagePlus imp2, final List< PointMatch > inliers )
	{
		if ( inliers.size() > 0 )
		{
			final ArrayList< Point > p1 = new ArrayList< Point >();
			final ArrayList< Point > p2 = new ArrayList< Point >();

			PointMatch.sourcePoints( inliers, p1 );
			PointMatch.targetPoints( inliers, p2 );

			imp1.setRoi( Util.pointsToPointRoi( p1 ) );
			imp2.setRoi( Util.pointsToPointRoi( p2 ) );
		}
	}

	public static void main( String[] args ) throws IOException
	{
		final String path = Path.getPath();

		new ImageJ();
		/*
		ImagePlus imp = new ImagePlus( new File( path + "slide-seq/stack_small_.tif" ).getAbsolutePath() );

		for ( int i = 0; i < imp.getStackSize() - 1; ++i )
			for ( int j = i + 1; j < imp.getStackSize(); ++j )
			{
				final ImagePlus imp1 = new ImagePlus( "0", imp.getImageStack().getProcessor( i+1 ) );
				final ImagePlus imp2 = new ImagePlus( "4", imp.getImageStack().getProcessor( j+1 ) );

				//imp1.show();
				//imp2.show();

				System.out.println( i + "<>" + j + " =>   " + Math.max( compare( imp1, imp2 ), compare( imp2, imp1 ) ) );
			}
		*/

		final String[] pucks = new String[] { "Puck_180602_20", "Puck_180602_18", "Puck_180602_17", "Puck_180602_16", "Puck_180602_15", "Puck_180531_23", "Puck_180531_22", "Puck_180531_19", "Puck_180531_18", "Puck_180531_17", "Puck_180531_13", "Puck_180528_22", "Puck_180528_20" };
		//final String[] pucks = new String[] { "Puck_180531_23", "Puck_180531_22" };
		//final String[] pucks = new String[] { "Puck_180602_18", "Puck_180531_18" }; // 1-8

		final ArrayList< STData > puckData = new ArrayList<STData>();
		for ( final String puck : pucks )
			puckData.add( N5IO.readN5( new File( path + "slide-seq/" + puck + "-normalized.n5" ) )/*.copy()*/ );

		// visualize using the global transform
		final double scale = 0.05;
		final double maxEpsilon = 300;
		final int minNumInliers = 50;

		// multi-threading
		final int numThreads = Threads.numThreads();
		final ExecutorService serviceGlobal = Threads.createFixedExecutorService( numThreads );

		for ( int i = 0; i < pucks.length - 1; ++i )
		{
			for ( int j = i + 1; j < pucks.length; ++j )
			{
				final STData stDataA = puckData.get(i);
				final STData stDataB = puckData.get(j);

				//System.out.println( new Date( System.currentTimeMillis() ) + ": Finding genes" );

				final List< String > genesToTest = Pairwise.genesToTest( stDataA, stDataB, 50 );
				/*final List< String > genesToTest = new ArrayList<>();
				genesToTest.add( "Calm1" );
				genesToTest.add( "Calm2" );
				genesToTest.add( "Hpca" );
				genesToTest.add( "Fth1" );
				genesToTest.add( "Ubb" );
				genesToTest.add( "Pcp4" );*/

				final AffineTransform2D tS = new AffineTransform2D();
				tS.scale( scale );

				final Interval interval = STDataUtils.getCommonInterval( stDataA, stDataB );
				final Interval finalInterval = Intervals.expand( ImgLib2Util.transformInterval( interval, tS ), 100 );

				final List< PointMatch > allCandidates = new ArrayList<>();

				final List< Callable< List< PointMatch > > > tasks = new ArrayList<>();
				final AtomicInteger nextGene = new AtomicInteger();

				for ( int threadNum = 0; threadNum < numThreads; ++threadNum )
				{
					tasks.add( () ->
					{
						final List< PointMatch > allPerGeneInliers = new ArrayList<>();

						for ( int g = nextGene.getAndIncrement(); g < genesToTest.size(); g = nextGene.getAndIncrement() )
						{
							final String gene = genesToTest.get( g );

							final RandomAccessibleInterval<DoubleType> imgA = Pairwise.display( stDataA, new STDataStatistics( stDataA ), gene, finalInterval, tS );
							final RandomAccessibleInterval<DoubleType> imgB = Pairwise.display( stDataB, new STDataStatistics( stDataB ), gene, finalInterval, tS );

							final ImagePlus impA = ImageJFunctions.wrapFloat( imgA, new RealFloatConverter<>(), "A_" + gene);
							final ImagePlus impB = ImageJFunctions.wrapFloat( imgB, new RealFloatConverter<>(), "B_" + gene );

							final List< PointMatch > matchesAB = extractCandidates(impA.getProcessor(), impB.getProcessor() );
							final List< PointMatch > matchesBA = extractCandidates(impB.getProcessor(), impA.getProcessor() );

							//System.out.println( gene + " = " + matchesAB.size() );
							//System.out.println( gene + " = " + matchesBA.size() );

							if ( matchesAB.size() == 0 && matchesBA.size() == 0 )
								continue;

							final List< PointMatch > candidatesTmp = new ArrayList<>();

							if ( matchesBA.size() > matchesAB.size() )
								PointMatch.flip( matchesBA, candidatesTmp );
							else
								candidatesTmp.addAll( matchesAB );

							// adjust the locations to the global coordinate system
							for ( final PointMatch pm : candidatesTmp )
							{
								final Point p1 = pm.getP1();
								final Point p2 = pm.getP2();

								for ( int d = 0; d < finalInterval.numDimensions(); ++d )
								{
									p1.getL()[ d ] = p1.getW()[ d ] = ( p1.getL()[ d ] + finalInterval.min( d ) ) / scale;
									p2.getL()[ d ] = p2.getW()[ d ] = ( p2.getL()[ d ] + finalInterval.min( d ) ) / scale;
								}
							}

							// prefilter the candidates
							final List< PointMatch > inliers = consensus( candidatesTmp, new RigidModel2D(), 7, 500 );

							// reset world coordinates
							for ( final PointMatch pm : candidatesTmp )
							{
								final Point p1 = pm.getP1();
								final Point p2 = pm.getP2();

								for ( int d = 0; d < finalInterval.numDimensions(); ++d )
								{
									p1.getW()[ d ] = p1.getW()[ d ];
									p2.getW()[ d ] = p2.getW()[ d ];
								}
							}

							if ( inliers.size() > 0 )
								allPerGeneInliers.addAll( inliers );
						}

						return allPerGeneInliers;
					});
				}

				try
				{
					final List< Future< List< PointMatch > > > futures = serviceGlobal.invokeAll( tasks );
					for ( final Future< List< PointMatch > > future : futures )
						allCandidates.addAll( future.get() );
				}
				catch ( final InterruptedException | ExecutionException e )
				{
					e.printStackTrace();
					throw new RuntimeException( e );
				}

				final RigidModel2D model = new RigidModel2D();
				final List< PointMatch > inliers = consensus( allCandidates, model, minNumInliers, maxEpsilon );

				// the model that maps J to I
				System.out.println( i + "\t" + j + "\t" + inliers.size() + "\t" + allCandidates.size() + "\t" + GlobalOpt.modelToAffineTransform2D( model ).inverse() );

				//GlobalOpt.visualizePair(stDataA, stDataB, new AffineTransform2D(), GlobalOpt.modelToAffineTransform2D( model ).inverse() );

				//SimpleMultiThreading.threadHaltUnClean();
				//impA.show();
				//impB.show();
				//visualizeInliers( impA, impB, inliers );
			}
		}
	}
}
