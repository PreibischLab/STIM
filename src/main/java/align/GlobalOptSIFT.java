package align;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;

import data.STData;
import data.STDataStatistics;
import ij.ImageJ;
import io.N5IO;
import io.Path;
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

public class GlobalOptSIFT
{
	public static class Matches
	{
		List< PointMatch > inliers = new ArrayList<>();

		int numInliers = 0;
		int numCandidates = 0;
		String puckAName = "";
		String puckBName = "";

		HashSet<String> genes = new HashSet<String>();

		public double quality()
		{
			if ( numCandidates == 0 )
				return 0;
			else
				return ( (double)numInliers / (double)numCandidates ) * Math.sqrt( numInliers );
		}
	}

	@SuppressWarnings("unchecked")
	protected static Matches loadMatches( final N5FSReader n5, final String puckA, final String puckB )
	{
		final Matches matches = new Matches();

		final String pairwiseGroupName = n5.groupPath( "/", "matches", puckA + "-" + puckB );

		if ( !n5.exists( pairwiseGroupName ) )
			return matches;

		try
		{
			matches.puckAName = n5.getAttribute( pairwiseGroupName, "stDataAname", String.class );
			matches.puckBName = n5.getAttribute( pairwiseGroupName, "stDataBname", String.class );

			if (!puckA.equals( matches.puckAName ) )
				throw new RuntimeException( "mismatch between match folder and metadata: " + puckA + "!=" + matches.puckAName );

			if (!puckB.equals( matches.puckBName ) )
				throw new RuntimeException( "mismatch between match folder and metadata: " + puckB + "!=" + matches.puckBName );

			matches.numInliers = n5.getAttribute( pairwiseGroupName, "inliers", Integer.class );
			matches.numCandidates = n5.getAttribute( pairwiseGroupName, "candidates", Integer.class );

			matches.genes = new HashSet<String>( n5.getAttribute( pairwiseGroupName, "genes", List.class ) );

			final DatasetAttributes datasetAttributes = n5.getDatasetAttributes( pairwiseGroupName );
			matches.inliers = n5.readSerializedBlock( pairwiseGroupName, datasetAttributes, new long[]{0} );

			// reset world coordinates
			for ( final PointMatch pm : matches.inliers )
			{
				for ( int d = 0; d < pm.getP1().getL().length; ++d )
				{
					pm.getP1().getW()[ d ] = pm.getP1().getL()[ d ];
					pm.getP2().getW()[ d ] = pm.getP2().getL()[ d ];
				}
			}
		}
		catch (Exception e)
		{
			System.out.println( "error reading: " + pairwiseGroupName + " from " + n5.getBasePath() + ": " + e );
			e.printStackTrace();
			return new Matches();
		}

		return matches;
	}

