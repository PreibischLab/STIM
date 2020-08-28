package align;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import align.GlobalOpt.Alignment;
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
import net.imglib2.multithreading.SimpleMultiThreading;
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

	protected static Matches loadMatches( final String path, final int puckA, final int puckB )
	{
		final Matches matches = new Matches();

		final File file = new File( path, puckA + "-" + puckB + ".pm.txt" );

		if ( !file.exists() )
		{
			//System.out.println( "Cannot find file '" + file.getAbsolutePath() + "'. Returning empty list." );
			return matches;
		}
		else
		{
			//System.out.println( "LOADING '" + file.getAbsolutePath() + "' ..." );
		}

		final BufferedReader in = TextFileAccess.openFileRead( file.getAbsolutePath() );

		try
		{
			while ( in.ready() )
			{
				final String input = in.readLine().trim();

				if ( input.equals( "DETAILS" ) )
					break;

				final String[] line = input.split( "\t" );
				String p1s = line[ 0 ].trim();
				String p2s = line[ 1 ].trim();

				p1s = p1s.substring( 1, p1s.length() - 1 );
				p2s = p2s.substring( 1, p2s.length() - 1 );

				String[] p1entries = p1s.split( "," );
				String[] p2entries = p2s.split( "," );

				final double[] p1 = new double[ p1entries.length ];
				final double[] p2 = new double[ p2entries.length ];

				for ( int d = 0; d < p1.length; ++d )
					p1[ d ] = Double.parseDouble( p1entries[ d ].trim() );

				for ( int d = 0; d < p2.length; ++d )
					p2[ d ] = Double.parseDouble( p2entries[ d ].trim() );

				matches.inliers.add( new PointMatch( new Point( p1 ), new Point( p2 ) ) );
			}

			String[] entries = in.readLine().trim().split( "\t" );

			if ( Integer.parseInt( entries[ 0 ] ) != puckA )
				throw new RuntimeException( "puck number off:" + puckA + "!=" + Integer.parseInt( entries[ 0 ] ) );
			if ( puckB != Integer.parseInt( entries[ 1 ] ) )
				throw new RuntimeException( "puck number off:" + puckB + "!=" + Integer.parseInt( entries[ 1 ] ) );
			
			matches.numInliers = Integer.parseInt( entries[ 2 ] ); // inliers
			matches.numCandidates = Integer.parseInt( entries[ 3 ] ); // candidates

			if ( matches.numInliers != matches.inliers.size() )
				throw new RuntimeException( "inlier number off:" + matches.numInliers + "!=" + matches.inliers.size() );

			final String model = entries[ 4 ].substring( entries[ 4 ].indexOf( "(" ) + 1, entries[ 4 ].indexOf( ")" )  );

			final String[] mXX = model.split( "," );
			final double[] m = new double[ 6 ];

			for ( int k = 0; k < m.length; ++k )
				m[ k ] = Double.parseDouble( mXX[ k ].trim() );

			matches.t = new AffineTransform2D();
			matches.t.set( m );

			entries = in.readLine().trim().split( "\t" );
			matches.puckAName = entries[ 0 ].trim();
			matches.puckBName = entries[ 1 ].trim();

			if ( !in.ready() || !in.readLine().trim().equals( "GENES" ) )
				throw new RuntimeException( "GENES not stored." );

			matches.genes = new HashSet<String>();
			
			while (in.ready() )
			{
				String gene = in.readLine().trim();
				if ( gene.length() > 0 )
					matches.genes.add( gene );
			}

			in.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
			return null;
		}

		return matches;
	}

	public static void main( String[] args ) throws IOException
	{
		final String path = Path.getPath();
		final String siftMatchesPath = path + "/slide-seq/sift_3_pm";

		final String[] pucks = new String[] { "Puck_180602_20", "Puck_180602_18", "Puck_180602_17", "Puck_180602_16", "Puck_180602_15", "Puck_180531_23", "Puck_180531_22", "Puck_180531_19", "Puck_180531_18", "Puck_180531_17", "Puck_180531_13", "Puck_180528_22", "Puck_180528_20" };

		final ArrayList< STData > puckData = new ArrayList<STData>();
		for ( final String puck : pucks )
			puckData.add( N5IO.readN5( new File( path + "slide-seq/" + puck + "-normalized.n5" ) ) );

		final HashMap< STData, Tile< RigidModel2D > > dataToTile = new HashMap<>();
		final HashMap< Tile< RigidModel2D >, STData > tileToData = new HashMap<>();
		final HashMap< Tile< RigidModel2D >, Integer > tileToIndex = new HashMap<>();

		// for accessing the quality later
		final double[][] quality = new double[pucks.length][pucks.length];
		double maxQuality = -Double.MAX_VALUE;
		double minQuality = Double.MAX_VALUE;

		for ( int i = 0; i < pucks.length - 1; ++i )
		{
			for ( int j = i + 1; j < pucks.length; ++j )
			{
				final Matches matches = loadMatches( siftMatchesPath, i, j );

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

		for ( int i = 0; i < pucks.length; ++i )
		{
			System.out.print( "i=" + i + ": " );

			for ( int j = 0; j < pucks.length; ++j )
			{
				if ( i == j || quality[ i ][ j ] < minQuality )
					quality[ i ][ j ] = 0.0;
				else
					quality[ i ][ j ] = ( quality[ i ][ j ] - minQuality ) / ( maxQuality - minQuality );

				System.out.print( j + "=" + quality[ i ][ j ] + " ");
			}
			System.out.println();
		}

		System.out.println( dataToTile.keySet().size() + " / " + pucks.length );
		System.out.println( tileToData.keySet().size() + " / " + pucks.length );

		//System.exit( 0 );

		for ( int i = 0; i < pucks.length; ++i )
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

		for ( int i = 0; i < pucks.length; ++i )
		{
			System.out.println( puckData.get( i ) + ": " + dataToTile.get( puckData.get( i ) ).getModel() );
			data.add( new ValuePair<>( puckData.get( i ), GlobalOpt.modelToAffineTransform2D( dataToTile.get( puckData.get( i ) ).getModel() ) ) );
		}

		new ImageJ();
		GlobalOpt.visualizeList( data );
		
		System.out.println( "Avg error: " + tileConfig.getError() );

		//
		// perform ICP refinement
		//
		final int icpIteration = 25;

		final TileConfiguration tileConfigICP = new TileConfiguration();

		for ( final Tile<?> t : dataToTile.values() )
		{
			for ( final Tile<?> s : dataToTile.values() )
				t.removeConnectedTile( s );

			System.out.println( t.getMatches().size() + ", " + t.getConnectedTiles().size() );
		}
		
		tileConfigICP.addTiles( new HashSet<>( dataToTile.values() ) );
		tileConfigICP.fixTile( dataToTile.get( puckData.get( 0 ) ) );

		for ( int i = 0; i < pucks.length - 1; ++i )
		{
			for ( int j = i + 1; j < pucks.length; ++j )
			{
				final Matches matches = loadMatches( siftMatchesPath, i, j );

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

						// modelA is the identity transform
						modelA.preConcatenate( modelAInv );
						// modelB maps B to A
						modelB.preConcatenate( modelAInv );

						final AffineModel2D affine = new AffineModel2D();
						affine.set( modelB );

						final InterpolatedAffineModel2D<AffineModel2D, RigidModel2D > interpolated =
								new InterpolatedAffineModel2D<AffineModel2D, RigidModel2D>( affine, modelB, 0.1 );

						final Pair< RigidModel2D, List< PointMatch > > icpT =
								ICPAlign.alignICP( puckData.get( i ), puckData.get( j ), matches.genes, modelB, tileConfig.getError() / 5.0, icpIteration );

						if ( icpT.getB().size() > 0 )
						{
							final Tile< RigidModel2D > tileA = dataToTile.get( puckData.get( i ) );
							final Tile< RigidModel2D > tileB = dataToTile.get( puckData.get( j ) );

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

		try
		{
			tileConfig.preAlign();

			TileUtil.optimizeConcurrently(
				new ErrorStatistic( 500 + 1 ),
				140,
				3000,
				500,
				1.0,
				tileConfig,
				tileConfig.getTiles(),
				tileConfig.getFixedTiles(),
				30 );

			System.out.println( " avg=" + tileConfig.getError() + ", min=" + tileConfig.getMinError() + ", max=" + tileConfig.getMaxError() );
		}
		catch ( Exception e )
		{
			System.out.println( ": Could not solve, cause: " + e );
			e.printStackTrace();
		}

		final List< Pair< STData, AffineTransform2D > > dataICP = new ArrayList<>();

		for ( int i = 0; i < pucks.length; ++i )
		{
			System.out.println( puckData.get( i ) + ": " + dataToTile.get( puckData.get( i ) ).getModel() );
			dataICP.add( new ValuePair<>( puckData.get( i ), GlobalOpt.modelToAffineTransform2D( dataToTile.get( puckData.get( i ) ).getModel() ) ) );
		}

		GlobalOpt.visualizeList( dataICP ).setTitle( "ICP" );
		
		System.out.println( "Avg error: " + tileConfig.getError() );

	}
}
