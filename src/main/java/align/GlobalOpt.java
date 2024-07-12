package align;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import gui.STDataAssembly;
import io.SpatialDataContainer;

import data.STData;
import data.STDataUtils;
import ij.ImageJ;
import io.Path;
import io.TextFileAccess;
import mpicbg.models.ErrorStatistic;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.RigidModel2D;
import mpicbg.models.Tile;
import mpicbg.models.TileConfiguration;
import mpicbg.models.TileUtil;
import net.imglib2.Interval;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import org.apache.logging.log4j.Logger;
import util.LoggerUtil;
import util.Threads;

public class GlobalOpt
{
	private static final Logger logger = LoggerUtil.getLogger();

	public static double centerOfMassDistance( final STData dataA, final STData dataB, final AffineTransform2D transformA, final AffineTransform2D transformB )
	{
		final Interval intervalA = dataA.getRenderInterval();
		final Interval intervalB = dataB.getRenderInterval();

		final double[] centerA = new double[ intervalA.numDimensions() ];
		final double[] centerB = new double[ intervalA.numDimensions() ];

		for ( int d = 0; d < intervalA.numDimensions(); ++d )
		{
			centerA[ d ] = ( intervalA.max( d ) - intervalA.min( d ) ) / 2.0 + intervalA.min( d );
			centerB[ d ] = ( intervalB.max( d ) - intervalB.min( d ) ) / 2.0 + intervalB.min( d );
		}

		transformA.apply( centerA, centerA );
		transformB.apply( centerB, centerB );

		double dist = 0;

		for ( int d = 0; d < intervalA.numDimensions(); ++d )
			dist += Math.pow( centerA[ d ] - centerB[ d ], 2 );
		
		return Math.sqrt( dist );
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

	protected static ArrayList< Alignment > loadPairwiseAlignments( final String file, final boolean loadICP )
	{
		final BufferedReader in = TextFileAccess.openFileRead( file );

		final ArrayList< Alignment > alignments = new ArrayList<>();

		final int modelEntry = loadICP ? 6 : 5;

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
				final String model = entries[ modelEntry ].substring( entries[ modelEntry ].indexOf( "(" ) + 1, entries[ modelEntry ].indexOf( ")" )  );

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
			logger.error("Could not load pairwise alignments", e);
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

	public static void iterativeGlobalOpt(
			final TileConfiguration tc,
			final Collection< Pair< Tile< ? >, Tile< ? > > > removedInconsistentPairs,
			final double maxAllowedError,
			final int maxIterations,
			final int maxPlateauwidth,
			final double relativeThreshold,
			final double absoluteThreshold,
			final HashMap< ? extends Tile< ? >, Integer > tileToIndex,
			final double[][] quality,
			final int numThreads)
	{
		// now perform the global optimization
		boolean finished = false;

		while (!finished)
		{
			try 
			{
				int unaligned = tc.preAlign().size();
				if ( unaligned > 0 )
					logger.info("Pre-aligned all tiles but {}", unaligned);
				else
					logger.info( "Prealigned all tiles" );

				TileUtil.optimizeConcurrently(
						new ErrorStatistic( maxPlateauwidth + 1 ),  maxAllowedError, maxIterations, maxPlateauwidth, 1.0f,
						tc, tc.getTiles(), tc.getFixedTiles(), numThreads );

				logger.info("Global optimization of {}", tc.getTiles().size());
				logger.info("   Avg Error: {}px", tc.getError());
				logger.info("   Min Error: {}px", tc.getMinError());
				logger.info("   Max Error: {}px", tc.getMaxError());

				// give some time for the output
				try { Thread.sleep( 50 ); } catch ( Exception ignored) {}
			}
			catch (Exception e)
			{
				logger.error( "Global optimization failed, please report this bug: ", e );
				return;
			}

			finished = true;

			// re-do if errors are too big
			if ( !isConverged( tc, relativeThreshold, absoluteThreshold ) )
			{
				finished = false;

				// if we cannot remove any link, then we are finished too
				final Pair< Tile< ? >, Tile< ? > > removed = removeLink( tc, tileToIndex, quality );

				if ( removed == null )
					finished = true;
				else if ( removedInconsistentPairs != null )
					removedInconsistentPairs.add( removed );
			}
		}
	}

	public static boolean isConverged(
			final TileConfiguration tc,
			final double relativeThreshold,
			final double absoluteThreshold)
	{
		double avgErr = tc.getError();
		double maxErr = tc.getMaxError();

		// the minMaxError (0.75) makes sure that no links are dropped if the maximal error is already below a pixel
		return (!(avgErr * relativeThreshold < maxErr) || !(maxErr > 0.75)) && !(avgErr > absoluteThreshold);
	}

	public static Pair< Tile< ? >, Tile< ? > > removeLink(
			final TileConfiguration tc,
			final HashMap< ? extends Tile< ? >, Integer > tileToIndex,
			final double[][] quality )
	{
		double worstInvScore = -Double.MAX_VALUE;
		Tile<?> worstTile1 = null;
		Tile<?> worstTile2 = null;
		
		for ( final Tile<?> t : tc.getTiles())
		{
			//System.out.println( "Inspecting group: " + findGroup( t, map ) );

			final int connected = t.getConnectedTiles().size();

			// we mustn't disconnect a tile entirely
			if ( connected <= 1 )
				continue;

			for ( final PointMatch pm : t.getMatches() )
			{
				final Tile<?> connectedTile = t.findConnectedTile( pm );

				// make sure that pm is not the only connection of the connected tile either 
				if ( connectedTile.getConnectedTiles().size() <= 1 )
					continue;

				double q = quality[ tileToIndex.get( t ) ][ tileToIndex.get( connectedTile ) ]; // between [0.00, 1.00]

				q = Math.min( 1.0, q );
				q = Math.max( 0.01, q );

				// TODO: QUALITY!!!
				final double invScore = Math.pow( 1.01 - q, 4 ) * Math.sqrt( pm.getDistance() );// * Math.log10( connected );

				//System.out.println( "invScore=" + invScore + " [dist=" + pm.getDistance() + ", quality=" + quality + ", connected=" + connected + "] to " + findGroup( t.findConnectedTile( pm ), map ) );

				if ( invScore > worstInvScore )
				{
					worstInvScore = invScore;

					worstTile1 = t;
					worstTile2 = connectedTile;

					//System.out.println( "NEW WORST: " + worstInvScore + " between " + findGroup( worstTile1, map ) + " and " + findGroup( worstTile2, map ) );
				}
			}
		}

		if (worstTile1 == null)
		{
			System.err.println( "WARNING: can not remove any more links without disconnecting components" );
			return null;
		}

		worstTile1.removeConnectedTile( worstTile2 );
		worstTile2.removeConnectedTile( worstTile1 );

		logger.info("Removed link from {} to {}", tileToIndex.get(worstTile1), tileToIndex.get(worstTile2));

		return new ValuePair<>( worstTile1, worstTile2 );
	}

	public static void main( String[] args ) throws IOException
	{
		new ImageJ();

		final String path = Path.getPath();

		final ArrayList< Alignment > alignments = loadPairwiseAlignments( new File( path + "/slide-seq", "alignments2" ).getAbsolutePath(), false );

		final ExecutorService service = Executors.newFixedThreadPool(8);
		final SpatialDataContainer container = SpatialDataContainer.openForReading(path + "slide-seq-normalized-gzip3.n5", service);
		final List<String> pucks = container.getDatasets();

		final List<STDataAssembly> puckData = container.openAllDatasets().stream()
				.map(sdio -> {try {return sdio.readData();} catch (IOException e) {throw new RuntimeException(e);}
				}).collect(Collectors.toList());

		for ( final Alignment align : alignments )
			if ( align.i < pucks.size() && align.j < pucks.size() )
				logger.info("{}-{}: {}", align.i, align.j, align.t);

		final List< Pair< STData, AffineTransform2D > > initialdata = new ArrayList<>();

		for (STDataAssembly puckDatum : puckData) {
			initialdata.add(new ValuePair<>(puckDatum.data(), new AffineTransform2D()));
		}

		AlignTools.visualizeList( initialdata );
		//visualizePair( puckData.get( debugA ), puckData.get( debugB ), new AffineTransform2D(), Alignment.getAlignment( alignments, debugA, debugB ).t );
		SimpleMultiThreading.threadHaltUnClean();

		final Interval interval = STDataUtils.getCommonInterval(puckData.stream().map(STDataAssembly::data).collect(Collectors.toList()));

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
				final Alignment align = Alignment.getAlignment( alignments, i, j );

				quality[i][j] = quality[j][i] = 1.0 / centerOfMassDistance(puckData.get(i).data(), puckData.get(j).data(), new AffineTransform2D(), Alignment.getAlignment(alignments, i, j).t);

				maxQuality = Math.max( maxQuality, quality[ i ][ j ] );
				minQuality = Math.min( minQuality, quality[ i ][ j ] );

				logger.debug("Connecting {}-{}", i, j);

				final STData stDataA = puckData.get(i).data();
				final STData stDataB = puckData.get(j).data();

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

				final AffineGet tA = new AffineTransform2D();
				final AffineGet tB = align.t;
				
				final List< PointMatch > pms = createFakeMatches(interval, tA, tB, 10 );
				tileA.connect( tileB, pms );
			}
		}

		logger.debug("minQ: {}", minQuality);
		logger.debug("maxQ: {}", maxQuality);

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

		logger.debug("{} / {}", dataToTile.keySet().size(), pucks.size());
		logger.debug("{} / {}", tileToData.keySet().size(), pucks.size());

		//System.exit( 0 );

		for ( int i = 0; i < pucks.size(); ++i )
			logger.debug("{}: {}", puckData.get(i), dataToTile.get(puckData.get(i)));

		final TileConfiguration tileConfig = new TileConfiguration();

		tileConfig.addTiles( new HashSet<>( dataToTile.values() ) );
		tileConfig.fixTile( dataToTile.get( puckData.get( 0 ) ) );

		final ArrayList< Pair< Tile< ? >, Tile< ? > > > removedInconsistentPairs = new ArrayList<>();
		
		iterativeGlobalOpt(
				tileConfig,
				removedInconsistentPairs,
				30,
				500,
				500,
				3.0,
				50,
				tileToIndex,
				quality,
				Threads.numThreads());

		for ( final Pair< Tile< ? >, Tile< ? > > removed : removedInconsistentPairs )
			logger.info("Removed {} to {} ({} to {})",
						tileToIndex.get(removed.getA()), tileToIndex.get(removed.getB()), tileToData.get(removed.getA()), tileToData.get(removed.getB()));

		final List< Pair< STData, AffineTransform2D > > data = new ArrayList<>();

		for ( int i = 0; i < pucks.size(); ++i )
		{
			logger.debug("{}: {}", puckData.get(i), dataToTile.get(puckData.get(i)).getModel());

			final RigidModel2D model = dataToTile.get( puckData.get( i ) ).getModel();

			data.add(new ValuePair<>(puckData.get(i).data(), AlignTools.modelToAffineTransform2D(model)));
		}

		AlignTools.visualizeList( data );
		service.shutdown();
	}
}
