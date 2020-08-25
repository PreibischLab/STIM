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
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.RigidModel2D;
import mpicbg.models.Tile;
import mpicbg.models.TileConfiguration;
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

		public double quality()
		{
			return numInliers;
		}
	}

	protected static Matches loadMatches( final String path, final int puckA, final int puckB )
	{
		final Matches matches = new Matches();

		final File file = new File( path + "/slide-seq/sift_2_pm", puckA + "-" + puckB + ".pm.txt" );

		if ( !file.exists() )
		{
			System.out.println( "Cannot find file '" + file.getAbsolutePath() + "'. Returning empty list." );
			return matches;
		}
		else
		{
			System.out.println( "LOADING '" + file.getAbsolutePath() + "' ..." );
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

		final String[] pucks = new String[] { "Puck_180602_20", "Puck_180602_18", "Puck_180602_17", "Puck_180602_16", "Puck_180602_15", "Puck_180531_23", "Puck_180531_22", "Puck_180531_19", "Puck_180531_18", "Puck_180531_17", "Puck_180531_13", "Puck_180528_22", "Puck_180528_20" };

		final ArrayList< STData > puckData = new ArrayList<STData>();
		for ( final String puck : pucks )
			puckData.add( N5IO.readN5( new File( path + "slide-seq/" + puck + "-normalized.n5" ) ) );

		new ImageJ();

		/*
		final List< Pair< STData, AffineTransform2D > > initialdata = new ArrayList<>();

//		for ( int i = 0; i < puckData.size(); ++i )
//			initialdata.add( new ValuePair<>(puckData.get( i ), new AffineTransform2D()) );

		final int debugA = 11;

		initialdata.add( new ValuePair<>( puckData.get( debugA ), new AffineTransform2D() ) );
		for ( int debugB = debugA + 1; debugB < puckData.size(); ++debugB )
		{
			final Alignment align = Alignment.getAlignment( alignments, debugA, debugB );
			if ( align.quality > 0 )
			{
				System.out.println( (initialdata.size()+1) + ": " + debugA + " >> " + debugB + " == " + align.quality);

				initialdata.add( new ValuePair<>(puckData.get( debugB ), align.t ) );
			}
		}

		GlobalOpt.visualizeList( initialdata );

		SimpleMultiThreading.threadHaltUnClean();
		*/

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
				final Matches matches = loadMatches( path, i, j );

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
				300,
				tileToIndex,
				quality );

		for ( final Pair< Tile< ? >, Tile< ? > > removed : removedInconsistentPairs )
			System.out.println( "Removed " + tileToIndex.get( removed.getA() ) + " to " + tileToIndex.get( removed.getB() ) + " (" + tileToData.get( removed.getA() ) + " to " + tileToData.get( removed.getB() ) + ")" );

		final List< Pair< STData, AffineTransform2D > > data = new ArrayList<>();

		for ( int i = 0; i < pucks.length; ++i )
		{
			System.out.println( puckData.get( i ) + ": " + dataToTile.get( puckData.get( i ) ).getModel() );

			final RigidModel2D model = dataToTile.get( puckData.get( i ) ).getModel();
			/*
			//  m00, m10, m01, m11, m02, m12
			final double[] array = new double[ 6 ];

			model.toArray(array);

			final AffineTransform2D t = new AffineTransform2D();
			t.set( array[ 0 ], array[ 2 ], array[ 4 ], array[ 1 ], array[ 3 ], array[ 5 ] );
			*/
			data.add( new ValuePair<>( puckData.get( i ), GlobalOpt.modelToAffineTransform2D( model ) ) );
		}

		GlobalOpt.visualizeList( data );
	}
}
