package align;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.joml.Math;

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
import mpicbg.models.Affine2D;
import mpicbg.models.Model;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.RealFloatConverter;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import util.Threads;

public class PairwiseSIFT
{
	public static class SIFTParam
	{
		public SIFTParam()
		{
			this.sift.fdSize = 8;
			this.sift.fdBins = 8;
			this.sift.steps = 10;
			this.rod = 0.90f;
			this.sift.minOctaveSize = 128;
		}

		public SIFTParam( final int fdSize, final int fdBins, final int steps, final float rod, final int minOctaveSize )
		{
			this.sift.fdSize = fdSize;
			this.sift.fdBins = fdBins;
			this.sift.steps = steps;
			this.rod = rod;
			this.sift.minOctaveSize = minOctaveSize;
		}

		final public FloatArray2DSIFT.Param sift = new FloatArray2DSIFT.Param();

		/**
		 * Closest/next closest neighbour distance ratio
		 */
		public float rod;
	}

	static public void matchFeatures(
			final Collection< Feature > fs1,
			final Collection< Feature > fs2,
			final List< PointMatch > matches,
			final float rod )
	{
		for ( final Feature f1 : fs1 )
		{
			Feature best = null;
			double best_d = Double.MAX_VALUE;
			double second_best_d = Double.MAX_VALUE;

			for ( final Feature f2 : fs2 )
			{
				final double d = f1.descriptorDistance( f2 );
				if ( d < best_d )
				{
					second_best_d = best_d;
					best_d = d;
					best = f2;
				}
				else if ( d < second_best_d )
					second_best_d = d;
			}

			if ( best != null && second_best_d < Double.MAX_VALUE && best_d / second_best_d < rod )
				matches.add(
						new PointMatch(
								new Point(
										new double[] { f1.location[ 0 ], f1.location[ 1 ] } ),
								new Point(
										new double[] { best.location[ 0 ], best.location[ 1 ] } ),
								best_d / second_best_d ) );
		}

		// now remove ambiguous matches
		for ( int i = 0; i < matches.size(); )
		{
			boolean amb = false;
			final PointMatch m = matches.get( i );
			final double[] m_p2 = m.getP2().getL();
			for ( int j = i + 1; j < matches.size(); )
			{
				final PointMatch n = matches.get( j );
				final double[] n_p2 = n.getP2().getL();
				if ( m_p2[ 0 ] == n_p2[ 0 ] && m_p2[ 1 ] == n_p2[ 1 ] )
				{
					amb = true;
					matches.remove( j );
				}
				else ++j;
			}
			if ( amb )
				matches.remove( i );
			else ++i;
		}
	}
	public static List< PointMatch > extractCandidates( final ImageProcessor ip1, final ImageProcessor ip2, final String gene, final SIFTParam p )
	{
		final List< Feature > fs1 = new ArrayList<>();
		final List< Feature > fs2 = new ArrayList<>();

		final FloatArray2DSIFT sift = new FloatArray2DSIFT( p.sift );

		final SIFT ijSIFT = new SIFT( sift );
		ijSIFT.extractFeatures( ip1, fs1 );
		ijSIFT.extractFeatures( ip2, fs2 );

		final List< PointMatch > candidates = new ArrayList<>();
		FeatureTransform.matchFeatures( fs1, fs2, candidates, p.rod );

		final List< PointMatch > candidatesST = new ArrayList<>();
		for ( final PointMatch pm : candidates )
			candidatesST.add(
					new PointMatch(
							new PointST( pm.getP1().getL(), gene ),
							new PointST( pm.getP2().getL(), gene ) ));

		return candidatesST;
	}

	public static ArrayList< PointMatch > consensus( final List< PointMatch > candidates, final Model< ? > model, final int minNumInliers, final double maxEpsilon )
	{
		final ArrayList< PointMatch > inliers = new ArrayList<>();

		boolean modelFound;

		try
		{
			modelFound = model.filterRansac(
					candidates,
					inliers,
					10000,
					maxEpsilon,
					0.1f, //p.minInlierRatio,
					minNumInliers,
					3f );
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
			final ArrayList< Point > p1 = new ArrayList<>();
			final ArrayList< Point > p2 = new ArrayList<>();

			PointMatch.sourcePoints( inliers, p1 );
			PointMatch.targetPoints( inliers, p2 );

			imp1.setRoi( Util.pointsToPointRoi( p1 ) );
			imp2.setRoi( Util.pointsToPointRoi( p2 ) );
		}
	}

