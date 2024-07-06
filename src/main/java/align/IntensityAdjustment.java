package align;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import gui.STDataAssembly;
import io.SpatialDataContainer;
import io.SpatialDataIO;
import net.imglib2.realtransform.AffineGet;

import data.STData;
import data.STDataStatistics;
import filter.Filters;
import filter.GaussianFilterFactory;
import filter.GaussianFilterFactory.WeightType;
import io.Path;
import mpicbg.models.Affine1D;
import mpicbg.models.AffineModel1D;
import mpicbg.models.IdentityModel;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.InterpolatedAffineModel1D;
import mpicbg.models.Model;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.Tile;
import mpicbg.models.TileConfiguration;
import mpicbg.models.TranslationModel1D;
import net.imglib2.IterableRealInterval;
import net.imglib2.KDTree;
import net.imglib2.RealCursor;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import util.KDTreeUtil;
import util.Threads;
import org.apache.logging.log4j.Logger;
import util.LoggerUtil;

@Deprecated
public class IntensityAdjustment
{
	private static final Logger logger = LoggerUtil.getLogger();
	public static HashMap< Integer, AffineModel1D > adjustIntensities(
			final SpatialDataContainer container,
			final List< String > pucks,
			final ArrayList< STData > puckData,
			final ArrayList< AffineTransform2D > transforms,
			final double maxDistance,
			final int maxMatches,
			final boolean correctScaling,
			final int nThreads )
	{
		final HashSet< String > genes = new HashSet<>();

		for ( int i = 0; i < pucks.size() - 1; ++i )
		{
			for ( int j = i + 1; j < pucks.size(); ++j )
			{
				// load the matches
				final SiftMatch siftmatches = GlobalOptSIFT.loadMatch(container, pucks.get(i), pucks.get(j));

				if ( siftmatches.getNumInliers() == 0 )
					continue;

				// use all genes that were used for alignment
				for ( final String gene : siftmatches.genes )
				{
					if ( !genes.contains( gene ) )
					{
						boolean validGene = true;

						// check that this gene is available for all pucks
						for ( final STData data : puckData )
						{
							if ( !new HashSet<>( data.getGeneNames() ).contains( gene ) )
							{
								validGene = false;
								break;
							}
						}

						if ( validGene )
							genes.add( gene );
					}
				}
			}
		}

		//genes.clear();
		//genes.add( "Calm2" );
		logger.debug( "Genes to be used: " + genes.size() );

		final ExecutorService service = Threads.createFixedExecutorService( nThreads );

		final HashMap< Pair< Integer, Integer >, ArrayList< PointMatch > > intensityMatches = new HashMap<>();

		for ( int i = 0; i < pucks.size() - 1; ++i )
		{
			for ( int j = i + 1; j < pucks.size(); ++j )
			{
				final int i0 = i;
				final int j0 = j;

				// load the matches
				final SiftMatch siftmatches = GlobalOptSIFT.loadMatch(container, pucks.get(i), pucks.get(j));

				if ( siftmatches.getNumInliers() == 0 )
					continue;

				final List< Callable< ArrayList< PointMatch > > > tasks = new ArrayList<>();

				// use all genes that were used for alignment (the same genes for all pairs, otherwise this is useless)
				for ( final String gene : genes )
				{
					tasks.add( () ->
					{
						final ArrayList< PointMatch > threadMatches = new ArrayList<>();

						// create two transformed IterableRealIntervals
						IterableRealInterval< DoubleType > dataI = puckData.get( i0 ).getExprData( gene );
						IterableRealInterval< DoubleType > dataJ = puckData.get( j0 ).getExprData( gene );
	
						dataI = Filters.filter( dataI, new GaussianFilterFactory<>( new DoubleType( 0 ), maxDistance * 4, WeightType.BY_SUM_OF_WEIGHTS ) );
						dataJ = Filters.filter( dataJ, new GaussianFilterFactory<>( new DoubleType( 0 ), maxDistance * 4, WeightType.BY_SUM_OF_WEIGHTS ) );

						final KDTree< DoubleType > treeJ = KDTreeUtil.createParallelizableKDTreeFrom( dataJ );
						final NearestNeighborSearchOnKDTree< DoubleType > searchJ = new NearestNeighborSearchOnKDTree<>( treeJ );
	
						final RealCursor< DoubleType > cursorI = dataI.localizingCursor();
	
						while ( cursorI.hasNext() )
						{
							cursorI.fwd();
							final double valueI = cursorI.get().get();

							if ( valueI > 0.25 )
							{
								searchJ.search( cursorI );
		
								if ( searchJ.getDistance() < maxDistance )
								{
									final double valueJ = searchJ.getSampler().get().get();
		
									if ( valueI + valueJ > 0.5 && valueJ > 0.25 )
										threadMatches.add(
													new PointMatch(
															new Point( new double[] { valueI } ),
															new Point( new double[] { valueJ } ),
															valueI * valueJ ) );
								}
							}
						}

						return threadMatches;
					});
				}

				// corresponding intensity values
				final ArrayList< PointMatch > localMatches = new ArrayList<>();

				try
				{
					final List< Future< ArrayList< PointMatch > > > futures = service.invokeAll( tasks );
					for ( final Future< ArrayList< PointMatch > > future : futures )
						localMatches.addAll( future.get() );
				}
				catch ( final InterruptedException | ExecutionException e )
				{
					logger.error("Error adjusting intensities", e);
					throw new RuntimeException( e );
				}

				logger.info( i + "-" + j + ": Found " + localMatches.size() + " corresponding measures (using max " + maxMatches + ")" );

				if ( localMatches.size() > 0 )
					intensityMatches.put( new ValuePair< Integer, Integer >( i, j ), localMatches );
			}
		}

		service.shutdown();

		// cut every pair to a max number of matches
		final Random rnd = new Random( 344 );
		for ( final Entry< Pair< Integer, Integer >, ArrayList< PointMatch > > matches : intensityMatches.entrySet() )
		{
			Collections.sort( matches.getValue(), (o1, o2) -> Double.compare(o2.getWeight(), o1.getWeight()));

			ArrayList< PointMatch > newList = new ArrayList<>();
			for ( int i = 0; i < Math.min( maxMatches * 2, matches.getValue().size() ) ; ++i )
				newList.add( matches.getValue().get( i ) );

			ArrayList< PointMatch > inliers = new ArrayList<>();

			try {
				new AffineModel1D().filterRansac( newList, inliers, 10000, 1, 0.1 );
			} catch (NotEnoughDataPointsException e) {}

			matches.getValue().clear();

			double sumWeights = 0;
			if ( inliers.size() > 0 )
			{
				for ( int i = 0; i < Math.min( maxMatches - 1, inliers.size() ) ; ++i )
				{
					matches.getValue().add( inliers.get( i ) );
					sumWeights += inliers.get( i ).getWeight();
				}
			}
			else
			{
				for ( int i = 0; i < Math.min( maxMatches - 1, newList.size() ) ; ++i )
				{
					matches.getValue().add( newList.get( i ) );
					sumWeights += newList.get( i ).getWeight();
				}
			}

			logger.info( matches.getKey().getA() + "-" + matches.getKey().getB() + ": " + newList.size() + ", " + inliers.size() + ", " + sumWeights );
			/*
			while ( matches.getValue().size() > maxMatches - 1 )
				matches.getValue().remove( rnd.nextInt( matches.getValue().size() ) );
			*/

			// add an identity match, 0 should be 0, with the weight being equal to the number of matches
			matches.getValue().add( new PointMatch( new Point( new double[] { 0 } ), new Point( new double[] { 0 } ), sumWeights*100 ) );

			/*
			if ( matches.getKey().getA() == 3 ||  matches.getKey().getB() == 3)
			{
				System.out.println( matches.getKey().getA() + "-" + matches.getKey().getB() + ": Found " + matches.getValue().size() + " corresponding measures." );

				for ( final PointMatch pm : matches.getValue() )
					System.out.println( pm.getP1().getL()[ 0 ] + " == " + pm.getP2().getL()[ 0 ] );
			}*/
		}


		// global optimization
		final double lambda1 = 0.01;
		final double lambda2 = 0.01;

		final HashMap< Integer, AffineModel1D > models =
				globalOpt(
						intensityMatches,
						new InterpolatedAffineModel1D<>(
								new InterpolatedAffineModel1D< AffineModel1D, TranslationModel1D >(
										new AffineModel1D(), new TranslationModel1D(), lambda1 ),
								new IdentityModel(), lambda2 ),
						//new InterpolatedAffineModel1D<AffineModel1D, TranslationModel1D >( new AffineModel1D(), new TranslationModel1D(), 0.9 ),
						//new AffineModel1D(),
						//new TranslationModel1D(),
						0.1,
						2000 ); 

		//System.out.println();
		//System.out.println();

		for ( final Entry< Pair< Integer, Integer >, ArrayList< PointMatch > > matches : intensityMatches.entrySet() )
		{
			//System.out.println( matches.getKey().getA() + "-" + matches.getKey().getB() );

			for ( final PointMatch pm : matches.getValue() )
			{
				final double[] p1 = pm.getP1().getL().clone();
				final double[] p2 = pm.getP2().getL().clone();

				models.get( matches.getKey().getA() ).applyInPlace( p1 );
				models.get( matches.getKey().getB() ).applyInPlace( p2 );

				//System.out.println( p1[ 0 ] + " == " + p2[ 0 ] );
			}
		}

		return models;
	}

