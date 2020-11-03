package align;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.janelia.saalfeldlab.n5.N5FSReader;

import data.STData;
import data.STDataStatistics;
import data.STDataUtils;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import imglib2.ImgLib2Util;
import io.N5IO;
import io.Path;
import io.TextFileAccess;
import mpicbg.models.AbstractAffineModel2D;
import mpicbg.models.ErrorStatistic;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.RigidModel2D;
import mpicbg.models.Tile;
import mpicbg.models.TileConfiguration;
import mpicbg.models.TileUtil;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.RealFloatConverter;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class GlobalOpt
{
	public static ImagePlus visualizeList( final List< Pair< STData, AffineTransform2D > > data )
	{
		// visualize result using the global transform
		final AffineTransform2D tS = new AffineTransform2D();
		tS.scale( 0.05 );

		final Interval interval = STDataUtils.getCommonInterval( data.stream().map( entry -> entry.getA() ).collect( Collectors.toList() ) );
		final Interval finalInterval = Intervals.expand( ImgLib2Util.transformInterval( interval, tS ), 100 );

		final ImageStack stack = new ImageStack( (int)finalInterval.dimension( 0 ), (int)finalInterval.dimension( 1 ) );

		for ( Pair< STData, AffineTransform2D > pair : data )
		{
			final AffineTransform2D tA = pair.getB().copy();
			tA.preConcatenate( tS );

			final RandomAccessibleInterval<DoubleType> vis = Pairwise.display( pair.getA(), new STDataStatistics( pair.getA() ), "Ubb", finalInterval, tA );

			stack.addSlice(pair.getA().toString(), ImageJFunctions.wrapFloat( vis, new RealFloatConverter<>(), pair.getA().toString(), null ).getProcessor());
		}

		ImagePlus imp = new ImagePlus("all", stack );
		imp.resetDisplayRange();
		imp.show();
		return imp;
	}

	public static ImagePlus visualizePair( final STData stDataA, final STData stDataB, final AffineTransform2D transformA, final AffineTransform2D transformB )
	{
		//final AffineTransform2D pcmTransform = new AffineTransform2D();
		//pcmTransform.set( 0.43837114678907746, -0.8987940462991671, 5283.362652306015, 0.8987940462991671, 0.43837114678907746, -770.4745037840293 );

		final Interval interval = STDataUtils.getCommonInterval( stDataA, stDataB );

		// visualize result using the global transform
		final AffineTransform2D tS = new AffineTransform2D();
		tS.scale( 0.05 );

		final AffineTransform2D tA = transformA.copy();
		tA.preConcatenate( tS );

		final AffineTransform2D tB = transformB.copy();
		tB.preConcatenate( tS );

		final Interval finalInterval = Intervals.expand( ImgLib2Util.transformInterval( interval, tS ), 100 );

		final ImageStack stack = new ImageStack( (int)finalInterval.dimension( 0 ), (int)finalInterval.dimension( 1 ) );

		final RandomAccessibleInterval<DoubleType> visA = Pairwise.display( stDataA, new STDataStatistics( stDataA ), "Calm1", finalInterval, tA );
		final RandomAccessibleInterval<DoubleType> visB = Pairwise.display( stDataB, new STDataStatistics( stDataB ), "Calm1", finalInterval, tB );

		stack.addSlice(stDataA.toString(), ImageJFunctions.wrapFloat( visA, new RealFloatConverter<>(), stDataA.toString(), null ).getProcessor());
		stack.addSlice(stDataB.toString(), ImageJFunctions.wrapFloat( visB, new RealFloatConverter<>(), stDataB.toString(), null ).getProcessor());

		ImagePlus imp = new ImagePlus("all", stack );
		imp.resetDisplayRange();
		imp.show();

		return imp;
	}

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

	public static void iterativeGlobalOpt(
			final TileConfiguration tc,
			final Collection< Pair< Tile< ? >, Tile< ? > > > removedInconsistentPairs,
			final double maxAllowedError,
			final int maxIterations,
			final int maxPlateauwidth,
			final double relativeThreshold,
			final double absoluteThreshold,
			final HashMap< Tile< RigidModel2D >, Integer > tileToIndex,
			final double[][] quality )
	{
		// now perform the global optimization
		boolean finished = false;

		while (!finished)
		{
			try 
			{
				int unaligned = tc.preAlign().size();
				if ( unaligned > 0 )
					System.out.println( "(" + new Date( System.currentTimeMillis() ) + "): pre-aligned all tiles but " + unaligned );
				else
					System.out.println( "(" + new Date( System.currentTimeMillis() ) + "): prealigned all tiles" );

				TileUtil.optimizeConcurrently(
						new ErrorStatistic( maxPlateauwidth + 1 ),  maxAllowedError, maxIterations, maxPlateauwidth, 1.0f,
						tc, tc.getTiles(), tc.getFixedTiles(), Runtime.getRuntime().availableProcessors());

				System.out.println( "(" + new Date( System.currentTimeMillis() ) + "): Global optimization of " + tc.getTiles().size());
				System.out.println( "(" + new Date( System.currentTimeMillis() ) + "):    Avg Error: " + tc.getError() + "px" );
				System.out.println( "(" + new Date( System.currentTimeMillis() ) + "):    Min Error: " + tc.getMinError() + "px" );
				System.out.println( "(" + new Date( System.currentTimeMillis() ) + "):    Max Error: " + tc.getMaxError() + "px" );

				// give some time for the output
				try { Thread.sleep( 50 ); } catch ( Exception e) {}
			}
			catch (Exception e)
			{
				System.out.println( "Global optimization failed, please report this bug: " + e );
				e.printStackTrace();
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
		if ( ( ( avgErr*relativeThreshold < maxErr && maxErr > 0.75 ) || avgErr > absoluteThreshold ) )
			return false;
		else
			return true;
	}

	public static Pair< Tile< ? >, Tile< ? > > removeLink(
			final TileConfiguration tc,
			final HashMap< Tile< RigidModel2D >, Integer > tileToIndex,
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

		System.out.println( new Date( System.currentTimeMillis() ) +  ": Removed link from " + tileToIndex.get( worstTile1 ) + " to " + tileToIndex.get( worstTile2 ) );

		return new ValuePair<>( worstTile1, worstTile2 );
	}

	public static AffineTransform2D modelToAffineTransform2D( final AbstractAffineModel2D< ? > model )
	{
		//  m00, m10, m01, m11, m02, m12
		final double[] array = new double[ 6 ];

		model.toArray(array);

		final AffineTransform2D t = new AffineTransform2D();
		t.set( array[ 0 ], array[ 2 ], array[ 4 ], array[ 1 ], array[ 3 ], array[ 5 ] );

		return t;
	}

	public static void main( String[] args ) throws IOException
	{
		new ImageJ();

		final int debugA = 0;
		int debugB = 4;

		final String path = Path.getPath();

		final ArrayList< Alignment > alignments = loadPairwiseAlignments( new File( path + "/slide-seq", "alignments2" ).getAbsolutePath(), false );

		// the inverse transformations were stored, upsi
		//for ( final Alignment al : alignments )
		//	al.t = al.t.inverse();

		//final String[] pucks = new String[] { "Puck_180602_20", "Puck_180602_18", "Puck_180602_17", "Puck_180602_16", "Puck_180602_15", "Puck_180531_23", "Puck_180531_22", "Puck_180531_19", "Puck_180531_18", "Puck_180531_17", "Puck_180531_13", "Puck_180528_22", "Puck_180528_20" };

		final N5FSReader n5 = N5IO.openN5( new File( path + "slide-seq-normalized-gzip3.n5" ) );
		final List< String > pucks = N5IO.listAllDatasets( n5 );

		final ArrayList< STData > puckData = new ArrayList<STData>();
		for ( final String puck : pucks )
			puckData.add( N5IO.readN5( n5, puck ) );

		for ( final Alignment align : alignments )
			if ( align.i < pucks.size() && align.j < pucks.size() )
				System.out.println( align.i + "-" + align.j + ": " + align.t );

		/*
i=0: 0=0.0 1=0.024038337709212782 2=0.24492487553469394 3=0.1518118833959881 4=0.7241020145787477 5=0.1306794304704525 6=0.15222925121638156 7=0.15483774054692553 8=0.132491429163626 9=0.17711581870553708 10=0.08701363466375361 11=0.2045784760110544 12=0.20891249466281947 
i=1: 0=0.024038337709212782 1=0.0 2=0.3330466142561688 3=0.11319724753860656 4=0.10022550806834965 5=0.20877708923578545 6=0.11832271709488193 7=0.07353720095912314 8=0.1713950715270506 9=0.09502724052349071 10=0.23944240649678283 11=0.11694349640166943 12=0.11195066945693335 
i=2: 0=0.24492487553469394 1=0.3330466142561688 2=0.0 3=0.24969917436088443 4=0.17472813760575273 5=0.9788951614909751 6=0.17426245380946803 7=0.18054806935102374 8=0.09074454325647692 9=0.09496693464673195 10=0.10267048518938668 11=0.1075661352374083 12=0.12349217922514331 
i=3: 0=0.1518118833959881 1=0.11319724753860656 2=0.24969917436088443 3=0.0 4=0.20776253762129704 5=0.3521493379456508 6=0.19462129769496608 7=0.03320550904928819 8=0.04381501333935987 9=0.02137252255928331 10=0.0 11=0.04980400301525422 12=0.004242569157942368 
i=4: 0=0.7241020145787477 1=0.10022550806834965 2=0.17472813760575273 3=0.20776253762129704 4=0.0 5=0.2760105640202167 6=0.5531206597483922 7=0.07913515105826611 8=0.15917311091873323 9=0.25045172666096027 10=0.16054736849735818 11=0.06656905561406037 12=0.0763166146516399 
i=5: 0=0.1306794304704525 1=0.20877708923578545 2=0.9788951614909751 3=0.3521493379456508 4=0.2760105640202167 5=0.0 6=0.6588552463927722 7=0.01900772579346503 8=0.1335874659752252 9=0.1277205212237738 10=0.08614429525971219 11=0.09772324217675286 12=0.01858521788859697 
i=6: 0=0.15222925121638156 1=0.11832271709488193 2=0.17426245380946803 3=0.19462129769496608 4=0.5531206597483922 5=0.6588552463927722 6=0.0 7=0.026002077904843646 8=0.1409801668617941 9=0.09114859275656546 10=0.31376868024657656 11=0.07260773747676567 12=4.418234511038576E-4 
i=7: 0=0.15483774054692553 1=0.07353720095912314 2=0.18054806935102374 3=0.03320550904928819 4=0.07913515105826611 5=0.01900772579346503 6=0.026002077904843646 7=0.0 8=0.2876741736681163 9=0.06181190408928276 10=0.10964337542752722 11=0.2132312584540062 12=0.0675333286312243 
i=8: 0=0.132491429163626 1=0.1713950715270506 2=0.09074454325647692 3=0.04381501333935987 4=0.15917311091873323 5=0.1335874659752252 6=0.1409801668617941 7=0.2876741736681163 8=0.0 9=1.0 10=0.15129672985645543 11=0.28507782241080326 12=0.4203623375451118 
i=9: 0=0.17711581870553708 1=0.09502724052349071 2=0.09496693464673195 3=0.02137252255928331 4=0.25045172666096027 5=0.1277205212237738 6=0.09114859275656546 7=0.06181190408928276 8=1.0 9=0.0 10=0.09688531350422797 11=0.35687450831352985 12=0.38860932836273004 
i=10: 0=0.08701363466375361 1=0.23944240649678283 2=0.10267048518938668 3=0.0 4=0.16054736849735818 5=0.08614429525971219 6=0.31376868024657656 7=0.10964337542752722 8=0.15129672985645543 9=0.09688531350422797 10=0.0 11=0.11920034539333373 12=0.2483245130454527 
i=11: 0=0.2045784760110544 1=0.11694349640166943 2=0.1075661352374083 3=0.04980400301525422 4=0.06656905561406037 5=0.09772324217675286 6=0.07260773747676567 7=0.2132312584540062 8=0.28507782241080326 9=0.35687450831352985 10=0.11920034539333373 11=0.0 12=0.48298694045995 
i=12: 0=0.20891249466281947 1=0.11195066945693335 2=0.12349217922514331 3=0.004242569157942368 4=0.0763166146516399 5=0.01858521788859697 6=4.418234511038576E-4 7=0.0675333286312243 8=0.4203623375451118 9=0.38860932836273004 10=0.2483245130454527 11=0.48298694045995 12=0.0 

c(i)=1: 0=302.8970299336632 1=0.0 2=1966.7125790780851 3=1127.5798466482315 4=1018.3643858073019 5=1244.0948936063103 6=1918.835171680812 7=1243.2553900055561 8=2550.2704505567276 9=250.05292046699788 10=2050.432852579864 11=755.2194098362946 12=1774.572922744189 

		 */

		final List< Pair< STData, AffineTransform2D > > initialdata = new ArrayList<>();

		for ( int i = 0; i < puckData.size(); ++i )
			initialdata.add( new ValuePair<>(puckData.get( i ), new AffineTransform2D()) );

//		initialdata.add( new ValuePair<>( puckData.get( debugA ), new AffineTransform2D() ) );
//		for ( debugB = debugA + 1; debugB < 11; ++debugB )
//			initialdata.add( new ValuePair<>(puckData.get( debugB ), Alignment.getAlignment( alignments, debugA, debugB ).t ) );

		visualizeList( initialdata );
		//visualizePair( puckData.get( debugA ), puckData.get( debugB ), new AffineTransform2D(), Alignment.getAlignment( alignments, debugA, debugB ).t );
		SimpleMultiThreading.threadHaltUnClean();

		final Interval interval = STDataUtils.getCommonInterval( puckData );

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

				//if ( align.quality < 0.6 )
				//	continue;

				//quality[ i ][ j ] = quality[ j ][ i ] = align.quality;
				
				quality[ i ][ j ] = quality[ j ][ i ] = 1.0 / centerOfMassDistance( puckData.get( i ), puckData.get( j ), new AffineTransform2D(), Alignment.getAlignment( alignments, i, j ).t );

				maxQuality = Math.max( maxQuality, quality[ i ][ j ] );
				minQuality = Math.min( minQuality, quality[ i ][ j ] );

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

				tileToIndex.putIfAbsent( tileA, i );
				tileToIndex.putIfAbsent( tileB, j );

				final AffineGet tA = new AffineTransform2D();
				final AffineGet tB = align.t;
				
				final List< PointMatch > pms = createFakeMatches(interval, tA, tB, 10 );
				tileA.connect( tileB, pms );
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
		
		iterativeGlobalOpt(
				tileConfig,
				removedInconsistentPairs,
				30,
				500,
				500,
				3.0,
				50,
				tileToIndex,
				quality );

		for ( final Pair< Tile< ? >, Tile< ? > > removed : removedInconsistentPairs )
			System.out.println( "Removed " + tileToIndex.get( removed.getA() ) + " to " + tileToIndex.get( removed.getB() ) + " (" + tileToData.get( removed.getA() ) + " to " + tileToData.get( removed.getB() ) + ")" );

		/*
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
		*/

		final List< Pair< STData, AffineTransform2D > > data = new ArrayList<>();

		for ( int i = 0; i < pucks.size(); ++i )
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
			data.add( new ValuePair<>( puckData.get( i ), modelToAffineTransform2D( model ) ) );
		}

		visualizeList( data );
		/*
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

		visualizePair( puckData.get( debugA ), puckData.get( debugB ), tA.inverse(), tB.inverse() );*/
	}
}
