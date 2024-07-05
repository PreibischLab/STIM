package align;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import data.STData;
import gui.STDataAssembly;
import gui.bdv.AddedGene.Rendering;
import ij.ImageJ;
import io.Path;
import io.SpatialDataContainer;
import io.SpatialDataIO;
import mpicbg.models.AffineModel2D;
import mpicbg.models.ErrorStatistic;
import mpicbg.models.InterpolatedAffineModel2D;
import mpicbg.models.PointMatch;
import mpicbg.models.RigidModel2D;
import mpicbg.models.Tile;
import mpicbg.models.TileConfiguration;
import mpicbg.models.TileUtil;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import util.Threads;
import org.apache.logging.log4j.Logger;
import util.LoggerUtil;

public class GlobalOptSIFT
{
	private static final Logger logger = LoggerUtil.getLogger();
	protected static SiftMatch loadMatch(final SpatialDataContainer container, final String datasetA, final String datasetB ) {

		SiftMatch match;
		try {
			match = container.loadPairwiseMatch(datasetA, datasetB);

			// reset world coordinates
			for (final PointMatch pm : match.getInliers()) {
				for (int d = 0; d < pm.getP1().getL().length; ++d) {
					pm.getP1().getW()[d] = pm.getP1().getL()[d];
					pm.getP2().getW()[d] = pm.getP2().getL()[d];
				}
			}
		}
		catch (Exception e) {
			final String matchName = container.constructMatchName(datasetA, datasetB);
			logger.error("error reading: " + matchName + ": " + e);
			e.printStackTrace();
			match = new SiftMatch();
		}

		return match;
	}