	public static < M extends Model< M > & Affine1D< M > > HashMap< Integer, AffineModel1D > globalOpt(
			final HashMap< Pair< Integer, Integer >, ArrayList< PointMatch > > intensityMatches,
			final M model,
			final double maxError,
			final int maxIterations )
	{
		final HashSet< Integer > ids = new HashSet<>();

		for ( final Pair< Integer, Integer > id : intensityMatches.keySet() )
		{
			ids.add( id.getA() );
			ids.add( id.getB() );
		}

		// assemble a list of all tiles
		final HashMap< Integer, Tile< M > > tiles = new HashMap<>();

		for ( final int id : ids )
			tiles.put( id, new Tile<>( model.copy() ) );

		for ( final Entry< Pair< Integer, Integer >, ArrayList< PointMatch > > entry : intensityMatches.entrySet() )
			if ( entry.getValue().size() > 0 )
				addPointMatches(
						entry.getValue(),
						tiles.get( entry.getKey().getA() ),
						tiles.get( entry.getKey().getB() ) );

		// create a new tileconfiguration organizing the global optimization
		final TileConfiguration tc = new TileConfiguration();

		for ( final int id : ids )
		{
			final Tile< M > tile = tiles.get( id );

			if ( tile.getConnectedTiles().size() > 0 || tc.getFixedTiles().contains( tile ) )
				tc.addTile( tile );
		}

		// fix a random tile
		//tc.fixTile( tiles.get( ids.iterator().next() ) );

		try 
		{
			int unaligned = tc.preAlign().size();
			if ( unaligned > 0 )
				logger.info( "Pre-aligned all tiles but " + unaligned );
			else
				logger.info( "Prealigned all tiles" );

			tc.optimize( maxError, maxIterations, 200 );

			logger.info( "Global optimization of " + tc.getTiles().size() +  " view-tiles:" );
			logger.info( "   Avg Error: " + tc.getError() + "px" );
			logger.info( "   Min Error: " + tc.getMinError() + "px" );
			logger.info( "   Max Error: " + tc.getMaxError() + "px" );
		}
		catch (NotEnoughDataPointsException e)
		{
			logger.error( "Global optimization failed: ", e );
		}
		catch (IllDefinedDataPointsException e)
		{
			logger.error( "Global optimization failed: ", e );
		}

		final HashMap< Integer, AffineModel1D > result = new HashMap<>();

		final double[] array = new double[ 2 ];
		double avgScale = 0;

		for ( final int id : ids )
		{
			final Tile< M > tile = tiles.get( id );
			tile.getModel().toArray( array );
			final AffineModel1D modelView = new AffineModel1D();
			modelView.set( array[ 0 ], array[ 1 ] );
			result.put( id, modelView );

			avgScale += array[ 0 ];
		}

		avgScale /= ids.size();
		logger.info( "Avg scale (will be corrected to avoid compression/expansion): " + avgScale );

		final AffineModel1D scale = new AffineModel1D();
		scale.set( 1.0 / avgScale, 0 );

		for ( final AffineModel1D m : result.values() )
		{
			m.preConcatenate( scale );
		}

		double minOffset = Double.MAX_VALUE;

		for ( final AffineModel1D m : result.values() )
		{
			m.toArray( array );
			minOffset = Math.min( minOffset, array[ 1 ] );
		}

		logger.info("Min offset (will be corrected to avoid negative intensities and offsets: " + minOffset );
		//System.out.println( "(" + new Date( System.currentTimeMillis() ) + "): Intensity adjustments:" );

		for ( final Entry<Integer, AffineModel1D> e : result.entrySet() )
		{
			e.getValue().toArray( array );
			array[ 1 ] -= minOffset;
			e.getValue().set( array[ 0 ], array[ 1 ] );

			//System.out.println( e.getKey() + ": " + Util.printCoordinates( array )  );
		}

		/*
		for ( final AffineModel1D m : result.values() )
		{
			final Tile< M > tile = tiles.get( id );
			tile.getModel().toArray( array );
			array[ 1 ] -= minOffset;
			final AffineModel1D modelView = new AffineModel1D();
			modelView.set( array[ 0 ], array[ 1 ] );
			result.put( id, modelView );

			System.out.println( id + ": " + Util.printCoordinates( array )  );
		}*/

		return result;
	}

