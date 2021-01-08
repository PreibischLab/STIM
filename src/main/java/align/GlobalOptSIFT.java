package align;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;

import data.STData;
import ij.ImageJ;
import io.N5IO;
import io.Path;
import io.TextFileAccess;
import mpicbg.models.AffineModel2D;
import mpicbg.models.ErrorStatistic;
import mpicbg.models.InterpolatedAffineModel2D;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.RigidModel2D;
import mpicbg.models.Tile;
import mpicbg.models.TileConfiguration;
import mpicbg.models.TileUtil;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class GlobalOptSIFT
{
	public static class Matches
	{
		List< PointMatch > inliers = new ArrayList<>();

		// pairwise transform
		AffineTransform2D t = new AffineTransform2D();

		int numInliers = 0;
		int numCandidates = 0;
		int puckA = -1;
		int puckB = -1;
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
	protected static Matches loadMatches( final N5FSReader n5, final int puckA, final int puckB )
	{
		final Matches matches = new Matches();

		final String pairwiseGroupName = n5.groupPath( "/", "matches", puckA + "-" + puckB );

		if ( !n5.exists( pairwiseGroupName ) )
			return matches;

		try
		{
			matches.puckA = puckA;
			matches.puckB = puckB;
			matches.puckAName = n5.getAttribute( pairwiseGroupName, "i", String.class );
			matches.puckBName = n5.getAttribute( pairwiseGroupName, "j", String.class );
			matches.numInliers = n5.getAttribute( pairwiseGroupName, "inliers", Integer.class );
			matches.numCandidates = n5.getAttribute( pairwiseGroupName, "candidates", Integer.class );
			matches.t = new AffineTransform2D();
			matches.t.set( n5.getAttribute( pairwiseGroupName, "model", double[].class ) );

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

	public static void main( String[] args ) throws IOException
	{
		final String path = Path.getPath();

		//final String[] pucks = new String[] { "Puck_180602_20", "Puck_180602_18", "Puck_180602_17", "Puck_180602_16", "Puck_180602_15", "Puck_180531_23", "Puck_180531_22", "Puck_180531_19", "Puck_180531_18", "Puck_180531_17", "Puck_180531_13", "Puck_180528_22", "Puck_180528_20" };

		final File n5Path = new File( path + "slide-seq-normalized.n5" );
		final N5FSReader n5 = N5IO.openN5( n5Path );
		final List< String > pucks = N5IO.listAllDatasets( n5 );

		final ArrayList< STData > puckData = new ArrayList<STData>();
		for ( final String puck : pucks )
			puckData.add( N5IO.readN5( n5, puck ) );

		final HashMap< STData, Tile< RigidModel2D > > dataToTile = new HashMap<>();
		final HashMap< Tile< RigidModel2D >, STData > tileToData = new HashMap<>();
		final HashMap< Tile< RigidModel2D >, Integer > tileToIndex = new HashMap<>();

		// for accessing the quality later
		final double[][] quality = new double[pucks.size()][pucks.size()];
		double maxQuality = -Double.MAX_VALUE;
		double minQuality = Double.MAX_VALUE;

		for ( int i = 0; i < pucks.size() - 1; ++i )
		{
			for ( int j = i + 1; j < pucks.size(); ++j )
			{
				final Matches matches = loadMatches( n5, i, j );//loadMatches( siftMatchesPath, i, j );

				quality[ i ][ j ] = quality[ j ][ i ] = matches.quality();

				maxQuality = Math.max( maxQuality, quality[ i ][ j ] );
				minQuality = Math.min( minQuality, quality[ i ][ j ] );

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

		System.out.println( "minQ: " + minQuality );
		System.out.println( "maxQ: " + maxQuality );

		for ( int i = 0; i < pucks.size(); ++i )
		{
			System.out.print( "i=" + i + ": " );

			for ( int j = 0; j < pucks.size(); ++j )
			{
				if ( i == j || quality[ i ][ j ] < minQuality )
					quality[ i ][ j ] = 0.0;
				else
					quality[ i ][ j ] = ( quality[ i ][ j ] - minQuality ) / ( maxQuality - minQuality );

				System.out.print( j + "=" + quality[ i ][ j ] + " ");
			}
			System.out.println();
		}

		System.out.println( dataToTile.keySet().size() + " / " + pucks.size() );
		System.out.println( tileToData.keySet().size() + " / " + pucks.size() );

		//System.exit( 0 );

		for ( int i = 0; i < pucks.size(); ++i )
			System.out.println( puckData.get( i ) + ": " + dataToTile.get( puckData.get( i ) ) );

		final TileConfiguration tileConfig = new TileConfiguration();

		tileConfig.addTiles( new HashSet<>( dataToTile.values() ) );
		tileConfig.fixTile( dataToTile.get( puckData.get( 0 ) ) );

		final ArrayList< Pair< Tile< ? >, Tile< ? > > > removedInconsistentPairs = new ArrayList<>();
		
		GlobalOpt.iterativeGlobalOpt(
				tileConfig,
				removedInconsistentPairs,
				300,
				500,
				500,
				3.0,
				160,
				tileToIndex,
				quality );

		for ( final Pair< Tile< ? >, Tile< ? > > removed : removedInconsistentPairs )
			System.out.println( "Removed " + tileToIndex.get( removed.getA() ) + " to " + tileToIndex.get( removed.getB() ) + " (" + tileToData.get( removed.getA() ) + " to " + tileToData.get( removed.getB() ) + ")" );

		final List< Pair< STData, AffineTransform2D > > data = new ArrayList<>();

		final N5FSWriter n5Writer = N5IO.openN5write( n5Path );

		for ( int i = 0; i < pucks.size(); ++i )
		{
			final AffineTransform2D transform = AlignTools.modelToAffineTransform2D( dataToTile.get( puckData.get( i ) ).getModel() );

			final String groupName = n5.groupPath( pucks.get( i ) );
			n5Writer.setAttribute( groupName, "model_sift", transform.getRowPackedCopy() );
			n5Writer.setAttribute( groupName, "transform", transform.getRowPackedCopy() ); // will be overwritten by ICP later

			System.out.println( puckData.get( i ) + ": " + transform );
			data.add( new ValuePair<>( puckData.get( i ), transform ) );
		}

		new ImageJ();
		AlignTools.visualizeList( data );
		
		System.out.println( "Avg error: " + tileConfig.getError() );

		//
		// perform ICP refinement
		//
		final int icpIteration = 100;
		final double lambda = 0.1;

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

		for ( int i = 0; i < pucks.size() - 1; ++i )
		{
			for ( int j = i + 1; j < pucks.size(); ++j )
			{
				final Matches matches = loadMatches( n5, i, j ); //loadMatches( siftMatchesPath, i, j );

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

						final InterpolatedAffineModel2D<AffineModel2D, RigidModel2D > interpolated =
								new InterpolatedAffineModel2D<AffineModel2D, RigidModel2D>( affineB, modelB, lambda );

						final Pair< InterpolatedAffineModel2D<AffineModel2D, RigidModel2D >, List< PointMatch > > icpT =
								ICPAlign.alignICP( puckData.get( i ), puckData.get( j ), matches.genes, interpolated, tileConfig.getError() / 10.0, icpIteration );

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
				140,
				3000,
				500,
				1.0,
				tileConfigICP,
				tileConfigICP.getTiles(),
				tileConfigICP.getFixedTiles(),
				30 );

			System.out.println( " avg=" + tileConfig.getError() + ", min=" + tileConfig.getMinError() + ", max=" + tileConfig.getMaxError() );
		}
		catch ( Exception e )
		{
			System.out.println( ": Could not solve, cause: " + e );
			e.printStackTrace();
		}

		final List< Pair< STData, AffineTransform2D > > dataICP = new ArrayList<>();

		for ( int i = 0; i < pucks.size(); ++i )
		{
			final AffineTransform2D transform = AlignTools.modelToAffineTransform2D( dataToTileICP.get( puckData.get( i ) ).getModel() );

			final String groupName = n5.groupPath( pucks.get( i ) );
			n5Writer.setAttribute( groupName, "model_icp", transform.getRowPackedCopy() );
			n5Writer.setAttribute( groupName, "transform", transform.getRowPackedCopy() ); // overwritten

			System.out.println( puckData.get( i ) + ": " + transform );

			dataICP.add( new ValuePair<>( puckData.get( i ), transform ) );
		}

		AlignTools.visualizeList( dataICP ).setTitle( "ICP-reg" );
		
		System.out.println( "Avg error: " + tileConfig.getError() );

	}
}
