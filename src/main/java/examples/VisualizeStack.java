package examples;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import javax.swing.ActionMap;
import javax.swing.InputMap;

import io.SpatialDataContainer;
import org.scijava.ui.behaviour.KeyStrokeAdder;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.io.InputTriggerDescription;
import org.scijava.ui.behaviour.util.AbstractNamedAction;

import align.AlignTools;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import cmd.InteractiveAlignment.AddedGene.Rendering;
import data.STData;
import data.STDataUtils;
import filter.FilterFactory;
import filter.GaussianFilterFactory;
import filter.SingleSpotRemovingFilterFactory;
import filter.GaussianFilterFactory.WeightType;
import filter.MedianFilterFactory;
import gui.STDataAssembly;
import ij.ImageJ;
import ij.ImagePlus;
import imglib2.StackedIterableRealInterval;
import imglib2.TransformedIterableRealInterval;
import io.Path;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import render.Render;
import tools.BDVFlyThrough;
import tools.BDVFlyThrough.CallbackBDV;

public class VisualizeStack
{
	protected static double minRange = 0;
	protected static double maxRange = 100;
	protected static double min = 0.1;
	protected static double max = 5.5;

	public static BdvStackSource<?> render2d( final STDataAssembly stdata )
	{
		final List< FilterFactory< DoubleType, DoubleType > > filterFactorys = new ArrayList<>();

		//filterFactorys.add( new MedianFilterFactory<>( new DoubleType( 0 ), 50.0 ) );
		//filterFactorys.add( new GaussianFilterFactory<>( new DoubleType( 0 ), 50.0, WeightType.BY_SUM_OF_WEIGHTS ) );
		//filterFactorys.add( new MeanFilterFactory<>( new DoubleType( 0 ), 50.0 ) );
		filterFactorys.add( new SingleSpotRemovingFilterFactory<>( new DoubleType( 0 ), stdata.statistics().getMedianDistance() * 1.5 ) );

		final String gene = "Calm2";
		final RealRandomAccessible< DoubleType > renderRRA = Render.getRealRandomAccessible( stdata, gene, 1.0, filterFactorys );

		final Interval interval =
				STDataUtils.getIterableInterval(
						new TransformedIterableRealInterval<>(
								stdata.data(),
								stdata.transform() ) );

		final BdvOptions options = BdvOptions.options().is2D().numRenderingThreads( Runtime.getRuntime().availableProcessors() / 2 );

		BdvStackSource<?> bdv = BdvFunctions.show( renderRRA, interval, gene, options );
		bdv.setDisplayRangeBounds( minRange, maxRange );
		bdv.setDisplayRange( min, max );
		//bdv.setColor( new ARGBType( ARGBType.rgba( 255, 0, 0, 0 ) ) );
		//bdv.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.SINGLE );
		//bdv.setCurrent();

		return bdv;
	}

	public static RandomAccessibleInterval< DoubleType > render3d_IJ( final List< STDataAssembly > stdata, final String gene, final double scale )
	{
		final DoubleType outofbounds = new DoubleType( 0 );

		final List< FilterFactory< DoubleType, DoubleType > > filterFactorys = new ArrayList<>();

		filterFactorys.add( new MedianFilterFactory<>( new DoubleType( 0 ), 20.0 ) );
		//filterFactorys.add( new GaussianFilterFactory<>( new DoubleType( 0 ), 50.0, WeightType.BY_SUM_OF_WEIGHTS ) );
		//filterFactorys.add( new MeanFilterFactory<>( new DoubleType( 0 ), 50.0 ) );
		filterFactorys.add( new SingleSpotRemovingFilterFactory<>( outofbounds, 30 ) );

		final Pair< RealRandomAccessible< DoubleType >, Interval > stack = createStack( stdata, gene, outofbounds, 4.0, 1, filterFactorys );

		if ( scale != 1.0 )
		{
			final AffineGet scaleTransform = new Scale3D( scale, scale, scale );
			final RandomAccessible< DoubleType > scaledImg = RealViews.affine( stack.getA(), scaleTransform );
	
			return Views.interval( scaledImg, Intervals.smallestContainingInterval( Intervals.scale( Intervals.expand( stack.getB(), 200, 2 ), scale ) ) );
		}
		else
		{
			return Views.interval( Views.raster( stack.getA() ), stack.getB() );
		}
	}

