package cmd;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import align.AlignTools;
import align.Pairwise;
import align.PairwiseSIFT;
import align.PairwiseSIFT.SIFTParam;
import align.PairwiseSIFT.SIFTParam.SIFTMatching;
import align.SiftMatch;
import bdv.tools.transformation.TransformedSource;
import bdv.ui.splitpanel.SplitPanel;
import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.SourceGroup;
import bdv.viewer.SynchronizedViewerState;
import bdv.viewer.ViewerState;
import data.STDataUtils;
import filter.FilterFactory;
import filter.GaussianFilterFactory;
import gui.DisplayScaleOverlay;
import gui.STDataAssembly;
import gui.geneselection.GeneSelectionExplorer;
import gui.overlay.SIFTOverlay;
import imglib2.TransformedIterableRealInterval;
import io.SpatialDataContainer;
import io.SpatialDataIO;
import io.TextFileAccess;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.PointMatch;
import mpicbg.models.RigidModel2D;
import net.imglib2.Interval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
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

		//
		// assemble genes to test
		//
		System.out.println( "Assembling inital genes for alignment (" + numGenes + " genes)... ");

		// TODO: right temp directory
		final File tmp = new File( "/tmp/" + inputPath.hashCode() + "_" + dataset1.hashCode() + "_" + dataset2.hashCode() );
		final List< Pair< String, Double > > allGenes = new ArrayList<>();

		if ( tmp.exists() )
		{
			System.out.println( "Attempting to load cached sorted result: " + tmp.getAbsolutePath() );
			try
			{
				final BufferedReader in = TextFileAccess.openFileReadEx( tmp );
				in.lines().forEach( s -> {
					String[] entries = s.split( "\t" );
					allGenes.add( new ValuePair<>( entries[ 0 ], Double.parseDouble( entries[1] ) ) );
				});
				in.close();
			}
			catch (IOException e )
			{
				System.out.println( "Couldn't load tmp file: " + e);
				allGenes.clear();
			}
		}

		// get all genes sorted (so we can pick quickly later)
		if ( allGenes.size() == 0 )
		{
			allGenes.addAll( Pairwise.allGenes( data1.data(), data2.data(), Threads.numThreads() ) );

			System.out.println( "Attempting to save cached sorted result: " + tmp.getAbsolutePath() );

			try
			{
				final PrintWriter out = TextFileAccess.openFileWriteEx( tmp );
				allGenes.forEach( s -> out.println( s.getA() + "\t" + s.getB() ) );
				out.close();
			}
			catch (IOException e )
			{
				System.out.println( "Couldn't save tmp file: " + e);
			}
		}

		if ( numGenes > 0 )
			System.out.println( "Automatically identified " + allGenes.size() + " genes that can be used for alignment" );
		else
		{
			System.err.println( "No common genes between both datasets. stopping.");
			System.exit( 0 );
		}

		// the plan is to open two BDV windows next to each other,
		// render in parallel and overlay candidates and inliers

		BdvStackSource< ? > source = null;
		ArrayList< GaussianFilterFactory< DoubleType, DoubleType > > factories = new ArrayList<>();

		System.out.println( "Starting BDV ... " );
		System.out.println( "Starting with the top " + numGenes + " genes (you find them in the 'groups' panel, you can add/remove genes in the GUI." );

		for ( int i = 0; i < numGenes; ++i )
		{
			final String gene = allGenes.get( i ).getA(); //"Calm2";
			System.out.println( "Rendering gene (each available as its own source): " + gene );

			source = addGene(
					source,
					factories,
					data1,
					gene,
					smoothnessFactor,
					new ARGBType( ARGBType.rgba(0, 255, 0, 0) ) );

			source = addGene(
					source,
					factories,
					data2,
					gene,
					smoothnessFactor,
					new ARGBType( ARGBType.rgba(255, 0, 255, 0) ) );
		}


		final SynchronizedViewerState state = source.getBdvHandle().getViewerPanel().state();
		final ArrayList< SourceGroup > oldGroups = new ArrayList<>( state.getGroups() );

		final HashMap< String, SourceGroup > geneToBDVSource = new HashMap<>();

		for ( int i = 0; i < numGenes; ++i )
		{
			final String gene = allGenes.get( i ).getA();

			final SourceGroup handle = new SourceGroup();
			state.addGroup( handle );
			state.setGroupName( handle, gene );
			state.setGroupActive( handle, true );
			state.addSourceToGroup( state.getSources().get(i*2), handle );
			state.addSourceToGroup( state.getSources().get(i*2 + 1), handle );

			geneToBDVSource.put( gene, handle );
		}

		source.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.GROUP );

		state.removeGroups( oldGroups );

		/*
		[13:19, 11/21/2023] Tobias Pietzsch: ViewerPanel.state()
		[13:20, 11/21/2023] Tobias Pietzsch: und dann in dem ViewerState addSourcesToGroup( Collection< ? extends SourceAndConverter< ? > > collection, SourceGroup group );
		[13:20, 11/21/2023] Tobias Pietzsch: und auch im ViewerState setDisplayMode(GROUP)
		[13:21, 11/21/2023] Stephan Preibisch: und wie mache ich 10 groups initial?
		[13:21, 11/21/2023] Stephan Preibisch: also die SourceGroup objekte
		[13:21, 11/21/2023] Tobias Pietzsch: einfach new bdv.viewer.SourceGroup()
		[13:22, 11/21/2023] Tobias Pietzsch: das sind quasi nur IDs
		[13:22, 11/21/2023] Stephan Preibisch: ok, cool, danke!
		[13:22, 11/21/2023] Tobias Pietzsch: dann ViewerState:addGroup(…) ode addGroups(…)
		[13:22, 11/21/2023] Stephan Preibisch: ich versuche mein Glück :)
		[13:22, 11/21/2023] Tobias Pietzsch: ViewerState.setGroupName(SourceGroup, String)
		[13:23, 11/21/2023] Tobias Pietzsch: etc
		*/
		final double medianDistance = (data1.statistics().getMedianDistance() + data2.statistics().getMedianDistance()) / 2.0;
		System.out.println( "Median distance of spots: " + medianDistance );

		// the side panel
		final SplitPanel splitPanel = source.getBdvHandle().getSplitPanel();

		// add scale (so the right size of the images for alignment can be selected)
		final DisplayScaleOverlay overlay = new DisplayScaleOverlay();
		source.getBdvHandle().getViewerPanel().renderTransformListeners().add(overlay);
		source.getBdvHandle().getViewerPanel().getDisplay().overlays().add(overlay);

		// show scalebar (so the right error can be selected)
		source.getBdvHandle().getAppearanceManager().appearance().setShowScaleBar( true );

		// collapse all existing panels (except sources)
		source.getBdvHandle().getCardPanel().setCardExpanded(bdv.ui.BdvDefaultCards.DEFAULT_SOURCEGROUPS_CARD, true); // collapse groups panel
		source.getBdvHandle().getCardPanel().setCardExpanded(bdv.ui.BdvDefaultCards.DEFAULT_SOURCES_CARD, false); // collapse sources panel
		source.getBdvHandle().getCardPanel().setCardExpanded(bdv.ui.BdvDefaultCards.DEFAULT_VIEWERMODES_CARD, false); // collapse display modes panel

		// add STIMCard panel
		final STIMCard card = new STIMCard(factories, medianDistance, source.getBdvHandle());
		source.getBdvHandle().getCardPanel().addCard( "STIM Display Options", "STIM Display Options", card.getPanel(), true );

		// add STIMAlignmentCard panel
		final STIMAlignmentCard cardAlign = new STIMAlignmentCard( data1, data2, overlay, card, allGenes, geneToBDVSource, medianDistance, medianDistance*1.5, 25, 10, numGenes, source.getBdvHandle());
		source.getBdvHandle().getCardPanel().addCard( "SIFT Alignment", "SIFT Alignment", cardAlign.getPanel(), true );

		// activate listeners
		card.toggleActiveListeners();

		// Expands the split Panel (after waiting 2 secs for the BDV to calm down)
		SimpleMultiThreading.threadWait( 2000 );
		splitPanel.setCollapsed(false);

		System.out.println("done");

		//service.shutdown();

		return null;
	}

	public static ArrayList< TransformedSource< ? > > getTransformedSources( final ViewerState state )
	{
		final List< ? extends SourceAndConverter< ? > > sourceList;
		synchronized ( state )
		{
			sourceList = new ArrayList<>( state.getSources() );
		}

		final ArrayList< TransformedSource< ? > > list = new ArrayList<>();
		for ( final SourceAndConverter< ? > soc : sourceList )
		{
			final Source< ? > source = soc.getSpimSource();
			if ( source instanceof TransformedSource )
				list.add( (TransformedSource< ? > ) source );
		}
		return list;
	}

	public static BdvStackSource< ? > addGene(
			final Bdv bdv,
			final List< GaussianFilterFactory< DoubleType, DoubleType > > factories,
			final STDataAssembly data,
			final String gene,
			final double smoothnessFactor,
			final ARGBType color )
	{
		final double[] minmax = new double[ 2 ];

		minmax[ 0 ] = Double.MAX_VALUE;
		minmax[ 1 ] = -Double.MAX_VALUE;

		for ( final DoubleType t : data.data().getExprData(gene) )
		{
			minmax[ 0 ] = Math.min( minmax[ 0 ], t.get() );
			minmax[ 1 ] = Math.max( minmax[ 1 ], t.get() );
		}

		System.out.println( "min/max: " + minmax[0] + "/" + minmax[1] );
		System.out.println( "min/max display range: " + "0" + "/" + minmax[1]/2 );

		final List< FilterFactory< DoubleType, DoubleType > > filterFactorys = new ArrayList<>();
		//filterFactorys.add( new MedianFilterFactory<DoubleType>( new DoubleType(), 3 * medianDistance ) );

		final Pair< RealRandomAccessible< DoubleType >, GaussianFilterFactory< DoubleType, DoubleType > > rendered = 
				Render.getRealRandomAccessible2( data, gene, smoothnessFactor, filterFactorys );

		final RealRandomAccessible< DoubleType > rra = rendered.getA();
		final GaussianFilterFactory< DoubleType, DoubleType > factory = rendered.getB();
		factories.add( factory );

		final Interval interval =
					STDataUtils.getIterableInterval(
							new TransformedIterableRealInterval<>(
									data.data(),
									data.transform() ) );

		BdvOptions options =
				BdvOptions.options().numRenderingThreads( Runtime.getRuntime().availableProcessors() / 2 ).addTo( bdv ).is2D().preferredSize(800, 800);

		BdvStackSource< ? > source = BdvFunctions.show( rra, interval, gene, options );

		source.setDisplayRangeBounds( 0, minmax[1] );
		source.setDisplayRange( minmax[0], minmax[1]/2 );
		source.setColor( color );
		//source.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.SINGLE );
		source.setCurrent();

		/*
		final AffineTransform3D mipmapTransform = getMipmapTransforms()[ level ];
		currentSourceTransforms[ level ].set( reg );
		currentSourceTransforms[ level ].concatenate( mipmapTransform );
		 */

		final AffineTransform3D t = new AffineTransform3D();
		source.getBdvHandle().getViewerPanel().state().getViewerTransform( t );
		t.set( 0, 2, 3 );
		source.getBdvHandle().getViewerPanel().state().setViewerTransform( t );

		return source;
	}

	public class STIMAlignmentCard
	{
		private final JPanel panel;

		private GeneSelectionExplorer gse = null;

		public STIMAlignmentCard(
				final STDataAssembly data1,
				final STDataAssembly data2,
				final DisplayScaleOverlay overlay,
				final STIMCard stimcard,
				final List< Pair< String, Double > > allGenes,
				final HashMap< String, SourceGroup > geneToBDVSource,
				final double medianDistance,
				final double errorInit,
				final int minNumInliersInit,
				final int minNumInliersGeneInit,
				final int numGenesInit,
				final BdvHandle bdvhandle )
		{
			this.panel = new JPanel(new MigLayout("gap 0, ins 5 5 5 0, fill", "[right][grow]", "center"));

			String options[] = { "Fast", "Normal", "Thorough", "Very thorough" }; // TODO: Advanced with window popping up
			JComboBox< String > box = new JComboBox< String > (options);
			box.setBorder( null );
			box.setSelectedIndex( 1 );
			final JLabel boxLabel = new JLabel("SIFT Matching ");
			panel.add( boxLabel, "aligny baseline" );
			panel.add( box, "growx, wrap" );

			final BoundedValuePanel maxErrorSlider = new BoundedValuePanel(new BoundedValue(0, medianDistance * 5, errorInit ));
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

			//final JCheckBox applyTransform = new JCheckBox( "Apply transform  " );
			//final JCheckBox overlayInliers = new JCheckBox( "Overlay features" );
			//panel.add(applyTransform, "aligny baseline");
			//panel.add(overlayInliers, "growx, wrap");

			final JButton add = new JButton("Add genes");
			panel.add(add, "aligny baseline");
			final JButton run = new JButton("Run SIFT");
			panel.add(run, "growx, wrap");

			run.addActionListener( l ->
			{
				final SynchronizedViewerState state = bdvhandle.getViewerPanel().state();
				updateRemainingSources( state, geneToBDVSource );

				final int minInliers = (int)Math.round( inliersSlider.getValue().getValue() );
				final int minInliersPerGene = (int)Math.round( inliersPerGeneSlider.getValue().getValue() );
				final double maxError = maxErrorSlider.getValue().getValue();
				final double scale = overlay.currentScale();
				final double sigma = stimcard.currentSigma();

				System.out.println( "Running SIFT align with the following parameters: ");
				System.out.println( "maxError: " + maxError + ", minInliers (over all genes): " + minInliers + ", minInliers (per genes): " + minInliersPerGene );
				System.out.println( "scale: " + scale + ", sigma: " + sigma );
				System.out.println( "SIFT: " + SIFTMatching.values()[ box.getSelectedIndex() ] );

				final SIFTParam p = new SIFTParam( SIFTMatching.values()[ box.getSelectedIndex() ] );

				final SiftMatch match = PairwiseSIFT.pairwiseSIFT(
						data1.data(), dataset1, data2.data(), dataset2,
						new RigidModel2D(), new RigidModel2D(),
						new ArrayList<>( geneToBDVSource.keySet() ),
						p, scale, sigma, maxError,
						minInliers, minInliersPerGene,
						true, Threads.numThreads() );

				System.out.println( match.getNumInliers() + "/" + match.getNumCandidates() );

				// TODO: print out cmd-line args

				// 
				// apply transformations
				//
				if ( match.getInliers().size() > 0 )
				{
					try
					{
						final RigidModel2D model = new RigidModel2D();
						model.fit( match.getInliers() );
						final AffineTransform2D m = AlignTools.modelToAffineTransform2D( model ).inverse();
						final AffineTransform3D m3d = new AffineTransform3D();
						m3d.set(m.get(0, 0), 0, 0 ); // row, column
						m3d.set(m.get(0, 1), 0, 1 ); // row, column
						m3d.set(m.get(1, 0), 1, 0 ); // row, column
						m3d.set(m.get(1, 1), 1, 1 ); // row, column
						m3d.set(m.get(0, 2), 0, 3 ); // row, column
						m3d.set(m.get(1, 2), 1, 3 ); // row, column

						System.out.println( m );
						System.out.println( m3d );

						final ArrayList<TransformedSource<?>> tsources = getTransformedSources(state);

						// every second source will be transformed
						for ( int i = 1; i < tsources.size(); i = i + 2 )
							tsources.get( i ).setFixedTransform( m3d );

						bdvhandle.getViewerPanel().requestRepaint();

					} catch (NotEnoughDataPointsException e)
					{
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

				//
				// Overlay detections
				//
				final SIFTOverlay siftoverlay = new SIFTOverlay( match.getInliers() );
				bdvhandle.getViewerPanel().renderTransformListeners().add( siftoverlay );
				bdvhandle.getViewerPanel().getDisplay().overlays().add( siftoverlay );
				//match.getInliers().forEach( s -> System.out.println( Util.printCoordinates( s.getP1().getL() )) );
			});

			add.addActionListener( l -> 
			{
				final List< Pair< String, Double > > genes = new ArrayList<>();

				for ( int i = 100; i >=0; --i )
					genes.add( new ValuePair<String, Double>("gene " + i, (double)i ) );

				if ( gse == null || gse.frame().isVisible() == false )
					gse = new GeneSelectionExplorer(
							allGenes,
							list ->
							{
								//
								// first check if all groups are still present that are in the HashMap
								//
								final SynchronizedViewerState state = bdvhandle.getViewerPanel().state();
								updateRemainingSources( state, geneToBDVSource );

								/*
								final SynchronizedViewerState state = bdvhandle.getViewerPanel().state();
								final ArrayList< SourceGroup > currentGroups = new ArrayList<>( state.getGroups() );

								final ArrayList< String > toRemove = new ArrayList<>();
								for ( final Entry<String, SourceGroup > entry : geneToBDVSource.entrySet() )
									if ( !currentGroups.contains( entry.getValue() ) )
										toRemove.add( entry.getKey() );

								toRemove.forEach( s -> geneToBDVSource.remove( s ) );
								*/

								//
								// Now add the new ones (if it's not already there)
								//
								for ( final String gene : list )
								{
									if ( !geneToBDVSource.containsKey( gene ) )
									{
										System.out.println( "Gene " + gene + " will be added." );

										addGene( bdvhandle, stimcard.gaussFactories(), data1, gene, stimcard.currentSigma(), new ARGBType( ARGBType.rgba(0, 255, 0, 0) ) );
										addGene( bdvhandle, stimcard.gaussFactories(), data2, gene, stimcard.currentSigma(), new ARGBType( ARGBType.rgba(255, 0, 255, 0) ) );

										final SourceGroup handle = new SourceGroup();
										state.addGroup( handle );
										state.setGroupName( handle, gene );
										state.setGroupActive( handle, true );
										state.addSourceToGroup( state.getSources().get( state.getSources().size() - 2 ), handle );
										state.addSourceToGroup( state.getSources().get( state.getSources().size() - 1 ), handle );

										geneToBDVSource.put( gene, handle );

										bdvhandle.getViewerPanel().setDisplayMode( DisplayMode.GROUP );
									}
									else
									{
										System.out.println( "Gene " + gene + " is already being displayed, ignoring." );
										// TODO: remove gaussFactories? - maybe not necessary
									}
								}
							} );
			});


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

		protected void updateRemainingSources( final SynchronizedViewerState state, final HashMap< String, SourceGroup > geneToBDVSource )
		{
			final ArrayList< SourceGroup > currentGroups = new ArrayList<>( state.getGroups() );

			final ArrayList< String > toRemove = new ArrayList<>();
			for ( final Entry<String, SourceGroup > entry : geneToBDVSource.entrySet() )
				if ( !currentGroups.contains( entry.getValue() ) )
					toRemove.add( entry.getKey() );

			toRemove.forEach( s -> geneToBDVSource.remove( s ) );
		}

		public JPanel getPanel() { return panel; }
	}

	public class STIMCard
	{
		private final JPanel panel;
		private boolean listenersActive = false;
		private double currentSigma;
		final List< GaussianFilterFactory< DoubleType, DoubleType > > gaussFactories;

		public STIMCard(
				final List< GaussianFilterFactory< DoubleType, DoubleType > > gaussFactories,
				final double medianDistance,
				final BdvHandle bdvhandle )
		{
			System.out.println( "Setting up panel ... " );

			this.gaussFactories = gaussFactories;
			this.panel = new JPanel(new MigLayout("gap 0, ins 5 5 5 0, fill", "[right][grow]", "center"));

			currentSigma = gaussFactories.get( 0 ).getSigma()/medianDistance;
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
					for ( final GaussianFilterFactory< DoubleType, DoubleType > gaussFactory : gaussFactories )
						if ( gaussFactory != null ) // could vanish when genes are removed
							gaussFactory.setSigma( ( currentSigma = sigmaSlider.getValue().getValue() ) * medianDistance );

					bdvhandle.getViewerPanel().requestRepaint();
				}
			} );

			System.out.println( "Adding Popups ... " );

			final JPopupMenu menu = new JPopupMenu();
			menu.add(runnableItem("set bounds ...", sigmaSlider::setBoundsDialog));
			sigmaSlider.setPopup(() -> menu);

			System.out.println( "Done ... " );
		}

		public List< GaussianFilterFactory< DoubleType, DoubleType > > gaussFactories() { return gaussFactories; }

		public double currentSigma() { return currentSigma; }
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