	public static void globalOpt(
			final File n5Path,
			final List<String> datasets,
			final boolean useQuality,
			final double lambda,
			final double maxAllowedError,
			final int maxIterations,
			final int maxPlateauwidth,
			final double relativeThreshold,
			final double absoluteThreshold,
			final boolean icpRefine,
			final double icpErrorFraction,
			final double maxAllowedErrorICP,
			final int numIterationsICP,
			final int maxPlateauwhidthICP,
			final int numThreads ) throws IOException
	{
		final N5FSReader n5 = N5IO.openN5( n5Path );

		final ArrayList< STData > puckData = new ArrayList<STData>();
		for ( final String puck : datasets )
			puckData.add( N5IO.readN5( n5, puck ) );

		final HashMap< STData, Tile< RigidModel2D > > dataToTile = new HashMap<>();
		final HashMap< Tile< RigidModel2D >, STData > tileToData = new HashMap<>();
		final HashMap< Tile< RigidModel2D >, Integer > tileToIndex = new HashMap<>();

		// for accessing the quality later
		final double[][] quality = new double[datasets.size()][datasets.size()];
		double maxQuality = -Double.MAX_VALUE;
		double minQuality = Double.MAX_VALUE;

		for ( int i = 0; i < datasets.size() - 1; ++i )
		{
			for ( int j = i + 1; j < datasets.size(); ++j )
			{
				final Matches matches = loadMatches( n5, datasets.get( i ), datasets.get( j ) );//loadMatches( siftMatchesPath, i, j );

				if ( useQuality )
				{
					quality[ i ][ j ] = quality[ j ][ i ] = matches.quality();
	
					maxQuality = Math.max( maxQuality, quality[ i ][ j ] );
					minQuality = Math.min( minQuality, quality[ i ][ j ] );
				}
				else
				{
					quality[ i ][ j ] = quality[ j ][ i ] = 1.0;
				}

				final STData stDataA = puckData.get(i);
				final STData stDataB = puckData.get(j);

				final Tile< RigidModel2D > tileA, tileB;

				if ( !dataToTile.containsKey( stDataA ) )
				{
					tileA = new Tile<>( new RigidModel2D() );
					dataToTile.put( stDataA, tileA );
					tileToData.put( tileA, stDataA );
				}
				else
				{
					tileA = dataToTile.get( stDataA );
				}

				if ( !dataToTile.containsKey( stDataB ) )
				{
					tileB = new Tile<>( new RigidModel2D() );
					dataToTile.put( stDataB, tileB );
					tileToData.put( tileB, stDataB );
				}
				else
				{
					tileB = dataToTile.get( stDataB );
				}

				tileToIndex.putIfAbsent( tileA, i );
				tileToIndex.putIfAbsent( tileB, j );

				final List< PointMatch > inliers = matches.inliers;
				if ( inliers.size() > 0 )
				{
					System.out.println( "Connecting " + i + " to " + j + " ... "); 
					tileA.connect( tileB, inliers );
				}
			}
		}

		if ( useQuality )
		{
			System.out.println( "minQ: " + minQuality );
			System.out.println( "maxQ: " + maxQuality );
	
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

		System.out.println( dataToTile.keySet().size() + " / " + datasets.size() );
		System.out.println( tileToData.keySet().size() + " / " + datasets.size() );

		//System.exit( 0 );

		for ( int i = 0; i < datasets.size(); ++i )
			System.out.println( puckData.get( i ) + ": " + dataToTile.get( puckData.get( i ) ) );

		final TileConfiguration tileConfig = new TileConfiguration();

		tileConfig.addTiles( new HashSet<>( dataToTile.values() ) );
		tileConfig.fixTile( dataToTile.get( puckData.get( 0 ) ) );

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
			System.out.println( "Removed " + tileToIndex.get( removed.getA() ) + " to " + tileToIndex.get( removed.getB() ) + " (" + tileToData.get( removed.getA() ) + " to " + tileToData.get( removed.getB() ) + ")" );

		final List< Pair< STData, AffineTransform2D > > data = new ArrayList<>();

		final N5FSWriter n5Writer = N5IO.openN5write( n5Path );

		for ( int i = 0; i < datasets.size(); ++i )
		{
			final AffineTransform2D transform = AlignTools.modelToAffineTransform2D( dataToTile.get( puckData.get( i ) ).getModel() );

			final String groupName = n5.groupPath( datasets.get( i ) );
			n5Writer.setAttribute( groupName, "model_sift", transform.getRowPackedCopy() );
			n5Writer.setAttribute( groupName, "transform", transform.getRowPackedCopy() ); // will be overwritten by ICP later

			System.out.println( puckData.get( i ) + ": " + transform );
			data.add( new ValuePair<>( puckData.get( i ), transform ) );
		}

		new ImageJ();
		AlignTools.visualizeList( data );

		System.out.println( "Avg error: " + tileConfig.getError() );

		if ( icpRefine )
		{
			//
			// perform ICP refinement
			//
			final int icpIteration = 100;
	
			final TileConfiguration tileConfigICP = new TileConfiguration();
	
			final HashMap< STData, Tile< InterpolatedAffineModel2D<AffineModel2D, RigidModel2D > > > dataToTileICP = new HashMap<>();
			final HashMap< Tile< InterpolatedAffineModel2D<AffineModel2D, RigidModel2D > >, STData > tileToDataICP = new HashMap<>();
	
			for ( final STData stdata : dataToTile.keySet() )
			{
				final Tile< InterpolatedAffineModel2D<AffineModel2D, RigidModel2D > > tile =
						new Tile<>( new InterpolatedAffineModel2D<AffineModel2D, RigidModel2D >( new AffineModel2D(), new RigidModel2D(), lambda ) );
				dataToTileICP.put( stdata, tile );
				tileToDataICP.put( tile, stdata );
			}
	
			for ( int i = 0; i < datasets.size() - 1; ++i )
			{
				for ( int j = i + 1; j < datasets.size(); ++j )
				{
					final Matches matches = loadMatches( n5, datasets.get( i ) , datasets.get( j ) ); //loadMatches( siftMatchesPath, i, j );
	
					// they were connected and we use the genes that RANSAC filtered
					if ( matches.genes.size() > 0 )
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
							System.out.println( i + "<>" + j + " was removed in global opt. Not running ICP." );
						}
						else
						{
							System.out.print( "ICP for: " + i + "<>" + j + ": " );
							for ( final String gene : matches.genes )
								System.out.print( gene + "," );
							System.out.println();
							
							final RigidModel2D modelA = dataToTile.get( puckData.get( i ) ).getModel().copy();
							final RigidModel2D modelB = dataToTile.get( puckData.get( j ) ).getModel().copy();
							final RigidModel2D modelAInv = modelA.createInverse();
	
							// modelA is the identity transform after applying its own inverse
							modelA.preConcatenate( modelAInv );
							// modelB maps B to A
							modelB.preConcatenate( modelAInv );
	
							final AffineModel2D affineB = new AffineModel2D();
							affineB.set( modelB );
	
							final InterpolatedAffineModel2D<AffineModel2D, RigidModel2D > interpolated = //modelB;
									new InterpolatedAffineModel2D<AffineModel2D, RigidModel2D>( affineB, modelB, lambda );

							final double medianDistance = 
									Math.max( new STDataStatistics( puckData.get( i ) ).getMedianDistance(), new STDataStatistics( puckData.get( j ) ).getMedianDistance() );

							final double maxDistance = Math.max( medianDistance * 2, tileConfig.getError() * icpErrorFraction );

							final Pair< InterpolatedAffineModel2D<AffineModel2D, RigidModel2D >, List< PointMatch > > icpT =
									ICPAlign.alignICP( puckData.get( i ), puckData.get( j ), matches.genes, interpolated, maxDistance, icpIteration );
	
							if ( icpT.getB().size() > 0 )
							{
								final Tile< InterpolatedAffineModel2D<AffineModel2D, RigidModel2D > > tileA = dataToTileICP.get( puckData.get( i ) );
								final Tile< InterpolatedAffineModel2D<AffineModel2D, RigidModel2D > > tileB = dataToTileICP.get( puckData.get( j ) );
	
								System.out.println( "Connecting " + i + " to " + j + " with " + icpT.getB().size() + " inliers." ); 
								tileA.connect( tileB, icpT.getB() );
							}
							/*
							List< Pair< STData, AffineTransform2D > > dataTmp = new ArrayList<>();
	
							dataTmp.add( new ValuePair<>( puckData.get( i ), GlobalOpt.modelToAffineTransform2D( modelA ) ) );
							dataTmp.add( new ValuePair<>( puckData.get( j ), GlobalOpt.modelToAffineTransform2D( modelB ) ) );
							dataTmp.add( new ValuePair<>( puckData.get( j ), GlobalOpt.modelToAffineTransform2D( icpT.getA() ) ) );
							
							GlobalOpt.visualizeList( dataTmp ).setTitle( i + "-" + j + "_ICP" );
	
							//SimpleMultiThreading.threadHaltUnClean();
							*/
						}
					}
				}
			}
	
			tileConfigICP.addTiles( new HashSet<>( dataToTileICP.values() ) );
			tileConfigICP.fixTile( dataToTileICP.get( puckData.get( 0 ) ) );
	
			try
			{
				tileConfigICP.preAlign();
	
				TileUtil.optimizeConcurrently(
					new ErrorStatistic( 500 + 1 ),
					maxAllowedErrorICP,
					numIterationsICP,
					maxPlateauwhidthICP,
					1.0,
					tileConfigICP,
					tileConfigICP.getTiles(),
					tileConfigICP.getFixedTiles(),
					numThreads );
	
				System.out.println( " avg=" + tileConfig.getError() + ", min=" + tileConfig.getMinError() + ", max=" + tileConfig.getMaxError() );
			}
			catch ( Exception e )
			{
				System.out.println( ": Could not solve, cause: " + e );
				e.printStackTrace();
			}
	
			final List< Pair< STData, AffineTransform2D > > dataICP = new ArrayList<>();
	
			for ( int i = 0; i < datasets.size(); ++i )
			{
				final AffineTransform2D transform = AlignTools.modelToAffineTransform2D( dataToTileICP.get( puckData.get( i ) ).getModel() );
	
				final String groupName = n5.groupPath( datasets.get( i ) );
				n5Writer.setAttribute( groupName, "model_icp", transform.getRowPackedCopy() );
				n5Writer.setAttribute( groupName, "transform", transform.getRowPackedCopy() ); // overwritten
	
				System.out.println( puckData.get( i ) + ": " + transform );
	
				dataICP.add( new ValuePair<>( puckData.get( i ), transform ) );
			}
	
			AlignTools.visualizeList( dataICP ).setTitle( "ICP-reg" );
			
			System.out.println( "Avg error: " + tileConfig.getError() );
		}
	}

	public static void main( String[] args ) throws IOException
	{
		final String path = Path.getPath();

		//final String[] pucks = new String[] { "Puck_180602_20", "Puck_180602_18", "Puck_180602_17", "Puck_180602_16", "Puck_180602_15", "Puck_180531_23", "Puck_180531_22", "Puck_180531_19", "Puck_180531_18", "Puck_180531_17", "Puck_180531_13", "Puck_180528_22", "Puck_180528_20" };

		final File n5Path = new File( path + "slide-seq-normalized.n5" );
		final N5FSReader n5 = N5IO.openN5( n5Path );
		final List< String > pucks = N5IO.listAllDatasets( n5 );

		final boolean useQuality = true;
		final double lambdaGlobal = 0.1; // rigid only
		final double maxAllowedError = 300;
		final int maxIterations = 500;
		final int maxPlateauwidth = 500;
		final double relativeThreshold = 3.0;
		final double absoluteThreshold = 160;

		final boolean doICP = false;
		final double icpErrorFraction = 1.0 / 10.0;
		final double maxAllowedErrorICP = 140;
		final int numIterationsICP = 3000;
		final int maxPlateauwhidthICP = 500;

		globalOpt(
				n5Path, pucks,
				useQuality,
				lambdaGlobal,
				maxAllowedError,
				maxIterations,
				maxPlateauwidth,
				relativeThreshold,
				absoluteThreshold,
				doICP,
				icpErrorFraction,
				maxAllowedErrorICP,
				numIterationsICP,
				maxPlateauwhidthICP,
				Threads.numThreads() );
	}
}