	public static BdvStackSource< ? > render3d( final List< STDataAssembly > stdata )
	{
		final DoubleType outofbounds = new DoubleType( 0 );

		final List< FilterFactory< DoubleType, DoubleType > > filterFactorys = new ArrayList<>();

		filterFactorys.add( new MedianFilterFactory<>( new DoubleType( 0 ), 20.0 ) );
		//filterFactorys.add( new GaussianFilterFactory<>( new DoubleType( 0 ), 50.0, WeightType.BY_SUM_OF_WEIGHTS ) );
		//filterFactorys.add( new MeanFilterFactory<>( new DoubleType( 0 ), 50.0 ) );
		filterFactorys.add( new SingleSpotRemovingFilterFactory<>( outofbounds, 30 ) );

		final Pair< RealRandomAccessible< DoubleType >, Interval > stack = createStack( stdata, "Calm2", outofbounds, 4.0, 2, filterFactorys );
		final Interval interval = stack.getB();

		final BdvOptions options = BdvOptions.options().numRenderingThreads( Runtime.getRuntime().availableProcessors() );
		BdvStackSource< ? > source = BdvFunctions.show( stack.getA(), interval, "Calm2", options );
		source.setDisplayRangeBounds( minRange, maxRange );
		source.setDisplayRange( min, max );
		//source.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.SINGLE );
		//source.setCurrent();

		return source;
	}

