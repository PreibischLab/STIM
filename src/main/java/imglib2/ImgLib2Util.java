package imglib2;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import data.STData;
import data.STDataImgLib2;
import ij.ImagePlus;
import ij.io.Opener;
import ij.process.ImageProcessor;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.IterableRealInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealCursor;
import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealPointSampleList;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import util.Threads;
import util.Threads.ImagePortion;

public class ImgLib2Util
{
	public static STDataImgLib2 copy( final STData data )
	{
		// copy locations and exprValues
		final ExecutorService service = Threads.createFixedExecutorService();
		final CellImgFactory< DoubleType > factory = new CellImgFactory< DoubleType >( new DoubleType() );

		long time = System.currentTimeMillis();

		final RandomAccessibleInterval< DoubleType > exprValues = ImgLib2Util.copyImg( data.getAllExprValues(), factory, service );
		final RandomAccessibleInterval< DoubleType > locations = ImgLib2Util.copyImg( data.getLocations(), factory, service );

		System.out.println( "Copy took: " + ( System.currentTimeMillis() - time ) );

		service.shutdown();

		// copy the gene name list
		final ArrayList< String > geneNames = new ArrayList<>( data.getGeneNames() );

		// copy the barcode list
		final ArrayList< String > barcodes = new ArrayList<>( data.getBarcodes() );

		// create the gene lookup
		final HashMap< String, Integer > geneLookup = new HashMap<>();

		for ( final String geneName : geneNames )
			geneLookup.put( geneName, data.getIndexForGene( geneName ) );

		return new STDataImgLib2( locations, exprValues, geneNames, barcodes, geneLookup );
	}

	public static Interval transformInterval( final Interval interval, final AffineGet affine )
	{
		if ( affine == null )
			return interval;

		final RealInterval bounds;

		if ( interval.numDimensions() == 2 )
			bounds = estimateBounds2d( interval, affine );
		else if ( interval.numDimensions() == 3 )
			bounds = estimateBounds3d( interval, affine );
		else
			throw new RuntimeException( "dim=" + interval.numDimensions() + " not supported." );

		/*
		final double[] min = new double[ interval.numDimensions() ];
		final double[] max = new double[ interval.numDimensions() ];

		for ( int d = 0; d < min.length; ++d )
		{
			min[ d ] = interval.min( d );
			max[ d ] = interval.max( d );
		}

		affine.apply( min, min );
		affine.apply( max, max );
		*/

		final long[] minL = new long[ interval.numDimensions() ];
		final long[] maxL = new long[ interval.numDimensions() ];

		for ( int d = 0; d < minL.length; ++d )
		{
			minL[ d ] = Math.round( Math.floor( bounds.realMin( d ) /*min[ d ]*/ ) );
			maxL[ d ] = Math.round( Math.ceil( bounds.realMax( d ) /*max[ d ]*/ ) );
		}

		return new FinalInterval( minL, maxL );
	}