	public static <M extends Affine2D<M> & Model<M>, N extends Affine2D<N> & Model<N>> SiftMatch pairwiseSIFT(
			final STData stDataA,
			final String stDataAname,
			final STData stDataB,
			final String stDataBname,
			final M modelPairwise,
			final N modelGlobal,
			final List< String > genesToTest,
			final SIFTParam p,
			final double scale,
			final double smoothnessFactor,
			final double maxEpsilon,
			final int minNumInliers,
			final int minNumInliersPerGene,
			final boolean visualizeResult,
			final int numThreads )
	{
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
					//System.out.println( "current gene: " + gene );

					final RandomAccessibleInterval<DoubleType> imgA = AlignTools.display( stDataA, new STDataStatistics( stDataA ), gene, finalInterval, tS, null, smoothnessFactor );
					final RandomAccessibleInterval<DoubleType> imgB = AlignTools.display( stDataB, new STDataStatistics( stDataB ), gene, finalInterval, tS, null, smoothnessFactor );

					final ImagePlus impA = ImageJFunctions.wrapFloat( imgA, new RealFloatConverter<>(), "A_" + gene);
					final ImagePlus impB = ImageJFunctions.wrapFloat( imgB, new RealFloatConverter<>(), "B_" + gene );

					final List< PointMatch > matchesAB = extractCandidates(impA.getProcessor(), impB.getProcessor(), gene, p );
					final List< PointMatch > matchesBA = extractCandidates(impB.getProcessor(), impA.getProcessor(), gene, p );

					//System.out.println( gene + " = " + matchesAB.size() );
					//System.out.println( gene + " = " + matchesBA.size() );

					if ( matchesAB.size() == 0 && matchesBA.size() == 0 )
						continue;

					final List< PointMatch > candidatesTmp = new ArrayList<>();

					if ( matchesBA.size() > matchesAB.size() )
						PointMatch.flip( matchesBA, candidatesTmp );
					else
						candidatesTmp.addAll( matchesAB );

					//final List< PointMatch > inliersTmp = consensus( candidatesTmp, new RigidModel2D(), minNumInliersPerGene, maxEpsilon*scale );
					//System.out.println( "remaining points" );
					
					//for ( final PointMatch pm:inliersTmp )
					//	System.out.println( pm.getWeight() );
					/*
					if ( gene.equals("Ckb"))
					{
						final List< PointMatch > inliersTmp = consensus( candidatesTmp, new RigidModel2D(), minNumInliersPerGene, maxEpsilon*scale );
						if ( inliersTmp.size() > minNumInliersPerGene )
						{
							impA.show();impA.resetDisplayRange();
							impB.show();impB.resetDisplayRange();
							visualizeInliers( impA, impB, inliersTmp );
						}
					}	*/

					// adjust the locations to the global coordinate system
					// and store the gene name it came from
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
					final M model = modelPairwise.copy();//new InterpolatedAffineModel2D<>( new AffineModel2D(), new RigidModel2D(), 0.1 );//new RigidModel2D();
					final List< PointMatch > inliers = consensus( candidatesTmp, model, minNumInliersPerGene, maxEpsilon );

					// reset world coordinates & compute error
					double error = Double.NaN, maxError = Double.NaN, minError = Double.NaN;
					if ( inliers.size() > 0 )
					{
						error = 0;
						minError = Double.MAX_VALUE;
						maxError = -Double.MAX_VALUE;

						for ( final PointMatch pm : inliers )
						{
							final double dist = Point.distance(pm.getP1(), pm.getP2());
							error += dist;
							maxError = Math.max( maxError, dist );
							minError = Math.min( minError, dist );
						}

						error /= (double)inliers.size();
					}

					for ( final PointMatch pm : candidatesTmp )
					{
						final Point p1 = pm.getP1();
						final Point p2 = pm.getP2();

						for ( int d = 0; d < finalInterval.numDimensions(); ++d )
						{
							p1.getW()[ d ] = p1.getL()[ d ];
							p2.getW()[ d ] = p2.getL()[ d ];
						}
					}

					if ( inliers.size() > 0 )
					{
						allPerGeneInliers.addAll( inliers );
						System.out.println( stDataAname + "-" + stDataBname + ": " + inliers.size() + "/" + candidatesTmp.size() + ", " + minError + "/" + error + "/" + maxError + ", " + ((PointST)inliers.get( 0 ).getP1()).getGene() );
						//System.out.println( ki + "-" + kj + ": " + inliers.size() + "/" + candidatesTmp.size() + ", " + ((PointST)inliers.get( 0 ).getP1()).getGene() + ", " );
						//GlobalOpt.visualizePair(stDataA, stDataB, new AffineTransform2D(), GlobalOpt.modelToAffineTransform2D( model ).inverse() ).setTitle( gene +"_" + inliers.size() );;
					}
				}