	public static void renderMovie3d( final List< STDataAssembly > stdata, final BdvStackSource< ? > source )
	{
		final List< String > genesToVisualize = new ArrayList<>();
		genesToVisualize.add( "Actb" );
		genesToVisualize.add( "Ubb" );
		genesToVisualize.add( "Hpca" );
		genesToVisualize.add( "Calm2" );
		genesToVisualize.add( "Mbp" );
		genesToVisualize.add( "Fth1" );
		genesToVisualize.add( "Pcp4" );
		genesToVisualize.add( "Ptgds" );
		genesToVisualize.add( "Ttr" );
		genesToVisualize.add( "Calm1" );
		genesToVisualize.add( "Fkbp1a" );

		final DoubleType outofbounds = new DoubleType( 0 );

		setupRecordMovie(
				source,
				(i, oldSource) ->
				{
					if ( i % 20 == 0 && i <= 220 )
					{
						final int newGeneIndex = ( i == 220 ) ? 0 : i / 20;

						final Pair< RealRandomAccessible< DoubleType >, Interval > stack = 
								createStack( stdata, genesToVisualize.get( newGeneIndex ), outofbounds );

						BdvStackSource<?> newSource =
								BdvFunctions.show(
										stack.getA(),
										stack.getB(),
										genesToVisualize.get( newGeneIndex ),
										BdvOptions.options().numRenderingThreads( Runtime.getRuntime().availableProcessors() ) );
						newSource.setDisplayRange( min, max );
						newSource.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.SINGLE );

						if ( oldSource != null )
							oldSource.close();

						return newSource;
					}
					else
					{
						return oldSource;
					} } );
	}

	public static Pair< RealRandomAccessible< DoubleType >, Interval > createStack( final List< STDataAssembly > stdata, final String gene, final DoubleType outofbounds )
	{
		return createStack(stdata, gene, outofbounds, 5.0, null );
	}

	public static Pair< RealRandomAccessible< DoubleType >, Interval > createStack(
			final List< STDataAssembly > stdata,
			final String gene,
			final DoubleType outofbounds,
			final double spacingFactor,
			final List< FilterFactory< DoubleType, DoubleType > > filterFactorys )
	{
		return createStack(stdata, gene, outofbounds, spacingFactor, 1.5, filterFactorys);
	}

	public static Pair< RealRandomAccessible< DoubleType >, Interval > createStack(
			final List< STDataAssembly > stdata,
			final String gene,
			final DoubleType outofbounds,
			final double spacingFactor,
			final double renderSigmaFactor,
			final List< FilterFactory< DoubleType, DoubleType > > filterFactorys )
	{
		final ArrayList< IterableRealInterval< DoubleType > > slices = new ArrayList<>();

		for ( int i = 0; i < stdata.size(); ++i )
			slices.add( Render.getRealIterable( stdata.get( i ), gene, filterFactorys ) );

		final double medianDistance = stdata.get( 0 ).statistics().getMedianDistance();

		// gauss crisp
		double gaussRenderSigma = medianDistance * 1.0;
		//double gaussRenderRadius = medianDistance * 4;

		final double spacing = medianDistance * spacingFactor;

		final Interval interval2d = STDataUtils.getCommonIterableInterval( slices );
		final long[] minI = new long[] { interval2d.min( 0 ), interval2d.min( 1 ), 0 - Math.round( Math.ceil( gaussRenderSigma * 3 ) ) };
		final long[] maxI = new long[] { interval2d.max( 0 ), interval2d.max( 1 ), Math.round( ( stdata.size() - 1 ) * spacing ) + Math.round( Math.ceil( gaussRenderSigma * 3 ) ) };
		final Interval interval = new FinalInterval( minI, maxI );

		final StackedIterableRealInterval< DoubleType > stack = new StackedIterableRealInterval<>( slices, spacing );

		return new ValuePair<>( Render.render( stack, new GaussianFilterFactory<>( outofbounds, gaussRenderSigma*renderSigmaFactor, WeightType.PARTIAL_BY_SUM_OF_WEIGHTS ) ), interval );
	}

	public static void setupRecordMovie( final BdvStackSource<?> bdvSource, final CallbackBDV callback )
	{
		final ActionMap ksActionMap = new ActionMap();
		final InputMap ksInputMap = new InputMap();

		// default input trigger config, disables "control button1" drag in bdv
		// (collides with default of "move annotation")
		final InputTriggerConfig config = new InputTriggerConfig(
				Arrays.asList(
						new InputTriggerDescription[]{
								new InputTriggerDescription(
										new String[]{"not mapped"}, "drag rotate slow", "bdv")}));

		final KeyStrokeAdder ksKeyStrokeAdder = config.keyStrokeAdder(ksInputMap, "persistence");

		new AbstractNamedAction( "Record movie" )
		{
			private static final long serialVersionUID = 3640052275162419689L;

			@Override
			public void actionPerformed(ActionEvent e)
			{
				new Thread( ()-> BDVFlyThrough.record( bdvSource, callback ) ).start();
			}

			public void register() {
				put(ksActionMap);
				ksKeyStrokeAdder.put(name(), "ctrl R" );
			}
		}.register();

		new AbstractNamedAction( "Add Current Viewer Transform" )
		{
			private static final long serialVersionUID = 3620052275162419689L;

			@Override
			public void actionPerformed(ActionEvent e)
			{
				BDVFlyThrough.addCurrentViewerTransform( bdvSource.getBdvHandle().getViewerPanel() );
			}

			public void register() {
				put(ksActionMap);
				ksKeyStrokeAdder.put(name(), "ctrl A" );
			}
		}.register();

		new AbstractNamedAction( "Clear All Viewer Transforms" )
		{
			private static final long serialVersionUID = 3620052275162419689L;

			@Override
			public void actionPerformed(ActionEvent e)
			{
				BDVFlyThrough.clearAllViewerTransform();
			}

			public void register() {
				put(ksActionMap);
				ksKeyStrokeAdder.put(name(), "ctrl X" );
			}
		}.register();

		bdvSource.getBdvHandle().getKeybindings().addActionMap("persistence", ksActionMap);
		bdvSource.getBdvHandle().getKeybindings().addInputMap("persistence", ksInputMap);
	}

	public static ImagePlus visualizeIJ(final List<STDataAssembly> puckData, final boolean useTransform, final boolean useIntensityTransform)
	{
		final List< STData > data = new ArrayList<>();
		final List< AffineTransform2D > transforms = new ArrayList<>();
		final List< AffineGet > intensityTransforms = useIntensityTransform ? new ArrayList<>() : null;

		for ( final STDataAssembly stda : puckData )
		{
			data.add( stda.data() );
			transforms.add( useTransform ? stda.transform() : new AffineTransform2D() );

			if ( useIntensityTransform )
				intensityTransforms.add( stda.intensityTransform() );
		}

		return AlignTools.visualizeList( data, transforms, intensityTransforms, AlignTools.defaultScale, Rendering.Gauss, AlignTools.defaultSmoothnessFactor, AlignTools.defaultGene, true );
	}

	public static void main( String[] args ) throws IOException
	{
		final ExecutorService service = Executors.newFixedThreadPool(8);
		final List<STDataAssembly> puckData =
				SpatialDataContainer.openForReading(Path.getPath() + "slide-seq-test.n5", service).openAllDatasets().stream()
						.map(sdio ->
							 {try {return sdio.readData();} catch (IOException e) {throw new RuntimeException(e);}})
						.collect(Collectors.toList());

		for ( final STDataAssembly p : puckData )
		{
			System.out.println( p.intensityTransform().get(0, 0 ) + ", " + p.intensityTransform().get(0, 1 ));
			p.intensityTransform().set( 1.0, 0.0 );
		}

		new ImageJ();

		// Display as full 3D ImageJ image
		RandomAccessibleInterval<DoubleType> img = render3d_IJ( puckData, "Ptgds", 0.4 );
		ImageJFunctions.show( img, Executors.newFixedThreadPool( 8 ) ).setTitle("Ptgds");

		// Display as 2D slices (each slice a 2D image)
		AlignTools.defaultGene = "Ptgds";//"Calm2";//"Mbp";//;
		AlignTools.defaultScale = 0.1;
		AlignTools.defaultSmoothnessFactor = 2.0;
		ImagePlus imp = visualizeIJ( puckData, true, false );
		imp.show();

		// Display interactively with BDV
		render2d( puckData.get( 0 ) );

		// Display 3D interactively with BDV
		BdvStackSource< ? > bdv = render3d( puckData );
		service.shutdown();
	}
}
