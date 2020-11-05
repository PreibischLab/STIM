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
import imglib2.StackedIterableRealInterval;
import imglib2.TransformedIterableRealInterval;
import io.N5IO;
import io.Path;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.RealCursor;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccessible;
import net.imglib2.Sampler;
import net.imglib2.converter.Converters;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.type.numeric.real.DoubleType;
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



	public static void render3d( final ArrayList< STData > puckData, final ArrayList< STDataStatistics > puckDataStatistics, final ArrayList< AffineTransform2D > transforms )
	{
		final String gene = "Ubb";
		final ArrayList< IterableRealInterval< DoubleType > > slices = new ArrayList<>();

		for ( int i = 0; i < puckData.size(); ++i )
		{
			IterableRealInterval< DoubleType > data = 
					Converters.convert(
							puckData.get( i ).getExprData( gene ),
							(a,b) -> b.set( a.get() + 0.1 ),
							new DoubleType() );

			if ( transforms != null )
				data = new TransformedIterableRealInterval<>(
						data,
						transforms.get( i ) );

			slices.add( data );
		}

		final double spacing = puckDataStatistics.get( 0 ).getMedianDistance() * 2;
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
		render3d( puckData, puckDataStatistics, transforms );
	}
}