	public static FinalRealInterval estimateBounds3d( final RealInterval interval, final AffineGet affine )
	{
		if ( interval.numDimensions() != 3 )
			return null;

		final double[] min = new double[ interval.numDimensions() ];
		final double[] max = new double[ min.length ];
		final double[] rMin = new double[ min.length ];
		final double[] rMax = new double[ min.length ];
		min[ 0 ] = interval.realMin( 0 );
		min[ 1 ] = interval.realMin( 1 );
		min[ 2 ] = interval.realMin( 2 );
		max[ 0 ] = interval.realMax( 0 );
		max[ 1 ] = interval.realMax( 1 );
		max[ 2 ] = interval.realMax( 2 );
		rMin[ 0 ] = rMin[ 1 ] = rMin[ 2 ] = Double.MAX_VALUE;
		rMax[ 0 ] = rMax[ 1 ] = rMax[ 2 ] = -Double.MAX_VALUE;
		for ( int d = 3; d < rMin.length; ++d )
		{
			rMin[ d ] = interval.realMin( d );
			rMax[ d ] = interval.realMax( d );
			min[ d ] = interval.realMin( d );
			max[ d ] = interval.realMax( d );
		}

		final double[] f = new double[ 3 ];
		final double[] g = new double[ 3 ];

		affine.apply( min, g );
		Util.min( rMin, g );
		Util.max( rMax, g );

		f[ 0 ] = max[ 0 ];
		f[ 1 ] = min[ 1 ];
		f[ 2 ] = min[ 2 ];
		affine.apply( f, g );
		Util.min( rMin, g );
		Util.max( rMax, g );

		f[ 0 ] = min[ 0 ];
		f[ 1 ] = max[ 1 ];
		f[ 2 ] = min[ 2 ];
		affine.apply( f, g );
		Util.min( rMin, g );
		Util.max( rMax, g );

		f[ 0 ] = max[ 0 ];
		f[ 1 ] = max[ 1 ];
		f[ 2 ] = min[ 2 ];
		affine.apply( f, g );
		Util.min( rMin, g );
		Util.max( rMax, g );

		f[ 0 ] = min[ 0 ];
		f[ 1 ] = min[ 1 ];
		f[ 2 ] = max[ 2 ];
		affine.apply( f, g );
		Util.min( rMin, g );
		Util.max( rMax, g );

		f[ 0 ] = max[ 0 ];
		f[ 1 ] = min[ 1 ];
		f[ 2 ] = max[ 2 ];
		affine.apply( f, g );
		Util.min( rMin, g );
		Util.max( rMax, g );

		f[ 0 ] = min[ 0 ];
		f[ 1 ] = max[ 1 ];
		f[ 2 ] = max[ 2 ];
		affine.apply( f, g );
		Util.min( rMin, g );
		Util.max( rMax, g );

		f[ 0 ] = max[ 0 ];
		f[ 1 ] = max[ 1 ];
		f[ 2 ] = max[ 2 ];
		affine.apply( f, g );
		Util.min( rMin, g );
		Util.max( rMax, g );

		return new FinalRealInterval( rMin, rMax );
	}

	public static FinalRealInterval estimateBounds2d( final RealInterval interval, final AffineGet affine )
	{
		if ( interval.numDimensions() != 2 )
			return null;

		final double[] min = new double[ interval.numDimensions() ];
		final double[] max = new double[ min.length ];
		final double[] rMin = new double[ min.length ];
		final double[] rMax = new double[ min.length ];
		min[ 0 ] = interval.realMin( 0 );
		min[ 1 ] = interval.realMin( 1 );
		max[ 0 ] = interval.realMax( 0 );
		max[ 1 ] = interval.realMax( 1 );
		rMin[ 0 ] = rMin[ 1 ] = Double.MAX_VALUE;
		rMax[ 0 ] = rMax[ 1 ] = -Double.MAX_VALUE;
		for ( int d = 2; d < rMin.length; ++d )
		{
			rMin[ d ] = interval.realMin( d );
			rMax[ d ] = interval.realMax( d );
			min[ d ] = interval.realMin( d );
			max[ d ] = interval.realMax( d );
		}

		final double[] f = new double[ 3 ];
		final double[] g = new double[ 3 ];

		affine.apply( min, g );
		Util.min( rMin, g );
		Util.max( rMax, g );

		f[ 0 ] = max[ 0 ];
		f[ 1 ] = min[ 1 ];
		affine.apply( f, g );
		Util.min( rMin, g );
		Util.max( rMax, g );

		f[ 0 ] = min[ 0 ];
		f[ 1 ] = max[ 1 ];
		affine.apply( f, g );
		Util.min( rMin, g );
		Util.max( rMax, g );

		f[ 0 ] = max[ 0 ];
		f[ 1 ] = max[ 1 ];
		affine.apply( f, g );
		Util.min( rMin, g );
		Util.max( rMax, g );

		return new FinalRealInterval( rMin, rMax );
	}
	public static < T extends Comparable< T > & Type< T > > Pair< T, T > minmax( final Iterable< T > img )
	{
		final T min = img.iterator().next().copy();
		final T max = min.copy();

		for ( final T value : img )
		{
			if ( value.compareTo( min ) < 0 )
				min.set( value );

			if ( value.compareTo( max ) > 0 )
				max.set( value );
		}

		return new ValuePair< T, T >( min, max );
	}