	private final static void addPointMatches( final List< ? extends PointMatch > correspondences, final Tile< ? > tileA, final Tile< ? > tileB )
	{
		final ArrayList<PointMatch> pm = new ArrayList<>(correspondences);

		if ( correspondences.size() > 0 )
		{
			tileA.addMatches( pm );
			tileB.addMatches( PointMatch.flip( pm ) );
			tileA.addConnectedTile( tileB );
			tileB.addConnectedTile( tileA );
		}
	}

	public static double avgMedianDist( Collection< STDataStatistics > puckDataStatistics )
	{
		double avg = 0;

		for ( final STDataStatistics s : puckDataStatistics )
			avg += s.getMedianDistance();

		return avg / (double)puckDataStatistics.size();
	}

	public static void main( String[] args ) throws IOException
	{
		final String path = Path.getPath();
		final ExecutorService service = Executors.newFixedThreadPool(8);
		final SpatialDataContainer container = SpatialDataContainer.openExisting(path + "slide-seq-test.n5", service);
		final List<String> pucks = container.getDatasets();

		final ArrayList< STData > puckData = new ArrayList<>();
		final ArrayList< STDataStatistics > puckDataStatistics = new ArrayList<>();
		final ArrayList< AffineTransform2D > transforms = new ArrayList<>();

		// load data and transformations for each puck
		for ( final String puck : pucks )
		{
			STDataAssembly stData = container.openDataset(puck).readData();
			puckData.add(stData.data());
			puckDataStatistics.add(stData.statistics());
			transforms.add(stData.transform());
		}

		final double maxDistance = avgMedianDist( puckDataStatistics );
		final int maxMatches = 1000;
		final boolean correctScaling = true;
		final int nThreads = Runtime.getRuntime().availableProcessors() / 2;

		logger.info( "max dist for assignment = " + maxDistance );

		long time = System.currentTimeMillis();

		final HashMap< Integer, AffineModel1D > models = adjustIntensities(container, pucks, puckData, transforms, maxDistance, maxMatches, correctScaling, nThreads );

		logger.info( "took " + (System.currentTimeMillis() - time ) + " msec." );

		for ( int i = 0; i < pucks.size(); ++i )
		{
			AffineModel1D model = models.get( i );
			final String puck = pucks.get(i);

			if ( model == null ) // wasn't connected
				model = new AffineModel1D();

			final SpatialDataIO sdio = container.openDataset(puck);
			sdio.updateTransformation((AffineGet) model, "intensity_transform");

			logger.info( pucks.get( i ) + ": " + model );
		}

		logger.info( "done." );
		service.shutdown();
	}
}
