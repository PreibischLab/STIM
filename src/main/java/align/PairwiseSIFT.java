package align;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import analyze.Entropy;
import org.joml.Math;

import align.SIFTParam.SIFTPreset;
import data.STData;
import data.STDataStatistics;
import data.STDataUtils;
import gui.STDataAssembly;
import gui.bdv.AddedGene;
import gui.bdv.AddedGene.Rendering;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.PointRoi;
import ij.process.ImageProcessor;
import imglib2.ImgLib2Util;
import io.Path;
import io.SpatialDataContainer;
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
import org.apache.logging.log4j.Logger;
import util.LoggerUtil;

public class PairwiseSIFT
{
	private static final Logger logger = LoggerUtil.getLogger();

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

	public static ArrayList< PointMatch > consensus(
			final List< PointMatch > candidates,
			final Model< ? > model,
			final int minNumInliers,
			final int iterations,
			final double minInlierRatio,
			final double maxEpsilon )
	{
		final ArrayList< PointMatch > inliers = new ArrayList<>();

		boolean modelFound;

		try
		{
			modelFound = model.filterRansac(
					candidates,
					inliers,
					iterations,
					maxEpsilon,
					minInlierRatio, //0.1f
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
		if (!inliers.isEmpty())
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
			final AffineTransform2D transformA,
			final String stDataAname,
			final STData stDataB,
			final AffineTransform2D transformB,
			final String stDataBname,
			final M modelPairwise,
			final N modelGlobal,
			final List< String > genesToTest,
			final SIFTParam p,
			final boolean visualizeResult,
			final int numThreads,
			final Consumer<Double> progressBar )
	{
		final ExecutorService service = Threads.createFixedExecutorService( numThreads );

		final SiftMatch s = pairwiseSIFT(
				stDataA, transformA, stDataAname, stDataB, transformB, stDataBname,
				modelPairwise, modelGlobal, genesToTest, p,
				visualizeResult,service, new ArrayList<>(), progressBar );

		service.shutdown();

		return s;
	}

	public static Interval intervalForAlignment(
			final STData stDataA,
			final AffineTransform2D tA,
			final STData stDataB,
			final AffineTransform2D tB )
	{
		final Interval iA = ImgLib2Util.transformInterval(stDataA.getRenderInterval(), tA);
		final Interval iB = ImgLib2Util.transformInterval(stDataB.getRenderInterval(), tB);

		final Interval interval = STDataUtils.getCommonIterableInterval( new ArrayList<>( Arrays.asList( iA, iB )) );
		final Interval finalInterval = Intervals.expand( interval, 100 );

		return finalInterval;
	}

	public static SiftMatch pairwiseSIFT(
			final STData stDataA,
			final AffineTransform2D transformA,
			final String stDataAname,
			final STData stDataB,
			final AffineTransform2D transformB,
			final String stDataBname,
			final Model<?> modelPairwise,
			final Model<?> modelGlobal,
			final List< String > genesToTest,
			final SIFTParam p,
			final boolean visualizeResult,
			final ExecutorService service,
			final List< Thread > threads,
			final Consumer< Double > progressBar )
	{
		final AffineTransform2D tScale = new AffineTransform2D();
		tScale.scale( p.scale );

		final AffineTransform2D tA = transformA.copy().preConcatenate( tScale );
		final AffineTransform2D tB = transformB.copy().preConcatenate( tScale );

		final Interval finalInterval = intervalForAlignment( stDataA, tA, stDataB, tB );
		final List< PointMatch > allCandidates = new ArrayList<>();
		final List< Callable< List< PointMatch > > > tasks = new ArrayList<>();
		final double progressPerGene = 90.0 / genesToTest.size();

		//new ImageJ();

		for ( int nextGene = 0; nextGene < genesToTest.size(); ++nextGene )
		{
			final int g = nextGene;

			tasks.add( () ->
			{
				synchronized ( threads ) { threads.add( Thread.currentThread() ); }
				
				progressBar.accept( progressPerGene / 10.0 );
				final List< PointMatch > allPerGeneInliers = new ArrayList<>();

				final String gene = genesToTest.get( g );
				//System.out.println( "current gene: " + gene );

				final double[] minmax = AddedGene.minmax( stDataA.getExprData( gene ) );
				final double minDisplay = AddedGene.getDisplayMin( minmax[ 0 ], minmax[ 1 ], p.brightnessMin );
				final double maxDisplay = AddedGene.getDisplayMax( minmax[ 1 ], p.brightnessMax );

				final RandomAccessibleInterval<DoubleType> imgA =
						AlignTools.display( stDataA, new STDataStatistics( stDataA ), gene, finalInterval, tA, p.filterFactories, p.rendering, p.renderingSmoothness );
				final RandomAccessibleInterval<DoubleType> imgB =
						AlignTools.display( stDataB, new STDataStatistics( stDataB ), gene, finalInterval, tB, p.filterFactories, p.rendering, p.renderingSmoothness );

				final ImagePlus impA = ImageJFunctions.wrapFloat( imgA, new RealFloatConverter<>(), "A_" + gene);
				final ImagePlus impB = ImageJFunctions.wrapFloat( imgB, new RealFloatConverter<>(), "B_" + gene );

				// this massively adjusts the amount of features, but min/max seems the right choice?
				//impA.resetDisplayRange();
				//impB.resetDisplayRange();
				impA.setDisplayRange(minDisplay, maxDisplay);
				impB.setDisplayRange(minDisplay, maxDisplay);

				//impA.show();
				//impB.show();
				//SimpleMultiThreading.threadHaltUnClean();

				progressBar.accept( progressPerGene / 4.0 );

				final List< PointMatch > matchesAB = extractCandidates(impA.getProcessor(), impB.getProcessor(), gene, p );
				final List< PointMatch > candidatesTmp = new ArrayList<>();

				if ( p.biDirectional )
				{
					final List< PointMatch > matchesBA = extractCandidates(impB.getProcessor(), impA.getProcessor(), gene, p );

					//System.out.println( gene + " = " + matchesAB.size() );
					//System.out.println( gene + " = " + matchesBA.size() );

					if (matchesAB.isEmpty() && matchesBA.isEmpty())
						return allPerGeneInliers;
	
					if ( matchesBA.size() > matchesAB.size() )
						PointMatch.flip( matchesBA, candidatesTmp );
					else
						candidatesTmp.addAll( matchesAB );
				}
				else
				{
					if (matchesAB.isEmpty())
						return allPerGeneInliers;

					candidatesTmp.addAll( matchesAB );
				}

				progressBar.accept( progressPerGene / 2.0 );

				// adjust the locations to the global coordinate system
				// and store the gene name it came from
				for ( final PointMatch pm : candidatesTmp )
				{
					final Point p1 = pm.getP1();
					final Point p2 = pm.getP2();

					for ( int d = 0; d < finalInterval.numDimensions(); ++d )
					{
						p1.getL()[ d ] = p1.getW()[ d ] = ( p1.getL()[ d ] + finalInterval.min( d ) ) / p.scale;
						p2.getL()[ d ] = p2.getW()[ d ] = ( p2.getL()[ d ] + finalInterval.min( d ) ) / p.scale;
					}
				}

				// prefilter the candidates
				final Model<?> model = modelPairwise.copy();
				final List< PointMatch > inliers = consensus( candidatesTmp, model, p.minInliersGene, p.iterations, p.minInlierRatio, p.maxError );

				// reset world coordinates & compute error
				double error = Double.NaN, maxError = Double.NaN, minError = Double.NaN;
				if (!inliers.isEmpty())
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

				if (!inliers.isEmpty())
				{
					allPerGeneInliers.addAll( inliers );
					logger.debug("{}-{}: {}/{}, {}/{}/{}, {}",
								 stDataAname, stDataBname, inliers.size(), candidatesTmp.size(), minError, error, maxError, ((PointST) inliers.get(0).getP1()).getGene());
					//System.out.println( ki + "-" + kj + ": " + inliers.size() + "/" + candidatesTmp.size() + ", " + ((PointST)inliers.get( 0 ).getP1()).getGene() + ", " );
					//GlobalOpt.visualizePair(stDataA, stDataB, new AffineTransform2D(), GlobalOpt.modelToAffineTransform2D( model ).inverse() ).setTitle( gene +"_" + inliers.size() );;
				}

				progressBar.accept( progressPerGene / 4.0 );

				return allPerGeneInliers;
			});
		}

		try
		{
			final List< Future< List< PointMatch > > > futures = service.invokeAll( tasks );
			for ( final Future< List< PointMatch > > future : futures )
				allCandidates.addAll( future.get() );
		}
		catch ( final InterruptedException | ExecutionException e )
		{
			logger.error("Error during pairwise SIFT alignment", e);
			throw new RuntimeException( e );
		}

		//service.shutdown();

		logger.debug("Running consensus across all genes ... ");

		//final InterpolatedAffineModel2D<AffineModel2D, RigidModel2D> model = new InterpolatedAffineModel2D<>( new AffineModel2D(), new RigidModel2D(), 0.1 );//new RigidModel2D();
		//final RigidModel2D model = new RigidModel2D();
		final ArrayList< PointMatch > inliers = consensus( allCandidates, modelGlobal, p.minInliersTotal, p.iterations, p.minInlierRatio, p.maxError );

		// the model that maps J to I
		logger.debug("{}<>{}\t{}\t{}\t{}",
					 stDataAname, stDataBname, inliers.size(), allCandidates.size(), AlignTools.modelToAffineTransform2D((Affine2D<?>) modelGlobal).inverse());

		if ( visualizeResult && inliers.size() >= p.minInliersTotal )
		{
			new ImageJ();

			ImagePlus rendered = AlignTools.visualizePair(
					stDataA, stDataB,
					new AffineTransform2D(),
					AlignTools.modelToAffineTransform2D( (Affine2D<?>)modelGlobal ).inverse(),
					genesToTest.get( 0 ),
					p.scale,
					p.rendering,
					p.renderingSmoothness );

			PointRoi roi = new PointRoi();
			for ( final PointMatch pm : inliers )
			{
				roi.addPoint(
						pm.getP1().getL()[ 0 ] * p.scale - rendered.getCalibration().xOrigin,// finalInterval.min( 0 ),
						pm.getP1().getL()[ 1 ] * p.scale - rendered.getCalibration().yOrigin ); //finalInterval.min( 1 ) );
			}
			
			rendered.setRoi( roi );
			rendered.setTitle( stDataAname + "-" + stDataBname + "-inliers-" + inliers.size() + " (" + AlignTools.defaultGene + ")" );
		}

		// compute errors
		// reset world coordinates & compute error
		double error = Double.NaN, maxError = Double.NaN, minError = Double.NaN;
		if (!inliers.isEmpty())
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

		logger.debug("errors: {}/{}/{}", minError, error, maxError);

		progressBar.accept( 10.0 );
		logger.info("{}<>{}\t{}\t{}\t{}",
					stDataAname, stDataBname, inliers.size(), allCandidates.size(), AlignTools.modelToAffineTransform2D((Affine2D<?>) modelGlobal).inverse());
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

		final ExecutorService service = Executors.newFixedThreadPool(8);
		SpatialDataContainer container = SpatialDataContainer.openExisting(path + "slide-seq-normalized.n5", service);

		List<String> pucks = container.getDatasets();
		final List<STDataAssembly> puckData = container.openAllDatasets().stream().
				map(sdio -> {
					try {return sdio.readData();} catch (IOException e) {throw new RuntimeException(e);}
				}).collect(Collectors.toList());

		// clear the alignment metadata
		for (String match : container.getMatches())
			container.deleteMatch(match);

		// visualize using the global transform
		final double smoothnessFactor = 4.0;

		final SIFTParam p = new SIFTParam();
		p.setIntrinsicParameters( SIFTPreset.VERY_THOROUGH);
		p.minInliersGene = 10;
		p.minInliersTotal = 12;
		p.setDatasetParameters( 300, 0.1, 1024, null, Rendering.Gauss, smoothnessFactor, 0.0, 1.0 );
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

				final STData stDataA = puckData.get(i).data();
				final STData stDataB = puckData.get(j).data();

				final String puckA = pucks.get( i );
				final String puckB = pucks.get( j );

				//System.out.println( new Date( System.currentTimeMillis() ) + ": Finding genes" );

				final List< String > genesToTest = Pairwise.genesToTest( stDataA, stDataB, Entropy.STDEV.label(), 2000 );
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
		logger.debug("done.");
		service.shutdown();
	}


}
