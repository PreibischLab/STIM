package align;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.netlib.util.doubleW;

import data.STData;
import data.STDataStatistics;
import data.STDataUtils;
import ij.ImageJ;
import imglib2.ImgLib2Util;
import io.N5IO;
import io.Path;
import io.TextFileAccess;
import mpicbg.models.AffineModel2D;
import mpicbg.models.ErrorStatistic;
import mpicbg.models.Model;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.RigidModel2D;
import mpicbg.models.Tile;
import mpicbg.models.TileConfiguration;
import mpicbg.models.TileUtil;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Interval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.preibisch.mvrecon.process.interestpointregistration.global.GlobalOptIterative;
import net.preibisch.mvrecon.process.interestpointregistration.global.convergence.IterativeConvergenceStrategy;
import net.preibisch.mvrecon.process.interestpointregistration.global.linkremoval.LinkRemovalStrategy;
import net.preibisch.mvrecon.process.interestpointregistration.global.pointmatchcreating.PointMatchCreator;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class GlobalOpt
{
	public static void visualizePair( final STData stDataA, final STData stDataB, final AffineTransform2D transformA, final AffineTransform2D transformB )
	{
		//final AffineTransform2D pcmTransform = new AffineTransform2D();
		//pcmTransform.set( 0.43837114678907746, -0.8987940462991671, 5283.362652306015, 0.8987940462991671, 0.43837114678907746, -770.4745037840293 );

		final Interval interval = STDataUtils.getCommonInterval( stDataA, stDataB );

		// visualize result using the global transform
		final AffineTransform2D tS = new AffineTransform2D();
		tS.scale( 0.1 );

		final AffineTransform2D tA = transformA.copy();
		tA.preConcatenate( tS );

		final AffineTransform2D tB = transformB.copy();
		tB.preConcatenate( tS );

		final Interval finalInterval = Intervals.expand( ImgLib2Util.transformInterval( interval, tA ), 100 );

		new ImageJ();

		ImageJFunctions.show( Pairwise.display( stDataA, new STDataStatistics( stDataA ), "Calm1", finalInterval, tA ) ).setTitle( stDataA.toString() );
		ImageJFunctions.show( Pairwise.display( stDataB, new STDataStatistics( stDataB ), "Calm1", finalInterval, tB ) ).setTitle( stDataB.toString() );
	}

	static class Alignment
	{
		int i,j;
		double quality;
		AffineTransform2D t;

		public static Alignment getAlignment( final Collection< ? extends Alignment > aligns, final int i, final int j )
		{
			for ( final Alignment a : aligns )
				if ( a.i == i && a.j == j || a.i == j && a.j == i )
					return a;

			return null;
		}
	}

	protected static ArrayList< Alignment > loadPairwiseAlignments( final String file )
	{
		final BufferedReader in = TextFileAccess.openFileRead( file );

		final ArrayList< Alignment > alignments = new ArrayList<>();

		try
		{
			while( in.ready() )
			{
				final Alignment align = new Alignment();

				// 0	1	1	40	0.2854247105120125	2d-affine: (-0.9781476007338058, -0.20791169081775931, 6497.904068613771, 0.20791169081775931, -0.9781476007338058, 5337.402667427485)
				final String[] entries = in.readLine().trim().split( "\t" );
				align.i = Integer.parseInt( entries[ 0 ] );
				align.j = Integer.parseInt( entries[ 1 ] );
				align.quality = Double.parseDouble( entries[ 4 ] );
				final String model = entries[ 5 ].substring( entries[ 5 ].indexOf( "(" ) + 1, entries[ 5 ].indexOf( ")" )  );

				final String[] mXX = model.split( "," );
				final double[] m = new double[ 6 ];

				for ( int k = 0; k < m.length; ++k )
					m[ k ] = Double.parseDouble( mXX[ k ].trim() );

				align.t = new AffineTransform2D();
				align.t.set( m );
				
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

	protected static List< PointMatch > createFakeMatches( final Interval interval, final AffineGet pModel, final AffineGet qModel, final int samplesPerDimension )
	{
		final int w = (int)interval.dimension( 0 );
		final int h = (int)interval.dimension( 1 );

		final List< PointMatch > matches = new ArrayList<>();
		
		final double sampleWidth = (w - 1.0) / (samplesPerDimension - 1.0);
		final double sampleHeight = (h - 1.0) / (samplesPerDimension - 1.0);

		for (int y = 0; y < samplesPerDimension; ++y)
		{
			final double sampleY = y * sampleHeight + interval.min( 1 );
			for (int x = 0; x < samplesPerDimension; ++x)
			{
				final double[] p = new double[] { x * sampleWidth + interval.min( 0 ), sampleY };
				final double[] q = new double[] { x * sampleWidth + interval.min( 0 ), sampleY };

				pModel.apply( p, p );
				qModel.apply( q, q );

				matches.add(new PointMatch( new Point(p), new Point(q) ));
			}
		}

		return matches;
	}

	public static void main( String[] args ) throws IOException
	{
		final int debugA = 0;
		final int debugB = 2;

		final String path = Path.getPath();

		final ArrayList< Alignment > alignments = loadPairwiseAlignments( new File( path + "/slide-seq", "alignments2" ).getAbsolutePath() );

		// the inverse transformations were stored, upsi
		for ( final Alignment al : alignments )
			al.t = al.t.inverse();

		final String[] pucks = new String[] { "Puck_180602_20", "Puck_180602_18", "Puck_180602_17", "Puck_180602_16", "Puck_180602_15", "Puck_180531_23", "Puck_180531_22", "Puck_180531_19", "Puck_180531_18", "Puck_180531_17", "Puck_180531_13", "Puck_180528_22", "Puck_180528_20" };

		for ( final Alignment align : alignments )
			if ( align.i < pucks.length && align.j < pucks.length )
				System.out.println( align.i + "-" + align.j + ": " + align.t );

		final ArrayList< STData > puckData = new ArrayList<STData>();
		for ( final String puck : pucks )
			puckData.add( N5IO.readN5( new File( path + "slide-seq/" + puck + "-normalized.n5" ) ) );

		visualizePair( puckData.get( debugA ), puckData.get( debugB ), new AffineTransform2D(), Alignment.getAlignment( alignments, debugA, debugB ).t );

		final Interval interval = STDataUtils.getCommonInterval( puckData );

		final HashMap< STData, Tile< RigidModel2D > > dataToTile = new HashMap<>();
		final HashMap< Tile< RigidModel2D >, STData > tileToData = new HashMap<>();

		for ( int i = 0; i < pucks.length - 1; ++i )
		{
			for ( int j = i + 1; j < pucks.length; ++j )
			{
				final Alignment align = Alignment.getAlignment( alignments, i, j );
				
				if ( align.quality < 0.6 )
					continue;

				System.out.println( "Connecting " + i + "-" + j );

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

				final AffineGet tA = new AffineTransform2D();
				final AffineGet tB = align.t;
				
				final List< PointMatch > pms = createFakeMatches(interval, tA, tB, 10 );
				tileA.connect( tileB, pms );
			}
		}

		System.out.println( dataToTile.keySet().size() + " / " + pucks.length );
		System.out.println( tileToData.keySet().size() + " / " + pucks.length );

		for ( int i = 0; i < pucks.length; ++i )
			System.out.println( puckData.get( i ) + ": " + dataToTile.get( puckData.get( i ) ) );

		final TileConfiguration tileConfig = new TileConfiguration();

		// new HashSet because all tiles link to their common group tile, which is therefore present more than once
		tileConfig.addTiles( new HashSet<>( dataToTile.values() ) );
		tileConfig.fixTile( dataToTile.get( puckData.get( 0 ) ) );

		/*
		 * final PointMatchCreator pmc,
			final IterativeConvergenceStrategy ics,
			final LinkRemovalStrategy lms,
			final Collection< Pair< Group< ViewId >, Group< ViewId > > > removedInconsistentPairs,
			final Collection< ViewId > fixedViews,
			final Collection< Group< ViewId > > groupsIn
		 */
		final PointMatchCreator pmc = new PointMatchCreator() {
			
			@Override
			public HashSet<ViewId> getAllViews() {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public <M extends Model<M>> void assignWeights(HashMap<ViewId, Tile<M>> tileMap, ArrayList<Group<ViewId>> groups,
					Collection<ViewId> fixedViews) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public <M extends Model<M>> void assignPointMatches(HashMap<ViewId, Tile<M>> tileMap,
					ArrayList<Group<ViewId>> groups, Collection<ViewId> fixedViews) {
				// TODO Auto-generated method stub
				
			}
		};
		GlobalOptIterative.compute( new RigidModel2D(), pmc, ics, lms, removedInconsistentPairs, fixedViews, groupsIn);
		try
		{
			tileConfig.preAlign();

			TileUtil.optimizeConcurrently(
				new ErrorStatistic( 500 + 1 ),
				30,
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
		
		for ( int i = 0; i < pucks.length; ++i )
		{
			System.out.println( puckData.get( i ) + ": " + dataToTile.get( puckData.get( i ) ).getModel() );
		}
		
		final RigidModel2D modelA = dataToTile.get( puckData.get( debugA ) ).getModel();
		final RigidModel2D modelB = dataToTile.get( puckData.get( debugB ) ).getModel();

		//  m00, m10, m01, m11, m02, m12
		final double[] dataA = new double[ 6 ];
		final double[] dataB = new double[ 6 ];
		
		modelA.toArray(dataA);
		modelB.toArray(dataB);
		
		final AffineTransform2D tA = new AffineTransform2D();
		final AffineTransform2D tB = new AffineTransform2D();

		tA.set( dataA[ 0 ], dataA[ 2 ], dataA[ 4 ], dataA[ 1 ], dataA[ 3 ], dataA[ 5 ] );
		tB.set( dataB[ 0 ], dataB[ 2 ], dataB[ 4 ], dataB[ 1 ], dataB[ 3 ], dataB[ 5 ] );

		visualizePair( puckData.get( debugA ), puckData.get( debugB ), tA.inverse(), tB.inverse() );
	}
}