	public static void globalOpt(
			final SpatialDataContainer container,
			final List<String> datasets,
			final boolean useQuality,
			final double lambda,
			final double maxAllowedError,
			final int maxIterations,
			final int maxPlateauwidth,
			final double relativeThreshold,
			final double absoluteThreshold,
			final boolean icpRefine,
			final int icpIterations,
			final double icpErrorFactor,
			final double maxAllowedErrorICP,
			final int numIterationsICP,
			final int maxPlateauwidthICP,
			final int numThreads,
			final boolean skipDisplayResults,
			final double smoothnessFactor,
			final String displaygene ) throws IOException
	{
		final ArrayList<SpatialDataIO> ioObjects = new ArrayList<>();
		final ArrayList<STDataAssembly> data = new ArrayList<>();
		for (final String name : datasets) {
			SpatialDataIO sdio = container.openDataset(name);
			ioObjects.add(sdio);
			data.add(sdio.readData());
		}

		final HashMap<STDataAssembly, Tile<InterpolatedAffineModel2D<AffineModel2D, RigidModel2D>>> dataToTile = new HashMap<>();
		final HashMap<Tile<InterpolatedAffineModel2D<AffineModel2D, RigidModel2D>>, STDataAssembly> tileToData = new HashMap<>();
		final HashMap< Tile< InterpolatedAffineModel2D<AffineModel2D, RigidModel2D > >, Integer > tileToIndex = new HashMap<>();

		// for accessing the quality later
		final double[][] quality = new double[datasets.size()][datasets.size()];
		double maxQuality = -Double.MAX_VALUE;
		double minQuality = Double.MAX_VALUE;

		final double lambda1 = icpRefine ? 1.0 : lambda;
		logger.debug( "Lambda for SIFT global align (amount of regularization by rigid model): " + lambda1 );

		for ( int i = 0; i < datasets.size() - 1; ++i )
		{
			for ( int j = i + 1; j < datasets.size(); ++j )
			{
				final SiftMatch match = loadMatch(container, datasets.get(i), datasets.get(j));

				if ( useQuality )
				{
					quality[ i ][ j ] = quality[ j ][ i ] = match.quality();
	
					maxQuality = Math.max( maxQuality, quality[ i ][ j ] );
					minQuality = Math.min( minQuality, quality[ i ][ j ] );
				}
				else
				{
					quality[ i ][ j ] = quality[ j ][ i ] = 1.0;
				}

				final STDataAssembly stDataA = data.get(i);
				final STDataAssembly stDataB = data.get(j);

				final Tile< InterpolatedAffineModel2D<AffineModel2D, RigidModel2D > > tileA, tileB;

				if ( !dataToTile.containsKey( stDataA ) )
				{
					tileA = new Tile<>( new InterpolatedAffineModel2D<>( new AffineModel2D(), new RigidModel2D(), lambda1 ) );
					dataToTile.put( stDataA, tileA );
					tileToData.put( tileA, stDataA );
				}
				else
				{
					tileA = dataToTile.get( stDataA );
				}

				if ( !dataToTile.containsKey( stDataB ) )
				{
					tileB = new Tile<>( new InterpolatedAffineModel2D<>( new AffineModel2D(), new RigidModel2D(), lambda1 ) );
					dataToTile.put( stDataB, tileB );
					tileToData.put( tileB, stDataB );
				}
				else
				{
					tileB = dataToTile.get( stDataB );
				}

				tileToIndex.putIfAbsent( tileA, i );
				tileToIndex.putIfAbsent( tileB, j );

				final List< PointMatch > inliers = match.getInliers();
				if (!inliers.isEmpty())
				{
					logger.debug( "Connecting " + i + " to " + j + " ... "); 
					tileA.connect( tileB, inliers );
				}
			}
		}

		if ( useQuality )
		{
			logger.debug( "minQ: " + minQuality );
			logger.debug( "maxQ: " + maxQuality );
	
			for ( int i = 0; i < datasets.size(); ++i )
			{
				System.out.print( "i=" + i + ": " );
	
				for ( int j = 0; j < datasets.size(); ++j )
				{
					if ( i == j || quality[ i ][ j ] < minQuality )
						quality[ i ][ j ] = 0.0;
					else
						quality[ i ][ j ] = ( quality[ i ][ j ] - minQuality ) / ( maxQuality - minQuality );
	
					System.out.print( j + "=" + quality[ i ][ j ] + " ");
				}
				System.out.println();
			}
		}

		logger.debug( dataToTile.keySet().size() + " / " + datasets.size() );
		logger.debug( tileToData.keySet().size() + " / " + datasets.size() );

		//System.exit( 0 );

		for ( int i = 0; i < datasets.size(); ++i )
			logger.debug( data.get( i ) + ": " + dataToTile.get( data.get( i ) ).getModel() );

		final TileConfiguration tileConfig = new TileConfiguration();

		tileConfig.addTiles( new HashSet<>( dataToTile.values() ) );
		tileConfig.fixTile( dataToTile.get( data.get( 0 ) ) );

		final ArrayList< Pair< Tile< ? >, Tile< ? > > > removedInconsistentPairs = new ArrayList<>();

		GlobalOpt.iterativeGlobalOpt(
				tileConfig,
				removedInconsistentPairs,
				maxAllowedError,
				maxIterations,
				maxPlateauwidth,
				relativeThreshold,
				absoluteThreshold,
				tileToIndex,
				quality,
				numThreads );

		for ( final Pair< Tile< ? >, Tile< ? > > removed : removedInconsistentPairs )
			logger.info( "Removed " + tileToIndex.get( removed.getA() ) + " to " + tileToIndex.get( removed.getB() ) + " (" + tileToData.get( removed.getA() ) + " to " + tileToData.get( removed.getB() ) + ")" );

		final List< Pair< STData, AffineTransform2D > > dataTrafoPair = new ArrayList<>();

		for ( int i = 0; i < datasets.size(); ++i )
		{
			final AffineTransform2D transform = AlignTools.modelToAffineTransform2D( dataToTile.get( data.get( i ) ).getModel() );

			ioObjects.get(i).updateTransformation(transform, "model_sift");
			ioObjects.get(i).updateTransformation(transform, "transform"); // will be overwritten by ICP later

			logger.debug( data.get( i ) + ": " + transform );
			dataTrafoPair.add(new ValuePair<>(data.get(i).data(), transform));
		}

		if ( !skipDisplayResults )
		{
			new ImageJ();
			AlignTools.visualizeList(dataTrafoPair, AlignTools.defaultScale, Rendering.Gauss, smoothnessFactor, displaygene, true);
		}

		logger.info( "Avg error: " + tileConfig.getError() );

		if ( icpRefine )
		{
			//
			// perform ICP refinement
			//
			final TileConfiguration tileConfigICP = new TileConfiguration();
	
			final HashMap<STDataAssembly, Tile<InterpolatedAffineModel2D<AffineModel2D, RigidModel2D>>> dataToTileICP = new HashMap<>();
			final HashMap<Tile<InterpolatedAffineModel2D<AffineModel2D, RigidModel2D>>, STDataAssembly> tileToDataICP = new HashMap<>();
	
			for ( final STDataAssembly stdata : dataToTile.keySet() )
			{
				final Tile< InterpolatedAffineModel2D<AffineModel2D, RigidModel2D > > tile =
						new Tile<>( new InterpolatedAffineModel2D<>( new AffineModel2D(), new RigidModel2D(), lambda ) );
				dataToTileICP.put( stdata, tile );
				tileToDataICP.put( tile, stdata );
			}
	
			for ( int i = 0; i < datasets.size() - 1; ++i )
			{
				for ( int j = i + 1; j < datasets.size(); ++j )
				{
					final SiftMatch matches = loadMatch(container, datasets.get(i) , datasets.get(j));
	
					// they were connected and we use the genes that RANSAC filtered
					if (!matches.genes.isEmpty())
					{
						// was this one removed during global opt?
						boolean wasRemoved = false;
	
						for ( final Pair< Tile< ? >, Tile< ? > > removed : removedInconsistentPairs )
						{
							final int id0 = tileToIndex.get( removed.getA() );
							final int id1 = tileToIndex.get( removed.getB() );
	
							if ( i == id0 && j == id1 || j == id0 && i == id1 )
							{
								wasRemoved = true;
								break;
							}
						}
	
						if ( wasRemoved )
						{
							logger.warn( i + "<>" + j + " was removed in global opt. Not running ICP." );
						}
						else
						{
							System.out.print( "ICP for: " + i + "<>" + j + ": " );
							for ( final String gene : matches.genes )
								System.out.print( gene + "," );
							System.out.println();
							
							final RigidModel2D modelA = dataToTile.get(data.get(i)).getModel().getB().copy();
							final RigidModel2D modelB = dataToTile.get(data.get(j)).getModel().getB().copy();
							final RigidModel2D modelAInv = modelA.createInverse();
	
							// modelA is the identity transform after applying its own inverse
							modelA.preConcatenate( modelAInv );
							// modelB maps B to A
							modelB.preConcatenate( modelAInv );
	
							final AffineModel2D affineB = new AffineModel2D();
							affineB.set( modelB );
	
							final InterpolatedAffineModel2D<AffineModel2D, RigidModel2D > interpolated = //modelB;
									new InterpolatedAffineModel2D<>( affineB, modelB, lambda );

							final double medianDistance = 
									Math.max(data.get(i).statistics().getMedianDistance(), data.get(j).statistics().getMedianDistance());

							final double maxDistance = medianDistance * icpErrorFactor;

							final ExecutorService service = Executors.newFixedThreadPool( Threads.numThreads() );

							final Pair< InterpolatedAffineModel2D<AffineModel2D, RigidModel2D >, List< PointMatch > > icpT =
									ICPAlign.alignICP(data.get(i).data(), new AffineTransform2D(), data.get(j).data(), new AffineTransform2D(), matches.genes, interpolated, maxDistance, maxDistance / 2.0, new AtomicInteger( icpIterations ), null, null, null, null, service );

							service.shutdown();

							if (!icpT.getB().isEmpty())
							{
								final Tile< InterpolatedAffineModel2D<AffineModel2D, RigidModel2D > > tileA = dataToTileICP.get(data.get(i));
								final Tile< InterpolatedAffineModel2D<AffineModel2D, RigidModel2D > > tileB = dataToTileICP.get(data.get(j));
	
								logger.info( "Connecting " + i + " to " + j + " with " + icpT.getB().size() + " inliers." ); 
								tileA.connect( tileB, icpT.getB() );
							}

							/*
							List< Pair< STData, AffineTransform2D > > dataTmp = new ArrayList<>();
	
							dataTmp.add( new ValuePair<>( data.get( i ), GlobalOpt.modelToAffineTransform2D( modelA ) ) );
							dataTmp.add( new ValuePair<>( data.get( j ), GlobalOpt.modelToAffineTransform2D( modelB ) ) );
							dataTmp.add( new ValuePair<>( data.get( j ), GlobalOpt.modelToAffineTransform2D( icpT.getA() ) ) );
							
							GlobalOpt.visualizeList( dataTmp ).setTitle( i + "-" + j + "_ICP" );
	
							//SimpleMultiThreading.threadHaltUnClean();
							*/
						}
					}
				}
			}
	
			tileConfigICP.addTiles( new HashSet<>( dataToTileICP.values() ) );
			tileConfigICP.fixTile(dataToTileICP.get(data.get(0)));
	
			try
			{
				tileConfigICP.preAlign();
	
				TileUtil.optimizeConcurrently(
					new ErrorStatistic( 500 + 1 ),
					maxAllowedErrorICP,
					numIterationsICP,
					maxPlateauwidthICP,
					1.0,
					tileConfigICP,
					tileConfigICP.getTiles(),
					tileConfigICP.getFixedTiles(),
					numThreads );
	
				logger.info( " avg=" + tileConfigICP.getError() + ", min=" + tileConfigICP.getMinError() + ", max=" + tileConfigICP.getMaxError() );
			}
			catch ( Exception e )
			{
				logger.error( ": Could not solve, cause: " + e );
				e.printStackTrace();
			}
	
			final List< Pair< STData, AffineTransform2D > > dataICP = new ArrayList<>();
	
			for ( int i = 0; i < datasets.size(); ++i )
			{
				final AffineTransform2D transform = AlignTools.modelToAffineTransform2D(dataToTileICP.get(data.get(i)).getModel());
	
				container.openDataset(datasets.get(i)).updateTransformation(transform, "model_icp");
				container.openDataset(datasets.get(i)).updateTransformation(transform, "transform");

				logger.debug( data.get( i ) + ": " + transform );
	
				dataICP.add(new ValuePair<>(data.get(i).data(), transform));
			}

			if ( !skipDisplayResults )
				AlignTools.visualizeList( dataICP, AlignTools.defaultScale, Rendering.Gauss, smoothnessFactor, displaygene, true ).setTitle( "ICP-reg" );

			logger.info( "Avg error: " + tileConfigICP.getError() );
		}
	}

