package gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import data.STDataUtils;
import filter.FilterFactory;
import filter.MedianFilterFactory;
import imglib2.TransformedIterableRealInterval;
import net.imglib2.Interval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;
import render.Render;

public class RenderThread implements Runnable
{
	protected static int medianFilter = 0;
	protected static int gaussFactor = 1;

	protected static double minRange = 0;
	protected static double maxRange = 200;
	protected static double min = 0.1;
	protected static double max = 15;

	protected final BdvOptions options;
	protected BdvStackSource< ? > bdv = null;
	protected final Interval interval;
	protected final DoubleType outofbounds = new DoubleType( 0 );

	protected final List< STDataAssembly > slides;

	final Queue< Pair< String, Integer > > globalQueue = new ConcurrentLinkedQueue<>();

	public AtomicBoolean keepRunning = new AtomicBoolean( true );
	public AtomicBoolean isSleeping = new AtomicBoolean( false );

	public RenderThread( final List< STDataAssembly > slides )
	{
		this.slides = slides;
		this.interval =
				STDataUtils.getCommonIterableInterval(
						slides.stream().map(
								pair -> new TransformedIterableRealInterval<>( pair.data(), pair.transform() ) )
						.collect( Collectors.toList() ) );

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
				final STDataAssembly slide = slides.get( lastElement.getB() );

				System.out.println( "rendering gene: " + gene + " of slide: " + slide.data().toString() );

				final List< FilterFactory< DoubleType, DoubleType > > filterFactorys = new ArrayList<>();

				if ( medianFilter > 0 )
					filterFactorys.add( new MedianFilterFactory<>( new DoubleType( 0 ), medianFilter ) );

				final RealRandomAccessible< DoubleType > renderRRA = Render.getRealRandomAccessible( slide, gene, gaussFactor, filterFactorys );

				BdvStackSource< ? > old = bdv;

				bdv = BdvFunctions.show( renderRRA, interval, gene, options.addTo( old ) );
				bdv.setDisplayRange( min, Math.max( max, getMaxDisplayRange( old ) ) );
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

	public static double getMaxDisplayRange( BdvStackSource< ? > bdv )
	{
		if ( bdv == null || bdv.getConverterSetups().size() == 0 || bdv.getBdvHandle().getSetupAssignments().getMinMaxGroups().size() == 0 )
			return 0;
		
		return bdv.getBdvHandle().getSetupAssignments().getMinMaxGroup( 
				bdv.getConverterSetups().iterator().next() ).getMaxBoundedValue().getCurrentValue();
		/*
		final HashSet< MinMaxGroup > groups = new HashSet<>();
		final SetupAssignments sa = bdv.getBdvHandle().getSetupAssignments();
		for ( final ConverterSetup setup : bdv.getConverterSetups() )
			groups.add( sa.getMinMaxGroup( setup ) );

		for ( final MinMaxGroup group : groups )
		{
			group.getMinBoundedValue().setCurrentValue( min );
			group.getMaxBoundedValue().setCurrentValue( max );
		}
		*/
	}
}