				return allPerGeneInliers;
			});
		}

		final ExecutorService service = Threads.createFixedExecutorService( numThreads );

		try
		{
			final List< Future< List< PointMatch > > > futures = service.invokeAll( tasks );
			for ( final Future< List< PointMatch > > future : futures )
				allCandidates.addAll( future.get() );
		}
		catch ( final InterruptedException | ExecutionException e )
		{
			e.printStackTrace();
			throw new RuntimeException( e );
		}

		service.shutdown();

		//final InterpolatedAffineModel2D<AffineModel2D, RigidModel2D> model = new InterpolatedAffineModel2D<>( new AffineModel2D(), new RigidModel2D(), 0.1 );//new RigidModel2D();
		//final RigidModel2D model = new RigidModel2D();
		final ArrayList< PointMatch > inliers = consensus( allCandidates, modelGlobal, minNumInliers, maxEpsilon );

		// the model that maps J to I
		System.out.println( stDataAname + "\t" + stDataBname + "\t" + inliers.size() + "\t" + allCandidates.size() + "\t" + AlignTools.modelToAffineTransform2D( modelGlobal ).inverse() );

		if ( visualizeResult && inliers.size() >= minNumInliers )
		{
			ImagePlus rendered = AlignTools.visualizePair(
					stDataA, stDataB,
					new AffineTransform2D(),
					AlignTools.modelToAffineTransform2D( modelGlobal ).inverse(),
					smoothnessFactor );
			rendered.setTitle( stDataAname + "-" + stDataBname + "-inliers-" + inliers.size() + " (" + AlignTools.defaultGene + ")" );
		}

		// compute errors
		// reset world coordinates & compute error
		double error = Double.NaN, maxError = Double.NaN, minError = Double.NaN;
		if ( inliers.size() > 0 )
		{
			error = 0;
			minError = Double.MAX_VALUE;
			maxError = -Double.MAX_VALUE;

			for ( final PointMatch pm : inliers )
			{
				pm.apply( modelGlobal );
				final double dist = Point.distance(pm.getP1(), pm.getP2());
				error += dist;
				maxError = Math.max( maxError, dist );
				minError = Math.min( minError, dist );
			}

			error /= (double)inliers.size();
		}

		System.out.println( "errors: " + minError + "/" + error + "/" + maxError );
		return new SiftMatch(stDataAname, stDataBname, allCandidates.size(), inliers);
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

		//final String[] pucks = new String[] { "Puck_180602_20", "Puck_180602_18", "Puck_180602_17", "Puck_180602_16", "Puck_180602_15", "Puck_180531_23", "Puck_180531_22", "Puck_180531_19", "Puck_180531_18", "Puck_180531_17", "Puck_180531_13", "Puck_180528_22", "Puck_180528_20" };
		//final String[] pucks = new String[] { "Puck_180531_23", "Puck_180531_22" };
		//final String[] pucks = new String[] { "Puck_180602_18", "Puck_180531_18" }; // 1-8

		final File n5File = new File( path + "slide-seq-normalized.n5" );
		final N5FSReader n5 = N5IO.openN5( n5File );
		final List< String > pucks = N5IO.listAllDatasets( n5 );

		final ArrayList< STData > puckData = new ArrayList<>();
		for ( final String puck : pucks )
			puckData.add( N5IO.readN5( n5, puck ) );

		// clear the alignment metadata
		final String matchesGroupName = n5.groupPath( "/", "matches" );
		final N5FSWriter n5Writer = N5IO.openN5write( n5File );
		if (n5.exists(matchesGroupName))
			n5Writer.remove( matchesGroupName );
		n5Writer.createGroup( matchesGroupName );

		// visualize using the global transform
		final double scale = 0.1;
		final double maxEpsilon = 300;
		final int minNumInliers = 12;
		final int minNumInliersPerGene = 10;

		final double smoothnessFactor = 4.0;

		final SIFTParam p = new SIFTParam();
		final boolean saveResult = true;
		final boolean visualizeResult = true;

		// multi-threading
		final int numThreads = Threads.numThreads();

		for ( int i = 0; i < pucks.size() - 1; ++i )
		{
			for ( int j = i + 1; j < pucks.size(); ++j )
			{
				if ( Math.abs( j - i ) > 2 )
					continue;

				//final int ki = i;
				//final int kj = j;

				final STData stDataA = puckData.get(i);
				final STData stDataB = puckData.get(j);

				final String puckA = pucks.get( i );
				final String puckB = pucks.get( j );

				//System.out.println( new Date( System.currentTimeMillis() ) + ": Finding genes" );

				final List< String > genesToTest = Pairwise.genesToTest( stDataA, stDataB, 2000, numThreads );
				//for ( final String gene : genesToTest )
				//	System.out.println( gene );
				/*final List< String > genesToTest = new ArrayList<>();
				genesToTest.add( "Calm1" );
				genesToTest.add( "Calm2" );
				genesToTest.add( "Hpca" );
				genesToTest.add( "Fth1" );
				genesToTest.add( "Ubb" );
				genesToTest.add( "Pcp4" );
				genesToTest.add( "Ckb" ); //mt-Nd1
				// check out ROD!
				*/

//				pairwiseSIFT(stDataA, puckA, stDataB, puckB, new RigidModel2D(), new RigidModel2D(), n5File, genesToTest, p, scale, smoothnessFactor, maxEpsilon,
//						minNumInliers, minNumInliersPerGene, saveResult, visualizeResult, numThreads);
			}
		}
		System.out.println("done.");
	}


}