	public static String printRealInterval( final RealInterval interval )
	{
		String out = "(Interval empty)";

		if ( interval == null || interval.numDimensions() == 0 )
			return out;

		out = "[" + interval.realMin( 0 );

		for ( int i = 1; i < interval.numDimensions(); i++ )
			out += ", " + interval.realMin( i );

		out += "] -> [" + interval.realMax( 0 );

		for ( int i = 1; i < interval.numDimensions(); i++ )
			out += ", " + interval.realMax( i );

		out += "], size (" + (interval.realMax( 0 ) - interval.realMin( 0 ));

		for ( int i = 1; i < interval.numDimensions(); i++ )
			out += ", " + (interval.realMax( i ) - interval.realMin( i ));

		out += ")";

		return out;
	}

	public static long[] dimensions( final Interval ri )
	{
		final long[] dim = new long[ ri.numDimensions() ];
		ri.dimensions( dim );
		return dim;
	}

	public static long[] min( final Interval ri )
	{
		final long[] min = new long[ ri.numDimensions() ];
		ri.min( min );
		return min;
	}

	public static FinalInterval roundRealInterval( final RealInterval ri )
	{
		final long[] min = new long[ ri.numDimensions() ];
		final long[] max = new long[ ri.numDimensions() ];

		for ( int d = 0; d < ri.numDimensions(); ++d )
		{
			min[ d ] = Math.round( Math.floor( ri.realMin( d ) ) );
			max[ d ] = Math.round( Math.ceil( ri.realMax( d ) ) );
		}

		return new FinalInterval( min, max );
	}

	
	public static < T extends Type< T > > RealPointSampleList< T > copyIterableRealInterval(
			final IterableRealInterval< T > data )
	{
		final RealPointSampleList< T > list =
				new RealPointSampleList<>( data.numDimensions() );

		final RealCursor< T > cursor = data.localizingCursor();

		while ( cursor.hasNext() )
		{
			final T value = cursor.next();

			list.add( new RealPoint( cursor ), value.copy() );
		}

		return list;
	}

	public static < T extends Type< T > > RandomAccessibleInterval< T > copyImg(
			final RandomAccessibleInterval< T > input,
			final ImgFactory< T > factory,
			final ExecutorService service )
	{
		return translateIfNecessary( input, copyImgNoTranslation( input, factory, service ) );
	}

	public static < T extends Type< T > > Img< T > copyImgNoTranslation( final RandomAccessibleInterval< T > input, final ImgFactory< T > factory, final T type, final ExecutorService service )
	{
		return copyImgNoTranslation( input, factory, service );
	}

	public static < T extends Type< T > > Img< T > copyImgNoTranslation(
			final RandomAccessibleInterval< T > input,
			final ImgFactory< T > factory,
			final ExecutorService service )
	{
		final RandomAccessibleInterval< T > in;

		if ( Views.isZeroMin( input ) )
			in = input;
		else
			in = Views.zeroMin( input );

		final long[] dim = new long[ in.numDimensions() ];
		in.dimensions( dim );

		final Img< T > tImg = factory.create( dim );

		// copy the virtual construct into an actual image
		copyImg( in, tImg, service );

		return tImg;
	}

	public static < T > RandomAccessibleInterval< T > translateIfNecessary( final Interval original, final RandomAccessibleInterval< T > copy )
	{
		if ( Views.isZeroMin( original ) )
		{
			return copy;
		}
		else
		{
			final long[] min = new long[ original.numDimensions() ];
			original.min( min );

			return Views.translate( copy, min );
		}
	}

