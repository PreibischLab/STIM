package tools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.janelia.saalfeldlab.n5.N5FSReader;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import data.STData;
import data.STDataStatistics;
import data.STDataUtils;
import filter.GaussianFilterFactory;
import filter.GaussianFilterFactory.WeightType;
import io.N5IO;
import io.Path;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.RealCursor;
import net.imglib2.RealRandomAccessible;
import net.imglib2.Sampler;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;
import render.Render;

public class VisualizeStack
{
	protected static double minRange = 0;
	protected static double maxRange = 100;
	protected static double min = 0.1;
	protected static double max = 16;

	public static void render2d( final ArrayList< STData > puckData, final ArrayList< STDataStatistics > puckDataStatistics )
	{
		final int visualize = 5;
		final String gene = "Ubb";
		final DoubleType outofbounds = new DoubleType( 0 );

		final IterableRealInterval< DoubleType > data =
				Converters.convert(
						puckData.get( visualize ).getExprData( gene ),
						(a,b) -> b.set( a.get() + 0.1 ),
						new DoubleType() );

		// gauss crisp
		double gaussRenderSigma = puckDataStatistics.get( visualize ).getMedianDistance();
		double gaussRenderRadius = puckDataStatistics.get( visualize ).getMedianDistance() * 4;

		final RealRandomAccessible< DoubleType > renderRRA = Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, WeightType.NONE ) );

		final Interval interval = STDataUtils.getCommonInterval( puckData );
		final BdvOptions options = BdvOptions.options().is2D().numRenderingThreads( Runtime.getRuntime().availableProcessors() );

		final BdvStackSource<?> bdv = BdvFunctions.show( renderRRA, interval, gene, options/*.addTo( old )*/ );
		bdv.setDisplayRange( min, max );
		bdv.setDisplayRangeBounds( minRange, maxRange );
		bdv.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.SINGLE );
		bdv.setCurrent();
	}

	public static class StackedIterableRealInterval< T > implements IterableRealInterval< T >
	{
		final List< IterableRealInterval< T > > slices;
		final int n;
		final double[] min, max;
		final long size;
		final double spacing;

		public StackedIterableRealInterval( final List< IterableRealInterval< T > > slices, final double spacing )
		{
			this.slices = slices;
			this.n = slices.get( 0 ).numDimensions() + 1;
			this.min = new double[ n ];
			this.max = new double[ n ];
			this.spacing = spacing;

			for ( int d = 0; d < n - 1; ++d )
			{
				min[ d ] = slices.get( 0 ).realMin( d );
				max[ d ] = slices.get( 0 ).realMax( d );
			}

			long sizeTmp = 0;

			for ( final IterableRealInterval< T > slice : slices )
			{
				for ( int d = 0; d < n - 1; ++d )
				{
					min[ d ] = Math.min( min[ d ], slice.realMin( d ) );
					max[ d ] = Math.max( max[ d ], slice.realMax( d ) );
				}

				if ( slice.size() == 0 )
					throw new RuntimeException( "Empty IterableRealIntervals not supported." );

				if ( slice.numDimensions() != slices.get( 0 ).numDimensions() )
					throw new RuntimeException( "Varying dimensionality of IterableRealIntervals not supported." );

				sizeTmp += slice.size();
			}

			this.size = sizeTmp;

			min[ n - 1 ] = 0;
			max[ n - 1 ] = ( slices.size() - 1 ) * spacing;
		}

		@Override
		public double realMin( final int d ) { return min[ d ]; }

		@Override
		public double realMax( final int d) { return max[ d ]; }

		@Override
		public int numDimensions() { return n; }

		@Override
		public Iterator<T> iterator() { return localizingCursor(); }

		@Override
		public RealCursor<T> cursor() { return localizingCursor(); }

		@Override
		public RealCursor<T> localizingCursor() { return new StackedIterableRealIntervalRealCursor<>( slices, spacing, n ); }

		@Override
		public long size() { return size; }

		@Override
		public T firstElement() { return slices.get( 0 ).firstElement(); }

		@Override
		public Object iterationOrder() { return this; }
		
	}

	public static class StackedIterableRealIntervalRealCursor< T > implements RealCursor< T >
	{
		final List< IterableRealInterval< T > > slices;
		final List< RealCursor< T > > cursors;
		final int n, lastPos;
		final double spacing;

		RealCursor< T > currentCursor;
		int pos = -1;

		public StackedIterableRealIntervalRealCursor(
				final List< IterableRealInterval< T > > slices,
				final double spacing,
				final int n )
		{
			this.slices = slices;
			this.cursors = new ArrayList<>();

			for ( final IterableRealInterval< T > slice : slices )
				this.cursors.add( slice.localizingCursor() );

			this.currentCursor = null;
			this.spacing = spacing;
			this.n = n;
			this.lastPos = cursors.size() - 1;
		}

		@Override
		public double getDoublePosition( final int d )
		{
			if ( d == n - 1)
				return pos * spacing;
			else
				return currentCursor.getDoublePosition( d );
		}

		@Override
		public int numDimensions() { return n; }

		@Override
		public T get()
		{
			return currentCursor.get();
		}

		@Override
		public void jumpFwd( final long steps )
		{
			for ( long s = 0; s < steps; ++s )
				fwd();
		}

		@Override
		public void fwd()
		{
			if ( currentCursor == null || !currentCursor.hasNext() )
			{
				++pos;
				currentCursor = cursors.get( pos );
			}

			currentCursor.fwd();
		}

		@Override
		public void reset()
		{
			for ( final RealCursor< T > cursor : cursors )
				cursor.reset();
			pos = -1;
		}

		@Override
		public boolean hasNext()
		{
			return pos < lastPos || currentCursor.hasNext();
		}

		@Override
		public T next()
		{
			if ( currentCursor == null || !currentCursor.hasNext() )
			{
				++pos;
				currentCursor = cursors.get( pos );
			}

			return currentCursor.next();
		}

		@Override
		public Sampler<T> copy() { return copyCursor(); }

		@Override
		public RealCursor<T> copyCursor() { return new StackedIterableRealIntervalRealCursor< T >( slices, spacing, n ); }
	}

	public static void render3d( final ArrayList< STData > puckData, final ArrayList< STDataStatistics > puckDataStatistics )
	{
		final String gene = "Ubb";
		final ArrayList< IterableRealInterval< DoubleType > > slices = new ArrayList<>();

		for ( int i = 0; i < puckData.size(); ++i )
		{
			slices.add(
					Converters.convert(
							puckData.get( i ).getExprData( gene ),
							(a,b) -> b.set( a.get() + 0.1 ),
							new DoubleType() ) );
		}

		final double spacing = puckDataStatistics.get( 0 ).getMedianDistance();
		final StackedIterableRealInterval< DoubleType > stack = new StackedIterableRealInterval<>( slices, spacing );

		final DoubleType outofbounds = new DoubleType( 0 );

		// gauss crisp
		double gaussRenderSigma = puckDataStatistics.get( 0 ).getMedianDistance();
		double gaussRenderRadius = puckDataStatistics.get( 0 ).getMedianDistance() * 4;

		final RealRandomAccessible< DoubleType > renderRRA = Render.render( stack, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, WeightType.NONE ) );

		final Interval interval2d = STDataUtils.getCommonInterval( puckData );
		final long[] minI = new long[] { interval2d.min( 0 ), interval2d.min( 1 ), 0 };
		final long[] maxI = new long[] { interval2d.max( 0 ), interval2d.max( 1 ), Math.round( ( puckData.size() ) * spacing ) };
		final Interval interval = new FinalInterval( minI, maxI );
		final BdvOptions options = BdvOptions.options().numRenderingThreads( Runtime.getRuntime().availableProcessors() );

		final BdvStackSource<?> bdv = BdvFunctions.show( renderRRA, interval, gene, options/*.addTo( old )*/ );
		bdv.setDisplayRange( min, max );
		bdv.setDisplayRangeBounds( minRange, maxRange );
		bdv.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.SINGLE );
		bdv.setCurrent();

		/*
		final RealCursor< DoubleType > c = stack.localizingCursor();

		System.out.println( stack.size() );

		long i = 0;
		while ( c.hasNext() )
		{
			c.next();
			++i;
		}

		System.out.println( i );
		*/
	}

	public static void main( String[] args ) throws IOException
	{
		final String path = Path.getPath();
		final File n5Path = new File( path + "slide-seq-normalized.n5" );
		final N5FSReader n5 = N5IO.openN5( n5Path );
		final List< String > pucks = N5IO.listAllDatasets( n5 );

		final ArrayList< STData > puckData = new ArrayList<>();
		final ArrayList< STDataStatistics > puckDataStatistics = new ArrayList<>();
		final ArrayList< AffineTransform2D > transforms = new ArrayList<>();

		for ( final String puck : pucks )
		{
			puckData.add( N5IO.readN5( n5, puck ) );
			puckDataStatistics.add( new STDataStatistics( puckData.get( puckData.size() - 1) ) );

			final AffineTransform2D t = new AffineTransform2D();
			t.set( n5.getAttribute( n5.groupPath( puck ), "transform", double[].class ) );
			transforms.add( t );
		}

		// 2d
		//render2d( puckData, puckDataStatistics );

		// 3d
		render3d( puckData, puckDataStatistics );
	}
}
