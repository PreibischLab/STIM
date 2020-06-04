package imglib2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import data.STData;
import data.STDataImgLib2;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
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
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import util.Threads;

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
	
		// create the gene lookup
		final HashMap< String, Integer > geneLookup = new HashMap<>();

		for ( final String geneName : geneNames )
			geneLookup.put( geneName, data.getIndexForGene( geneName ) );

		return new STDataImgLib2( locations, exprValues, geneNames, geneLookup );
	}

	public static Interval transformInterval( final Interval interval, final AffineGet affine )
	{
		if ( affine == null )
			return interval;

		final double[] min = new double[ interval.numDimensions() ];
		final double[] max = new double[ interval.numDimensions() ];

		for ( int d = 0; d < min.length; ++d )
		{
			min[ d ] = interval.min( d );
			max[ d ] = interval.max( d );
		}

		affine.apply( min, min );
		affine.apply( max, max );

		final long[] minL = new long[ min.length ];
		final long[] maxL = new long[ max.length ];

		for ( int d = 0; d < min.length; ++d )
		{
			minL[ d ] = Math.round( Math.floor( min[ d ] ) );
			maxL[ d ] = Math.round( Math.ceil( max[ d ] ) );
		}

		return new FinalInterval( minL, maxL );
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
		final Vector< ImagePortion > portions = divideIntoPortions( numPixels );
		final ArrayList< Callable< Void > > tasks = new ArrayList< Callable< Void > >();

		for ( final ImagePortion portion : portions )
		{
			tasks.add( new Callable< Void >()
			{
				@Override
				public Void call() throws Exception
				{
					copyImg( portion.getStartPosition(), portion.getLoopSize(), input, output );
					return null;
				}
			});
		}

		execTasks( tasks, service, "copy image" );
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
	public static final void execTasks( final ArrayList< Callable< Void > > tasks, final ExecutorService taskExecutor, final String jobDescription )
	{
		try
		{
			// invokeAll() returns when all tasks are complete
			taskExecutor.invokeAll( tasks );
		}
		catch ( final InterruptedException e )
		{
			System.out.println( "Failed to " + jobDescription + ": " + e );
			e.printStackTrace();
			return;
		}
	}

	public static class ImagePortion
	{
		public ImagePortion( final long startPosition, final long loopSize )
		{
			this.startPosition = startPosition;
			this.loopSize = loopSize;
		}
		
		public long getStartPosition() { return startPosition; }
		public long getLoopSize() { return loopSize; }
		
		protected long startPosition;
		protected long loopSize;
		
		@Override
		public String toString() { return "Portion [" + getStartPosition() + " ... " + ( getStartPosition() + getLoopSize() - 1 ) + " ]"; }
	}

	public static final Vector<ImagePortion> divideIntoPortions( final long imageSize )
	{
		int numPortions;

		if ( imageSize <= Threads.numThreads() )
			numPortions = (int)imageSize;
		else
			numPortions = Math.max( Threads.numThreads(), (int)( imageSize / ( 64l*64l*64l ) ) );

		//System.out.println( "nPortions for copy:" + numPortions );

		final Vector<ImagePortion> portions = new Vector<ImagePortion>();

		if ( imageSize == 0 )
			return portions;

		long threadChunkSize = imageSize / numPortions;

		while ( threadChunkSize == 0 )
		{
			--numPortions;
			threadChunkSize = imageSize / numPortions;
		}

		long threadChunkMod = imageSize % numPortions;

		for ( int portionID = 0; portionID < numPortions; ++portionID )
		{
			// move to the starting position of the current thread
			final long startPosition = portionID * threadChunkSize;

			// the last thread may has to run longer if the number of pixels cannot be divided by the number of threads
			final long loopSize;
			if ( portionID == numPortions - 1 )
				loopSize = threadChunkSize + threadChunkMod;
			else
				loopSize = threadChunkSize;
			
			portions.add( new ImagePortion( startPosition, loopSize ) );
		}
		
		return portions;
	}

}
