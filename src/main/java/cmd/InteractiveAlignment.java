package cmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import bdv.ui.splitpanel.SplitPanel;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import data.STDataUtils;
import filter.FilterFactory;
import filter.Filters;
import filter.GaussianFilterFactory;
import filter.MedianFilterFactory;
import filter.RadiusSearchFilterFactory;
import gui.DisplayScaleOverlay;
import gui.RenderThread;
import gui.STDataAssembly;
import imglib2.TransformedIterableRealInterval;
import io.SpatialDataContainer;
import io.SpatialDataIO;
import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.RealCursor;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.miginfocom.swing.MigLayout;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import render.Render;
import util.BoundedValue;
import util.BoundedValuePanel;

public class InteractiveAlignment implements Callable<Void> {

	@Option(names = {"-i", "--input"}, required = true, description = "input N5 container path, e.g. -i /home/ssq.n5.")
	private String inputPath = null;

	@Option(names = {"-d1", "--dataset1"}, required = false, description = "the first dataset (slice), e.g. -d1 'Puck_180528_20'")
	private String dataset1 = null;

	@Option(names = {"-d2", "--dataset2"}, required = false, description = "the second dataset (slice), e.g. -d2 'Puck_180528_22'")
	private String dataset2 = null;

	@Option(names = {"-s", "--scale"}, required = false, description = "initial scaling factor for rendering the coordinates into images, can be changed interactively (default: 0.05 for slideseq data)")
	private double scale = 0.05;

	@Option(names = {"-sf", "--smoothnessFactor"}, required = false, description = "initial factor for the sigma of the gaussian used for rendering, corresponds to smoothness, can be changed interactively, e.g -sf 2.0 (default: 4.0)")
	private double smoothnessFactor = 4.0;

	@Option(names = {"-c", "--contrast"}, description = "comma separated contrast range for BigDataViewer display, e.g. -c '0,255' (default 0.1,5)" )
	private String contrastString = null;

	@Override
	public Void call() throws Exception
	{
		final boolean useIntensityTransform = false;
		final boolean useTransform = true;

		final ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

		if ( !SpatialDataContainer.isCompatibleContainer(inputPath) )
		{
			System.out.println("'" + inputPath + "' is not a container. Stopping.");
			return null;
		}

		final SpatialDataContainer container = SpatialDataContainer.openForReading(inputPath, service);

		System.out.println("Opening dataset '" + dataset1 + "' in '" + inputPath + "' ...");

		final SpatialDataIO io1 = container.openDatasetReadOnly( dataset1 );
		final STDataAssembly data1 = io1.readData();

		if (!useTransform)
			data1.transform().set(new AffineTransform2D());
		if (!useIntensityTransform)
			data1.intensityTransform().set(1, 0);

		final double[] minmax = DisplayStackedSlides.parseContrastString( contrastString, RenderThread.min, RenderThread.max );

		// the plan is to open two BDV windows next to each other,
		// render in parallel and overlay candidates and inliers

		BdvStackSource< ? > source = null;

		final String gene = "Calm2";
		System.out.println( "Rendering gene: " + gene );

		final double medianDistance = data1.statistics().getMedianDistance();

		final List< FilterFactory< DoubleType, DoubleType > > filterFactorys = new ArrayList<>();
		filterFactorys.add( new MedianFilterFactory<DoubleType>( new DoubleType(), 3 * medianDistance ) );

		// Testing ..
		IterableRealInterval<DoubleType> raw = data1.data().getExprData( gene );
		IterableRealInterval<DoubleType> filtered =
				Filters.filter( data1.data().getExprData( gene ), filterFactorys.get( 0 ) );
		IterableRealInterval<DoubleType> filteredV =
				Filters.filterVirtual( data1.data().getExprData( gene ), filterFactorys.get( 0 ), DoubleType::new );

		RealCursor<DoubleType> rc = raw.cursor();
		RealCursor<DoubleType> fc = filtered.cursor();
		RealCursor<DoubleType> fcv = filteredV.cursor();

		for ( int i = 0; i < 10; ++i)
			System.out.println( "raw: " + rc.next() + ", filtered: " + fc.next() + ", filteredvirtual: " + fcv.next() );

		((RadiusSearchFilterFactory<?,?>)filterFactorys.get( 0 )).setRadius( 0.5 );

		rc = raw.cursor();
		fc = filtered.cursor();
		fcv = filteredV.cursor();

		System.out.println();

		for ( int i = 0; i < 10; ++i)
			System.out.println( "raw: " + rc.next() + ", filtered: " + fc.next() + ", filteredvirtual: " + fcv.next() );

		//System.exit( 0 );

		final Pair< RealRandomAccessible< DoubleType >, GaussianFilterFactory< DoubleType, DoubleType > > rendered = 
				Render.getRealRandomAccessible2( data1, gene, smoothnessFactor, filterFactorys );

		final RealRandomAccessible< DoubleType > rra = rendered.getA();
		final GaussianFilterFactory< DoubleType, DoubleType > factory = rendered.getB();

		final Interval interval =
					STDataUtils.getIterableInterval(
							new TransformedIterableRealInterval<>(
									data1.data(),
									data1.transform() ) );

		BdvOptions options = BdvOptions.options().numRenderingThreads( Runtime.getRuntime().availableProcessors() ).addTo( source );
		options = options.is2D();
		source = BdvFunctions.show( rra, interval, gene, options );
		source.setDisplayRange( minmax[0], minmax[1] );
		source.setDisplayRangeBounds( 0, 200 );
		source.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.FUSED );
		source.setCurrent();