	public static void main( String[] args ) throws IOException
	{
		final String path = Path.getPath();

		//final String[] pucks = new String[] { "Puck_180602_20", "Puck_180602_18", "Puck_180602_17", "Puck_180602_16", "Puck_180602_15", "Puck_180531_23", "Puck_180531_22", "Puck_180531_19", "Puck_180531_18", "Puck_180531_17", "Puck_180531_13", "Puck_180528_22", "Puck_180528_20" };

		final ExecutorService service = Executors.newFixedThreadPool(8);
		final SpatialDataContainer container = SpatialDataContainer.openExisting(path + "slide-seq-normalized.n5", service);
		final List<String> pucks = container.getDatasets();

		final boolean useQuality = true;
		final double lambdaGlobal = 0.1; // rigid only
		final double maxAllowedError = 300;
		final int maxIterations = 500;
		final int maxPlateauwidth = 500;
		final double relativeThreshold = 3.0;
		final double absoluteThreshold = 160;

		final boolean doICP = false;
		final int icpIterations = 100;
		final double icpErrorFraction = 2.0;
		final double maxAllowedErrorICP = 140;
		final int numIterationsICP = 3000;
		final int maxPlateauwidthICP = 500;

		globalOpt(
				container,
				pucks,
				useQuality,
				lambdaGlobal,
				maxAllowedError,
				maxIterations,
				maxPlateauwidth,
				relativeThreshold,
				absoluteThreshold,
				doICP,
				icpIterations,
				icpErrorFraction,
				maxAllowedErrorICP,
				numIterationsICP,
				maxPlateauwidthICP,
				Threads.numThreads(),
				false,
				AlignTools.defaultSmoothnessFactor,
				AlignTools.defaultGene );
		service.shutdown();
	}
}
