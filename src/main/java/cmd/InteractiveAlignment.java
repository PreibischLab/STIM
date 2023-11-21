package cmd;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import align.Pairwise;
import bdv.ui.splitpanel.SplitPanel;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import data.STDataUtils;
import filter.FilterFactory;
import filter.GaussianFilterFactory;
import gui.DisplayScaleOverlay;
import gui.RenderThread;
import gui.STDataAssembly;
import imglib2.TransformedIterableRealInterval;
import io.SpatialDataContainer;
import io.SpatialDataIO;
import io.TextFileAccess;
import net.imglib2.Interval;
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
import util.Threads;

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
	private double smoothnessFactor = 1.0;

	@Option(names = {"-c", "--contrast"}, description = "comma separated contrast range for BigDataViewer display, e.g. -c '0,255' (default 0.1,5)" )
	private String contrastString = null;

	@Option(names = {"-n", "--numGenes"}, required = false, description = "initial number of genes for alignment that have the highest entropy (default: 10)")
	private int numGenes = 10;

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

		final SpatialDataIO io2 = container.openDatasetReadOnly( dataset2 );
		final STDataAssembly data2 = io2.readData();

		if (!useTransform)
			data2.transform().set(new AffineTransform2D());
		if (!useIntensityTransform)
			data2.intensityTransform().set(1, 0);

		final double[] minmax = DisplayStackedSlides.parseContrastString( contrastString, RenderThread.min, RenderThread.max );

		//
		// assemble genes to test
		//
		System.out.println( "Assembling inital genes for alignment (" + numGenes + " genes)... ");

		// TODO: right temp directory
		final File tmp = new File( "/tmp/" + inputPath.hashCode() + "_" + dataset1.hashCode() + "_" + dataset2.hashCode() );
		final List< String > genesToTest = new ArrayList<>();

		if ( tmp.exists() )
		{
			System.out.println( "Attempting to load cached sorted result: " + tmp.getAbsolutePath() );
			try
			{
				final BufferedReader in = TextFileAccess.openFileReadEx( tmp );
				in.lines().forEach( s -> genesToTest.add( s.trim() ) );
				in.close();
			}
			catch (IOException e )
			{
				System.out.println( "Couldn't load tmp file: " + e);
				genesToTest.clear();
			}
		}

		// get all genes sorted (so we can pick quickly later)
		if ( genesToTest.size() == 0 )
		{
			genesToTest.addAll( Pairwise.genesToTest( data1.data(), data2.data(), Integer.MAX_VALUE, Threads.numThreads() ) );

			System.out.println( "Attempting to save cached sorted result: " + tmp.getAbsolutePath() );

			try
			{
				final PrintWriter out = TextFileAccess.openFileWriteEx( tmp );
				genesToTest.forEach( s -> out.println( s ) );
				out.close();
			}
			catch (IOException e )
			{
				System.out.println( "Couldn't load tmp file: " + e);
				genesToTest.clear();
			}
		}

		if ( numGenes > 0 )
			System.out.println( "Automatically identified " + genesToTest.size() + " genes that can be used for alignment" );

		// the plan is to open two BDV windows next to each other,
		// render in parallel and overlay candidates and inliers

		BdvStackSource< ? > source = null;
		ArrayList< GaussianFilterFactory< DoubleType, DoubleType > > factories = new ArrayList<>();

		System.out.println( "Starting BDV ... " );

		for ( int i = 0; i < numGenes; ++i )
		{
			final String gene = genesToTest.get( i ); //"Calm2";
			System.out.println( "Rendering gene (each available as its own source): " + gene );

			minmax[ 0 ] = Double.MAX_VALUE;
			minmax[ 1 ] = -Double.MAX_VALUE;

			for ( final DoubleType t : data1.data().getExprData(gene) )
			{
				minmax[ 0 ] = Math.min( minmax[ 0 ], t.get() );
				minmax[ 1 ] = Math.max( minmax[ 1 ], t.get() );
			}

			System.out.println( "min/max: " + minmax[0] + "/" + minmax[1] );
			System.out.println( "min/max display range: " + "0" + "/" + minmax[1]/2 );

			final List< FilterFactory< DoubleType, DoubleType > > filterFactorys = new ArrayList<>();
			//filterFactorys.add( new MedianFilterFactory<DoubleType>( new DoubleType(), 3 * medianDistance ) );

			final Pair< RealRandomAccessible< DoubleType >, GaussianFilterFactory< DoubleType, DoubleType > > rendered = 
					Render.getRealRandomAccessible2( data1, gene, smoothnessFactor, filterFactorys );

			final RealRandomAccessible< DoubleType > rra = rendered.getA();
			final GaussianFilterFactory< DoubleType, DoubleType > factory = rendered.getB();
			factories.add( factory );

			final Interval interval =
						STDataUtils.getIterableInterval(
								new TransformedIterableRealInterval<>(
										data1.data(),
										data1.transform() ) );

			BdvOptions options =
					BdvOptions.options().numRenderingThreads( Runtime.getRuntime().availableProcessors() / 2 ).addTo( source ).is2D();

			source = BdvFunctions.show( rra, interval, gene, options );

			source.setDisplayRangeBounds( 0, minmax[1] );
			source.setDisplayRange( minmax[0], minmax[1]/2 );
			source.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.SINGLE );
			source.setCurrent();
	
			final AffineTransform3D t = new AffineTransform3D();
			source.getBdvHandle().getViewerPanel().state().getViewerTransform( t );
			t.set( 0, 2, 3 );
			source.getBdvHandle().getViewerPanel().state().setViewerTransform( t );
		}

		final double medianDistance = (data1.statistics().getMedianDistance() + data2.statistics().getMedianDistance()) / 2.0;

		System.out.println( "Setting panel ... " );

		// the side panel
		final SplitPanel splitPanel = source.getBdvHandle().getSplitPanel();

		/*
		// add STIMCard panel
		System.out.println( "Adding STIMCard ... " );
		final STIMCard card = new STIMCard(factory, medianDistance, source.getBdvHandle());
		source.getBdvHandle().getCardPanel().addCard( "STIM Display Options", "STIM Display Options", card.getPanel(), false );

		// add STIMCard panel
		System.out.println( "Adding STIMAlignmentCard ... " );
		final STIMAlignmentCard cardAlign = new STIMAlignmentCard(medianDistance, medianDistance*2, 25, 10, numGenes, source.getBdvHandle());
		source.getBdvHandle().getCardPanel().addCard( "SIFT Alignment", "SIFT Alignment", cardAlign.getPanel(), false );

		card.toggleActiveListeners();
		source.getBdvHandle().getCardPanel().setCardExpanded(card, true); // collapse groups panel
		source.getBdvHandle().getCardPanel().setCardExpanded(cardAlign, true); // collapse sources panel
		System.out.println("done");
		*/

		// add scale (so the right size of the images for alignment can be selected)
		System.out.println( "Adding DisplayScaleOverlay ... " );
		final DisplayScaleOverlay overlay = new DisplayScaleOverlay();
		source.getBdvHandle().getViewerPanel().renderTransformListeners().add(overlay);
		source.getBdvHandle().getViewerPanel().getDisplay().overlays().add(overlay);

		// show scalebar (so the right error can be selected)
		System.out.println( "Adding ScaleBar ... " );
		source.getBdvHandle().getAppearanceManager().appearance().setShowScaleBar( true );

		// collapse all existing panels (except sources)
		source.getBdvHandle().getCardPanel().setCardExpanded(bdv.ui.BdvDefaultCards.DEFAULT_SOURCEGROUPS_CARD, false); // collapse groups panel
		source.getBdvHandle().getCardPanel().setCardExpanded(bdv.ui.BdvDefaultCards.DEFAULT_SOURCES_CARD, true); // collapse sources panel
		source.getBdvHandle().getCardPanel().setCardExpanded(bdv.ui.BdvDefaultCards.DEFAULT_VIEWERMODES_CARD, false); // collapse display modes panel

		// Expands the split Panel
		splitPanel.setCollapsed(false);

		//service.shutdown();

		return null;
	}

	public class STIMAlignmentCard
	{
		private final JPanel panel;
		private boolean listenersActive = false;

		public STIMAlignmentCard(
				final double medianDistance,
				final double errorInit,
				final int minNumInliersInit,
				final int minNumInliersGeneInit,
				final int numGenesInit,
				final BdvHandle bdvhandle )
		{
			this.panel = new JPanel(new MigLayout("gap 0, ins 5 5 5 0, fill", "[right][grow]", "center"));

			final BoundedValuePanel maxErrorSlider = new BoundedValuePanel(new BoundedValue(0, medianDistance * 10, errorInit ));
			maxErrorSlider.setBorder(null);
			final JLabel maxErrorLabel = new JLabel("max. error (px)");
			panel.add(maxErrorLabel, "aligny baseline");
			panel.add(maxErrorSlider, "growx, wrap");

			final BoundedValuePanel inliersSlider = new BoundedValuePanel(new BoundedValue(0, 50, minNumInliersInit ));
			inliersSlider.setBorder(null);
			final JLabel inliersLabel = new JLabel("min inliers (total)");
			panel.add(inliersLabel, "aligny baseline");
			panel.add(inliersSlider, "growx, wrap");

			final BoundedValuePanel inliersPerGeneSlider = new BoundedValuePanel(new BoundedValue(0, 25, minNumInliersGeneInit ));
			inliersPerGeneSlider.setBorder(null);
			final JLabel inliersPerGeneLabel = new JLabel("min inliers (gene)");
			panel.add(inliersPerGeneLabel, "aligny baseline");
			panel.add(inliersPerGeneSlider, "growx, wrap");

			final BoundedValuePanel numGenesSlides = new BoundedValuePanel(new BoundedValue(0, 100, numGenesInit ));
			numGenesSlides.setBorder(null);
			final JLabel numGenesSlidesLabel = new JLabel("num genes");
			panel.add(numGenesSlidesLabel, "aligny baseline");
			panel.add(numGenesSlides, "growx, wrap");

			final JButton run = new JButton("Run ...");
			panel.add(run);

			/*
			final BoundedValuePanel sigmaSlider = new BoundedValuePanel(new BoundedValue(0, Math.max( 2.50, currentSigma * 1.5 ), currentSigma ));
			sigmaSlider.setBorder(null);
			final JLabel sigmaLabel = new JLabel("sigma (-sf)");
			panel.add(sigmaLabel, "aligny baseline");
			panel.add(sigmaSlider, "growx, wrap");
			//panel.add
			//final MinSigmaEditor minSigmaEditor = new MinSigmaEditor(minSigmaLabel, minSigmaSlider, editor.getModel());

			sigmaSlider.changeListeners().add( () -> {
				gaussFactory.setSigma( sigmaSlider.getValue().getValue() * medianDistance);
				bdvhandle.getViewerPanel().requestRepaint();
			} );

			final JPopupMenu menu = new JPopupMenu();
			menu.add(runnableItem("set bounds ...", sigmaSlider::setBoundsDialog));
			sigmaSlider.setPopup(() -> menu);*/
		}

		public JPanel getPanel() { return panel; }
		public void toggleActiveListeners() { this.listenersActive = !this.listenersActive; }

		private JMenuItem runnableItem(final String text, final Runnable action) {
			final JMenuItem item = new JMenuItem(text);
			item.addActionListener(e -> action.run());
			return item;
		}
	}

	public class STIMCard
	{
		private final JPanel panel;
		private boolean listenersActive = false;

		public STIMCard(
				final GaussianFilterFactory< DoubleType, DoubleType > gaussFactory,
				final double medianDistance,
				final BdvHandle bdvhandle )
		{
			System.out.println( "Setting up panel ... " );

			this.panel = new JPanel(new MigLayout("gap 0, ins 5 5 5 0, fill", "[right][grow]", "center"));

			final double currentSigma = gaussFactory.getSigma()/medianDistance;
			final BoundedValuePanel sigmaSlider = new BoundedValuePanel(new BoundedValue(0, Math.max( 2.50, currentSigma * 1.5 ), currentSigma ));
			sigmaSlider.setBorder(null);
			final JLabel sigmaLabel = new JLabel("sigma (-sf)");
			panel.add(sigmaLabel, "aligny baseline");
			panel.add(sigmaSlider, "growx, wrap");
			//panel.add
			//final MinSigmaEditor minSigmaEditor = new MinSigmaEditor(minSigmaLabel, minSigmaSlider, editor.getModel());

			System.out.println( "Adding Listeners ... " );

			sigmaSlider.changeListeners().add( () ->
			{
				if ( this.listenersActive )
				{
					gaussFactory.setSigma( sigmaSlider.getValue().getValue() * medianDistance );
					bdvhandle.getViewerPanel().requestRepaint();
				}
			} );

			System.out.println( "Adding Popups ... " );

			final JPopupMenu menu = new JPopupMenu();
			menu.add(runnableItem("set bounds ...", sigmaSlider::setBoundsDialog));
			sigmaSlider.setPopup(() -> menu);

			System.out.println( "Done ... " );
		}

		public void toggleActiveListeners() { this.listenersActive = !this.listenersActive; }
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