		final AffineTransform3D t = new AffineTransform3D();
		source.getBdvHandle().getViewerPanel().state().getViewerTransform( t );
		t.set(0, 2, 3 );
		source.getBdvHandle().getViewerPanel().state().setViewerTransform( t );

		// the side panel
		final SplitPanel splitPanel = source.getBdvHandle().getSplitPanel();

		// Expands the split Panel
		splitPanel.setCollapsed(false);

		// collapse all existing panels
		source.getBdvHandle().getCardPanel().setCardExpanded(bdv.ui.BdvDefaultCards.DEFAULT_SOURCEGROUPS_CARD, false); // collapse groups panel
		source.getBdvHandle().getCardPanel().setCardExpanded(bdv.ui.BdvDefaultCards.DEFAULT_SOURCES_CARD, false); // collapse sources panel
		source.getBdvHandle().getCardPanel().setCardExpanded(bdv.ui.BdvDefaultCards.DEFAULT_VIEWERMODES_CARD, false); // collapse display modes panel

		// add new STIMCard panel
		final STIMCard card = new STIMCard(factory, medianDistance, source.getBdvHandle());
		source.getBdvHandle().getCardPanel().addCard( "STIM Display Options", "STIM Display Options", card.getPanel(), true );

		// add scale
		DisplayScaleOverlay overlay = new DisplayScaleOverlay();
		source.getBdvHandle().getViewerPanel().renderTransformListeners().add(overlay);
		source.getBdvHandle().getViewerPanel().getDisplay().overlays().add(overlay);

		System.out.println("done");

		service.shutdown();

		return null;
	}

	public class STIMCard
	{
		private final JPanel panel;

		public STIMCard(
				final GaussianFilterFactory< DoubleType, DoubleType > gaussFactory,
				final double medianDistance,
				final BdvHandle bdvhandle )
		{
			this.panel = new JPanel(new MigLayout("gap 0, ins 5 5 5 0, fill", "[right][grow]", "center"));

			final double currentSigma = gaussFactory.getSigma()/medianDistance;
			final BoundedValuePanel sigmaSlider = new BoundedValuePanel(new BoundedValue(0, Math.max( 2.50, currentSigma * 1.5 ), currentSigma ));
			sigmaSlider.setBorder(null);
			final JLabel sigmaLabel = new JLabel("sigma (-sf)");
			panel.add(sigmaLabel, "aligny baseline");
			panel.add(sigmaSlider, "growx, wrap");
			//final MinSigmaEditor minSigmaEditor = new MinSigmaEditor(minSigmaLabel, minSigmaSlider, editor.getModel());

			sigmaSlider.changeListeners().add( () -> {
				gaussFactory.setSigma( sigmaSlider.getValue().getValue() * medianDistance);
				bdvhandle.getViewerPanel().requestRepaint();
			} );

			final JPopupMenu menu = new JPopupMenu();
			menu.add(runnableItem("set bounds ...", sigmaSlider::setBoundsDialog));
			sigmaSlider.setPopup(() -> menu);
		}

		public JPanel getPanel() { return panel; }

		private JMenuItem runnableItem(final String text, final Runnable action) {
			final JMenuItem item = new JMenuItem(text);
			item.addActionListener(e -> action.run());
			return item;
		}
	}

	public static void main(final String... args) {
		CommandLine.call(new InteractiveAlignment(), args);
	}
}