	public static < T extends Type< T > > void copyImg( final RandomAccessibleInterval< T > input, final RandomAccessibleInterval< T > output, final ExecutorService service )
	{
		final long numPixels = Views.iterable( input ).size();
		final Vector< ImagePortion > portions = Threads.divideIntoPortions( numPixels );
		final ArrayList< Callable< Void > > tasks = new ArrayList<>();

		for ( final ImagePortion portion : portions )
		{
			tasks.add(() -> {
				copyImg( portion.getStartPosition(), portion.getLoopSize(), input, output );
				return null;
			});
		}

		Threads.execTasks( tasks, service, "copy image" );
	}

	public static final < T extends Type< T > > void copyImg(
			final long start,
			final long loopSize,
			final RandomAccessibleInterval< T > source,
			final RandomAccessibleInterval< T > target )
	{
		final IterableInterval< T > sourceIterable = Views.iterable( source );
		final IterableInterval< T > targetIterable = Views.iterable( target );

		if ( sourceIterable.iterationOrder().equals( targetIterable.iterationOrder() ) )
		{
			final Cursor< T > cursorSource = sourceIterable.cursor();
			final Cursor< T > cursorTarget = targetIterable.cursor();
	
			cursorSource.jumpFwd( start );
			cursorTarget.jumpFwd( start );
	
			for ( long l = 0; l < loopSize; ++l )
				cursorTarget.next().set( cursorSource.next() );
		}
		else
		{
			final RandomAccess< T > raSource = source.randomAccess();
			final Cursor< T > cursorTarget = targetIterable.localizingCursor();

			cursorTarget.jumpFwd( start );

			for ( long l = 0; l < loopSize; ++l )
			{
				cursorTarget.fwd();
				raSource.setPosition( cursorTarget );

				cursorTarget.get().set( raSource.get() );
			}
		}
	}

	public static Img< FloatType > openAs32Bit( final File file )
	{
		return openAs32Bit( file, new ArrayImgFactory<>(new FloatType()) );
	}

	public static Img< FloatType > openAs32Bit( final File file, final ImgFactory< FloatType > factory )
	{
		if ( !file.exists() )
			throw new RuntimeException( "File '" + file.getAbsolutePath() + "' does not exist." );

		final ImagePlus imp = new Opener().openImage( file.getAbsolutePath() );

		if ( imp == null )
			throw new RuntimeException( "File '" + file.getAbsolutePath() + "' could not be opened." );

		final Img< FloatType > img;

		if ( imp.getStack().getSize() == 1 )
		{
			// 2d
			img = factory.create( new int[]{ imp.getWidth(), imp.getHeight() } );
			final ImageProcessor ip = imp.getProcessor();

			final Cursor< FloatType > c = img.localizingCursor();
			
			while ( c.hasNext() )
			{
				c.fwd();

				final int x = c.getIntPosition( 0 );
				final int y = c.getIntPosition( 1 );

				c.get().set( ip.getf( x, y ) );
			}
		}
		else
		{
			// >2d
			img = factory.create( new int[]{ imp.getWidth(), imp.getHeight(), imp.getStack().getSize() } );

			final Cursor< FloatType > c = img.localizingCursor();

			// for efficiency reasons
			final ArrayList< ImageProcessor > ips = new ArrayList<>();

			for ( int z = 0; z < imp.getStack().getSize(); ++z )
				ips.add( imp.getStack().getProcessor( z + 1 ) );

			while ( c.hasNext() )
			{
				c.fwd();

				final int x = c.getIntPosition( 0 );
				final int y = c.getIntPosition( 1 );
				final int z = c.getIntPosition( 2 );

				c.get().set( ips.get( z ).getf( x, y ) );
			}
		}

		return img;
	}
}
