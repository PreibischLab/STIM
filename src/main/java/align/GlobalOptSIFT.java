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
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;

public class GlobalOptSIFT
{
	protected static List< PointMatch > loadMatches( final String path, final int puckA, final int puckB )
	{
		final List< PointMatch > inliers = new ArrayList<>();

		final File file = new File( path + "/slide-seq/sift_1_pm", puckA + "-" + puckB + ".pm.txt" );

		if ( !file.exists() )
		{
			System.out.println( "Cannot find file '" + file.getAbsolutePath() + "'. Returning empty list." );
			return inliers;
		}

		final BufferedReader in = TextFileAccess.openFileRead( file.getAbsolutePath() );

		/*
		if ( ( puckA == 0 && puckB == 2 ) || ( puckA == 0 && puckB == 5 ) || ( puckA == 0 && puckB == 7 )|| ( puckA == 0 && puckB == 8 )|| ( puckA == 0 && puckB == 9 )|| ( puckA == 0 && puckB == 10 )|| ( puckA == 0 && puckB == 11 ) ||
				( puckA == 1 && puckB == 8 ) ||
				( puckA == 2 && puckB == 4 ) || ( puckA == 2 && puckB == 7 ) || ( puckA == 2 && puckB == 10 )|| ( puckA == 2 && puckB == 11 ) || 
				( puckA == 4 && puckB == 5 ) || ( puckA == 4 && puckB == 7 ) || ( puckA == 4 && puckB == 11 ) ||
				( puckA == 5 && puckB == 7 ) || ( puckA == 5 && puckB == 8 ) || ( puckA == 5 && puckB == 9 ) || ( puckA == 5 && puckB == 10 ) || ( puckA == 5 && puckB == 11 ) ||
				( puckA == 6 && puckB == 7 ) || ( puckA == 6 && puckB == 11 ) || ( puckA == 6 && puckB == 12 ) ||
				( puckA == 7 && puckB == 9 ) || ( puckA == 7 && puckB == 10 ) ||
				( puckA == 8 && puckB == 10 ) || ( puckA == 8 && puckB == 12 ) ||  // vs 12 could be moved
				( puckA == 9 && puckB == 11 ) )
			if ( Math.abs( puckA - puckB ) > 2 )
			return inliers;
		*/

		try
		{
			while ( in.ready() )
			{
				final String[] line = in.readLine().trim().split( "\t" );
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

				inliers.add( new PointMatch( new Point( p1 ), new Point( p2 ) ) );

				//System.out.println( Util.printCoordinates( p1 ) );
				//System.out.println( Util.printCoordinates( p2 ) );
			}

			in.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
			return null;
		}

		return inliers;
	}

	protected static ArrayList< Alignment > loadPairwiseAlignments( final String file )
	{
		final BufferedReader in = TextFileAccess.openFileRead( file );

		final ArrayList< Alignment > alignments = new ArrayList<>();

		final int modelEntry = 4;

		try
		{
			while( in.ready() )
			{
				final Alignment align = new Alignment();

				// 0	1	59	114	2d-affine: (-0.9992259508792166, -0.03933826495316421, 6086.339263900335, 0.03933826495316421, -0.9992259508792166, 6397.2829715976395)
				final String[] entries = in.readLine().trim().split( "\t" );
				int puckA = align.i = Integer.parseInt( entries[ 0 ] );
				int puckB = align.j = Integer.parseInt( entries[ 1 ] );
				final int inliers = Integer.parseInt( entries[ 2 ] ); // inliers
				final int candidates = Integer.parseInt( entries[ 3 ] ); // candidates
				align.quality = inliers;//(double)inliers / (double)candidates;

				final String model = entries[ modelEntry ].substring( entries[ modelEntry ].indexOf( "(" ) + 1, entries[ modelEntry ].indexOf( ")" )  );

				final String[] mXX = model.split( "," );
				final double[] m = new double[ 6 ];

				for ( int k = 0; k < m.length; ++k )
					m[ k ] = Double.parseDouble( mXX[ k ].trim() );

				align.t = new AffineTransform2D();
				align.t.set( m );

				/*
				if ( ( puckA == 0 && puckB == 2 ) || ( puckA == 0 && puckB == 5 ) || ( puckA == 0 && puckB == 7 )|| ( puckA == 0 && puckB == 8 )|| ( puckA == 0 && puckB == 9 )|| ( puckA == 0 && puckB == 10 )|| ( puckA == 0 && puckB == 11 ) ||
					( puckA == 1 && puckB == 8 ) ||
					( puckA == 2 && puckB == 4 ) || ( puckA == 2 && puckB == 7 ) || ( puckA == 2 && puckB == 10 )|| ( puckA == 2 && puckB == 11 ) || 
					( puckA == 4 && puckB == 5 ) || ( puckA == 4 && puckB == 7 ) || ( puckA == 4 && puckB == 11 ) ||
					( puckA == 5 && puckB == 7 ) || ( puckA == 5 && puckB == 8 ) || ( puckA == 5 && puckB == 9 ) || ( puckA == 5 && puckB == 10 ) || ( puckA == 5 && puckB == 11 ) ||
					( puckA == 6 && puckB == 7 ) || ( puckA == 6 && puckB == 11 ) || ( puckA == 6 && puckB == 12 ) ||
					( puckA == 7 && puckB == 9 ) || ( puckA == 7 && puckB == 10 ) ||
					( puckA == 8 && puckB == 10 ) || ( puckA == 8 && puckB == 12 ) ||  // vs 12 could be moved
					( puckA == 9 && puckB == 11 ) )*/
				if ( Math.abs( puckA - puckB ) > 2 )
				{
					align.quality = 0;
					align.t = new AffineTransform2D();
				}

				alignments.add( align );
			}

			in.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
			return null;
		}

		return alignments;
	}

	public static void main( String[] args ) throws IOException
	{
		final String path = Path.getPath();

		final String[] pucks = new String[] { "Puck_180602_20", "Puck_180602_18", "Puck_180602_17", "Puck_180602_16", "Puck_180602_15", "Puck_180531_23", "Puck_180531_22", "Puck_180531_19", "Puck_180531_18", "Puck_180531_17", "Puck_180531_13", "Puck_180528_22", "Puck_180528_20" };

		// load alignments
		final ArrayList< Alignment > alignments = loadPairwiseAlignments( new File( path + "/slide-seq", "alignments_sift_1" ).getAbsolutePath() );

		for ( final Alignment align : alignments )
			if ( align.i < pucks.length && align.j < pucks.length )
				System.out.println( align.i + "-" + align.j + ": " + align.t );

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
				final Alignment align = Alignment.getAlignment( alignments, i, j );

				quality[ i ][ j ] = quality[ j ][ i ] = align.quality;
				//quality[ i ][ j ] = quality[ j ][ i ] = 1.0 / GlobalOpt.centerOfMassDistance( puckData.get( i ), puckData.get( j ), new AffineTransform2D(), Alignment.getAlignment( alignments, i, j ).t );

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

				final List< PointMatch > inliers = loadMatches( path, i, j );
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
