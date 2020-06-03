package render.gui;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import align.Pairwise;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import data.STData;
import data.STDataStatistics;
import filter.GaussianFilterFactory;
import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;
import render.Render;

public class RenderThread implements Runnable
{
	protected static double minRange = 0;
	protected static double maxRange = 100;
	protected static double min = 0.1;
	protected static double max = 6;

	protected final BdvOptions options;
	protected BdvStackSource< ? > bdv = null;
	protected final Interval interval;
	protected final DoubleType outofbounds = new DoubleType( 0 );

	protected final List< Pair< STData, STDataStatistics > > slides;

	final Queue< Pair< String, Integer > > globalQueue = new ConcurrentLinkedQueue<>();

	public AtomicBoolean keepRunning = new AtomicBoolean( true );
	public AtomicBoolean isSleeping = new AtomicBoolean( false );

	public RenderThread( final List< Pair< STData, STDataStatistics > > slides )
	{
		this.slides = slides;
		this.interval = Pairwise.getCommonInterval( slides.stream().map( pair -> pair.getA() ).collect( Collectors.toList() ) );
		this.options = BdvOptions.options().is2D().numRenderingThreads( Runtime.getRuntime().availableProcessors() );
		this.bdv = BdvFunctions.show( Views.extendZero( ArrayImgs.doubles( 1, 1 ) ), interval, "", options );
		bdv.setDisplayRange( min, max );
		bdv.setDisplayRangeBounds( minRange, maxRange );
		bdv.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.SINGLE );
	}

	@Override
	public void run()
	{
		do
		{
			try
			{
				Thread.interrupted();

				isSleeping.set( true );

				// avoid 
				synchronized ( this )
				{
					Thread.sleep( 2000 );
					isSleeping.set( false );
				}
			}
			catch (InterruptedException e)
			{
				isSleeping.set( false );
				Thread.interrupted();
			}

			Pair< String, Integer > lastElement = null;
			Pair< String, Integer > element;

			synchronized ( globalQueue )
			{
				do
				{
					element = globalQueue.poll();
					if ( element != null )
						lastElement = element;
				}
				while ( element != null );
			}

			if ( lastElement != null )
			{
				final String gene = lastElement.getA();
				final Pair< STData, STDataStatistics > slide = slides.get( lastElement.getB() );

				System.out.println( "rendering gene: " + gene + " of slide: " + slide.getA().toString() );

				IterableRealInterval< DoubleType > data = slide.getA().getExprData( gene );

				data = Converters.convert(
						data,
						new Converter< DoubleType, DoubleType >()
						{
							@Override
							public void convert( final DoubleType input, final DoubleType output )
							{
								output.set( input.get() + 0.1 );
							}
						},
						new DoubleType() );

				//final Pair< DoubleType, DoubleType > minmax = ImgLib2Util.minmax( data );
				//System.out.println( minmax.getA() + " " + minmax.getB() );

				// gauss crisp
				double gaussRenderSigma = slide.getB().getMedianDistance();
				double gaussRenderRadius = slide.getB().getMedianDistance() * 4;

				final RealRandomAccessible< DoubleType > renderRRA = Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) );

				BdvStackSource< ? > old = bdv;
				bdv = BdvFunctions.show( renderRRA, interval, gene, options.addTo( old ) );
				bdv.setDisplayRange( min, max );
				bdv.setDisplayRangeBounds( minRange, maxRange );
				bdv.setCurrent();
				old.removeFromBdv();
			}
			else
			{
				//System.out.println( "queue empty." );
			}
		}
		while ( keepRunning.get() );

		bdv.close();
	}
}
