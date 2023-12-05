package cmd;

import java.awt.Font;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

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
import bdv.viewer.SourceGroup;
import bdv.viewer.SynchronizedViewerState;
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
import mpicbg.models.Affine2D;
import mpicbg.models.AffineModel2D;
import mpicbg.models.InterpolatedAffineModel2D;
import mpicbg.models.Model;
import mpicbg.models.RigidModel2D;
import mpicbg.models.SimilarityModel2D;
import mpicbg.models.TranslationModel2D;
import net.imglib2.Interval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.miginfocom.swing.MigLayout;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import render.Render;
import util.BDVUtils;
import util.BoundedValue;
import util.BoundedValuePanel;
import util.BoundedValuePanel.ChangeListener;
import util.Threads;

// -i visium.n5 -d1 slice1.h5ad -d2 slice2.n5 -n 9 -sk 14
public class InteractiveAlignment implements Callable<Void> {

	@Option(names = {"-i", "--input"}, required = true, description = "input N5 container path, e.g. -i /home/ssq.n5.")
	private String inputPath = null;

	@Option(names = {"-d1", "--dataset1"}, required = false, description = "the first dataset (slice), e.g. -d1 'Puck_180528_20'")
	private String dataset1 = null;

	@Option(names = {"-d2", "--dataset2"}, required = false, description = "the second dataset (slice), e.g. -d2 'Puck_180528_22'")
	private String dataset2 = null;

	@Option(names = {"-s", "--scale"}, required = false, description = "initial scaling factor for rendering the coordinates into images, can be changed interactively (default: 0.05 for slideseq data)")
	private double scale = 0.05;

	@Option(names = {"-bmin", "--brightnessMin"}, required = false, description = "min initial brightness relative to the maximal value + overall min intensity (default: 0.0)")
	private double brightnessMin = 0.0;

	@Option(names = {"-bmax", "--brightnessMax"}, required = false, description = "max initial brightness relative to the maximal value (default: 0.5)")
	private double brightnessMax = 0.5;

	@Option(names = {"-sf", "--smoothnessFactor"}, required = false, description = "initial factor for the sigma of the gaussian used for rendering, corresponds to smoothness, can be changed interactively, e.g -sf 2.0 (default: 4.0)")
	private double smoothnessFactor = 1.0;

	@Option(names = {"-n", "--numGenes"}, required = false, description = "initial number of genes for alignment that have the highest entropy (default: 10)")
	private int numGenes = 10;

	@Option(names = {"-sk", "--skip"}, required = false, description = "skips the first N genes when selecting by highest entropy, as they can be outliers (default: 10)")
	private int skipFirstNGenes = 10;

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

		BdvStackSource< ? > lastSource = null;
		//final ArrayList< GaussianFilterFactory< DoubleType, DoubleType > > factories = new ArrayList<>();
		final HashMap< String, Pair< AddedGene, AddedGene > > sourceData = new HashMap<>();

		System.out.println( "Starting BDV ... " );
		System.out.println( "Starting with the top " + numGenes + " genes after skipping the first " + skipFirstNGenes +" genes (you find them in the 'groups' panel, you can add/remove genes in the GUI." );

		for ( int i = skipFirstNGenes; i < numGenes + skipFirstNGenes; ++i )
		{
			final String gene = allGenes.get( i ).getA(); //"Calm2";
			System.out.println( "Rendering gene (each available as its own source): " + gene );

			final AddedGene addedGene1 = AddedGene.addGene(
					lastSource,
					data1,
					gene,
					smoothnessFactor,
					new ARGBType( ARGBType.rgba(0, 255, 0, 0) ),
					brightnessMin,
					brightnessMax );

			final AddedGene addedGene2 = AddedGene.addGene(
					addedGene1.source,
					data2,
					gene,
					smoothnessFactor,
					new ARGBType( ARGBType.rgba(255, 0, 255, 0) ),
					brightnessMin,
					brightnessMax );

			sourceData.put(gene, new ValuePair<>(addedGene1, addedGene2 ) );
			lastSource = addedGene2.source;
		}


		final SynchronizedViewerState state = lastSource.getBdvHandle().getViewerPanel().state();
		final ArrayList< SourceGroup > oldGroups = new ArrayList<>( state.getGroups() );

		final HashMap< String, SourceGroup > geneToBDVSource = new HashMap<>();

		for ( int i = 0; i < numGenes; ++i )
		{
			final String gene = allGenes.get( i + skipFirstNGenes ).getA();

			final SourceGroup handle = new SourceGroup();
			state.addGroup( handle );
			state.setGroupName( handle, gene );
			state.setGroupActive( handle, true );
			state.addSourceToGroup( state.getSources().get(i*2), handle );
			state.addSourceToGroup( state.getSources().get(i*2 + 1), handle );

			geneToBDVSource.put( gene, handle );
		}

		lastSource.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.GROUP );

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
		final SplitPanel splitPanel = lastSource.getBdvHandle().getSplitPanel();

		// add scale (so the right size of the images for alignment can be selected)
		final DisplayScaleOverlay overlay = new DisplayScaleOverlay();
		lastSource.getBdvHandle().getViewerPanel().renderTransformListeners().add(overlay);
		lastSource.getBdvHandle().getViewerPanel().getDisplay().overlays().add(overlay);

		// show scalebar (so the right error can be selected)
		lastSource.getBdvHandle().getAppearanceManager().appearance().setShowScaleBar( true );

		// collapse all existing panels (except sources)
		lastSource.getBdvHandle().getCardPanel().setCardExpanded(bdv.ui.BdvDefaultCards.DEFAULT_SOURCEGROUPS_CARD, true); // collapse groups panel
		lastSource.getBdvHandle().getCardPanel().setCardExpanded(bdv.ui.BdvDefaultCards.DEFAULT_SOURCES_CARD, false); // collapse sources panel
		lastSource.getBdvHandle().getCardPanel().setCardExpanded(bdv.ui.BdvDefaultCards.DEFAULT_VIEWERMODES_CARD, false); // collapse display modes panel

		// add STIMCard panel
		final STIMCard card = new STIMCard( sourceData, geneToBDVSource, medianDistance, smoothnessFactor, brightnessMin, brightnessMax, lastSource.getBdvHandle());
		lastSource.getBdvHandle().getCardPanel().addCard( "STIM Display Options", "STIM Display Options", card.getPanel(), true );

		// add STIMAlignmentCard panel
		final STIMAlignmentCard cardAlign =
				new STIMAlignmentCard(
						data1, data2, overlay, card, allGenes, sourceData, geneToBDVSource, medianDistance, medianDistance*1.5, 25, 10, numGenes, lastSource.getBdvHandle(), service );
		lastSource.getBdvHandle().getCardPanel().addCard( "SIFT Alignment", "SIFT Alignment", cardAlign.getPanel(), true );

		// Expands the split Panel (after waiting 2 secs for the BDV to calm down)
		SimpleMultiThreading.threadWait( 2000 );
		splitPanel.setCollapsed(false);

		System.out.println("done");

		// service is used in alignment
		//service.shutdown();

		return null;
	}

	public class STIMAlignmentCard
	{
		private final JPanel panel;
		private GeneSelectionExplorer gse = null;
		private final SIFTOverlay siftoverlay;

		final String optionsModel[] = { "Translation", "Rigid", "Similarity", "Affine" };
		final String optionsModelReg[] = { "No Reg.", "Transl.", "Rigid", "Simil.", "Affine" };

		public STIMAlignmentCard(
				final STDataAssembly data1,
				final STDataAssembly data2,
				final DisplayScaleOverlay overlay,
				final STIMCard stimcard,
				final List< Pair< String, Double > > allGenes,
				final HashMap< String, Pair< AddedGene, AddedGene > > sourceData,
				final HashMap< String, SourceGroup > geneToBDVSource,
				final double medianDistance,
				final double errorInit,
				final int minNumInliersInit,
				final int minNumInliersGeneInit,
				final int numGenesInit,
				final BdvHandle bdvhandle,
				final ExecutorService service )
		{
			this.siftoverlay = new SIFTOverlay( new ArrayList<>(), bdvhandle );
			this.panel = new JPanel(new MigLayout("gap 0, ins 5 5 5 5, fill", "[right][grow]", "center"));

			final String options[] = { "Fast", "Normal", "Thorough", "Very thorough" }; // TODO: Advanced with window popping up
			final JComboBox< String > box = new JComboBox< String > (options);
			box.setBorder( null );
			box.setSelectedIndex( 1 );
			final JLabel boxLabel = new JLabel("SIFT Matching ");
			panel.add( boxLabel, "aligny baseline" );
			panel.add( box, "growx, wrap" );

			final BoundedValuePanel maxErrorSlider = new BoundedValuePanel(new BoundedValue(0, Math.round( Math.ceil( medianDistance * 5 ) ), errorInit ));
			maxErrorSlider.setBorder(null);
			final JLabel maxErrorLabel = new JLabel("max. error (px)");
			panel.add(maxErrorLabel, "aligny baseline");
			panel.add(maxErrorSlider, "growx, wrap");

			final BoundedValuePanel inliersSlider = new BoundedValuePanel(new BoundedValue(0, 50, minNumInliersInit ));
			inliersSlider.setBorder(null);
			final JLabel inliersLabel = new JLabel("min inliers (total)");
			final Font font = inliersLabel.getFont().deriveFont( 10f );
			inliersLabel.setFont( font );
			inliersLabel.setBorder( null );
			panel.add(inliersLabel, "aligny baseline");
			panel.add(inliersSlider, "growx, wrap");

			final BoundedValuePanel inliersPerGeneSlider = new BoundedValuePanel(new BoundedValue(0, 25, minNumInliersGeneInit ));
			inliersPerGeneSlider.setBorder(null);
			final JLabel inliersPerGeneLabel = new JLabel("min inliers (gene)");
			inliersPerGeneLabel.setFont( font );
			inliersPerGeneLabel.setBorder( null );
			panel.add(inliersPerGeneLabel, "aligny baseline");
			panel.add(inliersPerGeneSlider, "growx, wrap");

			// Panel for RANSAC MODEL
			final JComboBox< String > boxModelRANSAC1 = new JComboBox< String > (optionsModel);
			boxModelRANSAC1.setSelectedIndex( 1 );
			final JLabel boxModeRANSACLabel1 = new JLabel("RANSAC model ");
			panel.add( boxModeRANSACLabel1, "aligny baseline, sy 2" );
			panel.add( boxModelRANSAC1, "growx, wrap" );
			final JPanel panRANSAC = new JPanel( new MigLayout("gap 0, ins 0 0 0 0, fill", "[right][grow]", "center") );
			final JComboBox< String > boxModelRANSAC2 = new JComboBox< String > (optionsModelReg);
			boxModelRANSAC2.setSelectedIndex( 0 );
			panRANSAC.add( boxModelRANSAC2 );
			final JLabel labelRANSACReg = new JLabel( "λ=" );
			panRANSAC.add( labelRANSACReg, "alignx right" );
			final JTextField tfRANSAC = new JTextField( "0.1" );
			panRANSAC.add( tfRANSAC, "growx" );
			panRANSAC.setBorder( BorderFactory.createEmptyBorder(0,0,5,0));
			panel.add( panRANSAC, "growx, wrap" );

			// Panel for FINAL MODEL
			final JComboBox< String > boxModelFinal1 = new JComboBox< String > (optionsModel);
			boxModelFinal1.setSelectedIndex( 1 );
			final JLabel boxModeFinalLabel1 = new JLabel("Final model ");
			panel.add( boxModeFinalLabel1, "aligny baseline, sy 2" );
			panel.add( boxModelFinal1, "growx, wrap" );
			final JPanel panFinal = new JPanel( new MigLayout("gap 0, ins 0 0 0 0, fill", "[right][grow]", "center") );
			final JComboBox< String > boxModelFinal2 = new JComboBox< String > (optionsModelReg);
			boxModelFinal2.setSelectedIndex( 0 );
			panFinal.add( boxModelFinal2 );
			final JLabel labelFinalReg = new JLabel( "λ=" );
			panFinal.add( labelFinalReg, "alignx right" );
			final JTextField tfFinal = new JTextField( "0.1" );
			panFinal.add( tfFinal, "growx" );
			panFinal.setBorder( BorderFactory.createEmptyBorder(0,0,5,0));
			panel.add( panFinal, "growx, wrap" );

			final JCheckBox overlayInliers = new JCheckBox( "Overlay SIFT features" );
			panel.add(overlayInliers, "span,growx,pushy");
			overlayInliers.setSelected( true );
			overlayInliers.setEnabled( false );
			overlayInliers.addChangeListener( e ->
			{
				if ( !overlayInliers.isSelected() )
				{
					bdvhandle.getViewerPanel().renderTransformListeners().remove( siftoverlay );
					bdvhandle.getViewerPanel().getDisplay().overlays().remove( siftoverlay );
				}
				else
				{
					bdvhandle.getViewerPanel().renderTransformListeners().add( siftoverlay );
					bdvhandle.getViewerPanel().getDisplay().overlays().add( siftoverlay );
				}
				bdvhandle.getViewerPanel().requestRepaint();
			});

			final JProgressBar bar = new JProgressBar(SwingConstants.HORIZONTAL, 0, 100);
			bar.setValue( 0 );
			bar.setStringPainted(false);
			panel.add(bar, "span,growx,pushy");

			final JButton add = new JButton("Add genes");
			panel.add(add, "aligny baseline");
			final JButton run = new JButton("Run SIFT");
			panel.add(run, "growx, wrap");

			//
			// Run SIFT alignment
			//
			run.addActionListener( l ->
			{
				siftoverlay.setInliers( new ArrayList<>() );
				bdvhandle.getViewerPanel().renderTransformListeners().remove( siftoverlay );
				bdvhandle.getViewerPanel().getDisplay().overlays().remove( siftoverlay );
				run.setEnabled( false );
				add.setEnabled( false );
				overlayInliers.setEnabled( false );
				bar.setValue( 1 );

				new Thread( () ->
				{
					final SynchronizedViewerState state = bdvhandle.getViewerPanel().state();
					AddedGene.updateRemainingSources( state, geneToBDVSource, sourceData );
	
					final int minInliers = (int)Math.round( inliersSlider.getValue().getValue() );
					final int minInliersPerGene = (int)Math.round( inliersPerGeneSlider.getValue().getValue() );
					final double maxError = maxErrorSlider.getValue().getValue();
					final double scale = overlay.currentScale();
					final double sigma = stimcard.currentSigma();
					final double lambda1 = Double.parseDouble( tfRANSAC.getText().trim() );
					final double lambda2 = Double.parseDouble( tfFinal.getText().trim() );
					final SIFTParam p = new SIFTParam( SIFTMatching.values()[ box.getSelectedIndex() ] );

					final Model model1 = getModelFor( boxModelRANSAC1.getSelectedIndex(), boxModelRANSAC2.getSelectedIndex(), lambda1 );
					final Model model2 = getModelFor( boxModelFinal1.getSelectedIndex(), boxModelFinal2.getSelectedIndex(), lambda2 );

					System.out.println( "Running SIFT align with the following parameters: ");
					System.out.println( "maxError: " + maxError + ", minInliers (over all genes): " + minInliers + ", minInliers (per genes): " + minInliersPerGene );
					System.out.println( "scale: " + scale + ", sigma: " + sigma );
					System.out.println( "SIFT: " + SIFTMatching.values()[ box.getSelectedIndex() ] );
					System.out.println( "RANSAC model: " + optionsModel[ boxModelRANSAC1.getSelectedIndex() ] + ", regularizer: " + optionsModelReg[ boxModelRANSAC2.getSelectedIndex() ] + ", lambda=" + lambda1 );
					System.out.println( "FINAL model: " + optionsModel[ boxModelFinal1.getSelectedIndex() ] + ", regularizer: " + optionsModelReg[ boxModelFinal2.getSelectedIndex() ] + ", lambda=" + lambda2 );

					System.out.println( model1.getClass().getSimpleName() );
					System.out.println( model2.getClass().getSimpleName() );

					final boolean visResult = false;
					final double[] progressBarValue = new double[] { 1.0 };
					
					final SiftMatch match = PairwiseSIFT.pairwiseSIFT(
							data1.data(), dataset1, data2.data(), dataset2,
							(Affine2D & Model)model1, (Affine2D & Model)model1,
							new ArrayList<>( geneToBDVSource.keySet() ),
							p, scale, sigma, maxError,
							minInliers, minInliersPerGene,
							visResult, service, (v) -> {
								synchronized ( this ) {
									progressBarValue[ 0 ] += v;
									bar.setValue( (int)Math.round( progressBarValue[ 0 ] ));
								}
							});
	
					System.out.println( match.getNumInliers() + "/" + match.getNumCandidates() );
	
					// TODO: print out cmd-line args
	
					// 
					// apply transformations
					//
					if ( match.getInliers().size() > 0 )
					{
						try
						{
							//final RigidModel2D model = new RigidModel2D();
							//final AffineModel2D model = new AffineModel2D();
							model2.fit( match.getInliers() );
							final AffineTransform2D m = AlignTools.modelToAffineTransform2D( (Affine2D)model2 ).inverse();
							final AffineTransform3D m3d = new AffineTransform3D();
							m3d.set(m.get(0, 0), 0, 0 ); // row, column
							m3d.set(m.get(0, 1), 0, 1 ); // row, column
							m3d.set(m.get(1, 0), 1, 0 ); // row, column
							m3d.set(m.get(1, 1), 1, 1 ); // row, column
							m3d.set(m.get(0, 2), 0, 3 ); // row, column
							m3d.set(m.get(1, 2), 1, 3 ); // row, column
	
							System.out.println( "final model" + m );
							//System.out.println( m3d );
	
							final List<TransformedSource<?>> tsources = BDVUtils.getTransformedSources(state);
	
							// every second source will be transformed
							for ( int i = 1; i < tsources.size(); i = i + 2 )
								tsources.get( i ).setFixedTransform( m3d );

						} catch (Exception e)
						{
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

						//
						// Overlay detections
						//
						siftoverlay.setInliers( match.getInliers() );

						// Very good to know!
						//for ( final PointMatch pm : match.getInliers() )
						//	System.out.println( ((PointST) pm.getP1()).getGene() );

						overlayInliers.setSelected( true );
						overlayInliers.setEnabled( true );
					}
					else
					{
						siftoverlay.setInliers( new ArrayList<>() );
						overlayInliers.setEnabled( false );
					}

					bar.setValue( 100 );
					add.setEnabled( true );
					run.setEnabled( true );
					bdvhandle.getViewerPanel().requestRepaint();
				}).start();

			});

			//
			// Add genes ...
			//
			add.addActionListener( l -> 
			{
				if ( gse == null || gse.frame().isVisible() == false )
					gse = new GeneSelectionExplorer(
						allGenes,
						list ->
						{
							//
							// first check if all groups are still present that are in the HashMap
							//
							final SynchronizedViewerState state = bdvhandle.getViewerPanel().state();
							AddedGene.updateRemainingSources( state, geneToBDVSource, sourceData );

							//
							// Now add the new ones (if it's not already there)
							//
							for ( final String gene : list )
							{
								if ( !geneToBDVSource.containsKey( gene ) )
								{
									System.out.println( "Gene " + gene + " will be added." );

									final AddedGene gene1 = AddedGene.addGene( bdvhandle, data1, gene, stimcard.currentSigma(), new ARGBType( ARGBType.rgba(0, 255, 0, 0) ), stimcard.currentBrightnessMin(), stimcard.currentBrightnessMax() );
									final AddedGene gene2 = AddedGene.addGene( bdvhandle, data2, gene, stimcard.currentSigma(), new ARGBType( ARGBType.rgba(255, 0, 255, 0) ), stimcard.currentBrightnessMin(), stimcard.currentBrightnessMax() );

									stimcard.sourceData().put( gene, new ValuePair<>( gene1, gene2 ) );
									//stimcard.gaussFactories().add( gene1.factory );
									//stimcard.gaussFactories().add( gene2.factory );

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

		protected Model<?> getModelFor( final int modelIndex, final int regIndex, final double lambda )
		{
			if ( regIndex == 0 )
			{
				if ( modelIndex == 0 )
					return new TranslationModel2D();
				else if ( modelIndex == 1 )
					return new RigidModel2D();
				else if ( modelIndex == 2 )
					return new SimilarityModel2D();
				else if ( modelIndex == 3 )
					return new AffineModel2D();
				else
					throw new RuntimeException( "Unknown model index: "+ modelIndex );
			}
			else if ( regIndex == 1 )
			{
				if ( modelIndex == 0 )
					return new TranslationModel2D();
				else if ( modelIndex == 1 )
					return new InterpolatedAffineModel2D<TranslationModel2D, RigidModel2D>( new TranslationModel2D(), new RigidModel2D(), lambda );
				else if ( modelIndex == 2 )
					return new InterpolatedAffineModel2D<TranslationModel2D, SimilarityModel2D>( new TranslationModel2D(), new SimilarityModel2D(), lambda );
				else if ( modelIndex == 3 )
					return new InterpolatedAffineModel2D<TranslationModel2D, AffineModel2D>( new TranslationModel2D(), new AffineModel2D(), lambda );
				else
					throw new RuntimeException( "Unknown model index: "+ modelIndex );
			}
			else if ( regIndex == 2 )
			{
				if ( modelIndex == 0 )
					return new InterpolatedAffineModel2D<RigidModel2D, TranslationModel2D>( new RigidModel2D(), new TranslationModel2D(), lambda );
				else if ( modelIndex == 1 )
					return new RigidModel2D();
				else if ( modelIndex == 2 )
					return new InterpolatedAffineModel2D<RigidModel2D, SimilarityModel2D>( new RigidModel2D(), new SimilarityModel2D(), lambda );
				else if ( modelIndex == 3 )
					return new InterpolatedAffineModel2D<RigidModel2D, AffineModel2D>( new RigidModel2D(), new AffineModel2D(), lambda );
				else
					throw new RuntimeException( "Unknown model index: "+ modelIndex );
			}
			else if ( regIndex == 3 )
			{
				if ( modelIndex == 0 )
					return new InterpolatedAffineModel2D<SimilarityModel2D, TranslationModel2D>( new SimilarityModel2D(), new TranslationModel2D(), lambda );
				else if ( modelIndex == 1 )
					return new InterpolatedAffineModel2D<SimilarityModel2D, RigidModel2D>( new SimilarityModel2D(), new RigidModel2D(), lambda );
				else if ( modelIndex == 2 )
					return new SimilarityModel2D();
				else if ( modelIndex == 3 )
					return new InterpolatedAffineModel2D<SimilarityModel2D, AffineModel2D>( new SimilarityModel2D(), new AffineModel2D(), lambda );
				else
					throw new RuntimeException( "Unknown model index: "+ modelIndex );
			}
			else if ( regIndex == 4 )
			{
				if ( modelIndex == 0 )
					return new InterpolatedAffineModel2D<AffineModel2D, TranslationModel2D>( new AffineModel2D(), new TranslationModel2D(), lambda );
				else if ( modelIndex == 1 )
					return new InterpolatedAffineModel2D<AffineModel2D, RigidModel2D>( new AffineModel2D(), new RigidModel2D(), lambda );
				else if ( modelIndex == 2 )
					return new InterpolatedAffineModel2D<AffineModel2D, SimilarityModel2D>( new AffineModel2D(), new SimilarityModel2D(), lambda );
				else if ( modelIndex == 3 )
					return new AffineModel2D();
				else
					throw new RuntimeException( "Unknown model index: "+ modelIndex );
			}
			else
				throw new RuntimeException( "Unknown regularizer model index: "+ modelIndex );
		}

		public JPanel getPanel() { return panel; }
	}

	public class STIMCard
	{
		private final JPanel panel;
		private final HashMap< String, Pair< AddedGene, AddedGene > > sourceData;
		private final HashMap< String, SourceGroup > geneToBDVSource;
		private double currentSigma, currentBrightnessMin, currentBrightnessMax;

		public STIMCard(
				final HashMap< String, Pair< AddedGene, AddedGene > > sourceData,
				final HashMap< String, SourceGroup > geneToBDVSource,
				final double medianDistance,
				final double initialSigma,
				final double initialBrightnessMin,
				final double initialBrightnessMax,
				final BdvHandle bdvhandle )
		{
			this.sourceData = sourceData;
			this.geneToBDVSource = geneToBDVSource;
			this.currentBrightnessMin = initialBrightnessMin;
			this.currentBrightnessMax = initialBrightnessMax;
			this.currentSigma = initialSigma;

			this.panel = new JPanel(new MigLayout("gap 0, ins 5 5 5 0, fill", "[right][grow]", "center"));

			// sigma slider
			final BoundedValuePanel sigmaSlider = new BoundedValuePanel(new BoundedValue(0, Math.round( Math.ceil( Math.max( 2.5, currentSigma * 1.5 ) ) ), currentSigma ));
			sigmaSlider.setBorder(null);
			final JLabel sigmaLabel = new JLabel("sigma (-sf)");
			panel.add(sigmaLabel, "aligny baseline");
			panel.add(sigmaSlider, "growx, wrap");

			// brightness slider
			final BoundedValuePanel brightnessSliderMin = new BoundedValuePanel(new BoundedValue(0, 1, currentBrightnessMin ));
			final JLabel brightnessLabelMin = new JLabel("brightness (-bmin)");
			final Font font = brightnessLabelMin.getFont().deriveFont( 10f );
			brightnessLabelMin.setFont( font );
			brightnessLabelMin.setBorder( null );
			brightnessSliderMin.setBorder(null);
			panel.add(brightnessLabelMin, "aligny baseline");
			panel.add(brightnessSliderMin, "growx, wrap");

			final BoundedValuePanel brightnessSliderMax = new BoundedValuePanel(new BoundedValue(0, 1, currentBrightnessMax ));
			final JLabel brightnessLabelMax = new JLabel("brightness (-bmax)");
			brightnessLabelMax.setFont( font );
			brightnessLabelMax.setBorder( null );
			brightnessSliderMin.setBorder(null);
			brightnessSliderMax.setBorder(null);
			panel.add(brightnessLabelMax, "aligny baseline");
			panel.add(brightnessSliderMax, "growx, wrap");

			// sigma listener
			sigmaSlider.changeListeners().add( () ->
			{
				final double oldSigma = currentSigma;
				currentSigma = sigmaSlider.getValue().getValue();

				if ( oldSigma != currentSigma )
				{
					final SynchronizedViewerState state = bdvhandle.getViewerPanel().state();
					AddedGene.updateRemainingSources( state, geneToBDVSource, sourceData );
	
					final double actualSigma = currentSigma * medianDistance;
					sourceData.values().forEach( p -> {
						p.getA().factory.setSigma( actualSigma );
						p.getB().factory.setSigma( actualSigma );
					} );
	
					bdvhandle.getViewerPanel().requestRepaint();
				}
			} );

			// brightness listener
			brightnessSliderMin.changeListeners().add( () -> {

				final double oldBrightness = currentBrightnessMin;
				currentBrightnessMin = brightnessSliderMin.getValue().getValue();

				if ( currentBrightnessMin > currentBrightnessMax )
				{
					currentBrightnessMin = currentBrightnessMax;
					brightnessSliderMin.setValue( new BoundedValue(brightnessSliderMin.getValue().getMinBound(), brightnessSliderMin.getValue().getMaxBound(), currentBrightnessMin) );
				}

				if ( oldBrightness != currentBrightnessMin )
				{
					sourceData.values().forEach( p -> {
						final double displayMin = AddedGene.getDisplayMin( p.getA().min, p.getA().max, currentBrightnessMin );
						final double displayMax = AddedGene.getDisplayMax( p.getA().max, currentBrightnessMax );
	
						p.getA().source.setDisplayRange(displayMin, displayMax);
						p.getB().source.setDisplayRange(displayMin, displayMax);
					} );
	
					final SynchronizedViewerState state = bdvhandle.getViewerPanel().state();
					AddedGene.updateRemainingSources( state, geneToBDVSource, sourceData );
	
					bdvhandle.getViewerPanel().requestRepaint();
				}
			} );

			brightnessSliderMax.changeListeners().add( () -> {

				final double oldBrightness = currentBrightnessMax;
				currentBrightnessMax = brightnessSliderMax.getValue().getValue();

				if ( currentBrightnessMax < currentBrightnessMin )
				{
					currentBrightnessMax = currentBrightnessMin;
					brightnessSliderMax.setValue( new BoundedValue(brightnessSliderMax.getValue().getMinBound(), brightnessSliderMax.getValue().getMaxBound(), currentBrightnessMax) );
				}

				if ( oldBrightness != currentBrightnessMax )
				{
					sourceData.values().forEach( p -> {
						final double displayMin = AddedGene.getDisplayMin( p.getA().min, p.getA().max, currentBrightnessMin );
						final double displayMax = AddedGene.getDisplayMax( p.getA().max, currentBrightnessMax );
	
						p.getA().source.setDisplayRange(displayMin, displayMax);
						p.getB().source.setDisplayRange(displayMin, displayMax);
					} );
	
					final SynchronizedViewerState state = bdvhandle.getViewerPanel().state();
					AddedGene.updateRemainingSources( state, geneToBDVSource, sourceData );
	
					bdvhandle.getViewerPanel().requestRepaint();
				}
			} );

			// popups
			final JPopupMenu menu = new JPopupMenu();
			menu.add(runnableItem("set bounds ...", sigmaSlider::setBoundsDialog));
			sigmaSlider.setPopup(() -> menu);

			System.out.println( "Done ... " );
		}

		public HashMap< String, Pair< AddedGene, AddedGene > > sourceData() { return sourceData; }
		public HashMap< String, SourceGroup > geneToBDVSource() { return geneToBDVSource; }
		public double currentBrightnessMin() { return currentBrightnessMin; }
		public double currentBrightnessMax() { return currentBrightnessMax; }
		public double currentSigma() { return currentSigma; }
		public JPanel getPanel() { return panel; }

		private JMenuItem runnableItem(final String text, final Runnable action) {
			final JMenuItem item = new JMenuItem(text);
			item.addActionListener(e -> action.run());
			return item;
		}
	}

	protected static class AddedGene
	{
		final GaussianFilterFactory< DoubleType, DoubleType > factory;
		final BdvStackSource<?> source;
		final double min, max;

		public AddedGene(
				final GaussianFilterFactory< DoubleType, DoubleType > factory,
				final BdvStackSource<?> source,
				final double min,
				final double max )
		{
			this.factory = factory;
			this.source = source;
			this.min = min;
			this.max = max;
		}

		public static synchronized void updateRemainingSources(
				final SynchronizedViewerState state,
				final Map< String, SourceGroup > geneToBDVSource,
				final Map< String, Pair< AddedGene, AddedGene > > sourceData )
		{
			final ArrayList< SourceGroup > currentGroups = new ArrayList<>( state.getGroups() );

			final ArrayList< String > toRemove = new ArrayList<>();
			for ( final Entry<String, SourceGroup > entry : geneToBDVSource.entrySet() )
				if ( !currentGroups.contains( entry.getValue() ) )
					toRemove.add( entry.getKey() );

			toRemove.forEach( s -> {
				geneToBDVSource.remove( s );
				sourceData.remove( s );
				} );
		}

		public static double getDisplayMin( final double min, final double max, final double bMin ) { return min + max * bMin; }
		public static double getDisplayMax( final double max, final double bMax ) { return max * bMax; }

		public static AddedGene addGene(
				final Bdv bdv,
				final STDataAssembly data,
				final String gene,
				final double smoothnessFactor,
				final ARGBType color,
				final double relativeInitialBrightnessMin,
				final double relativeInitialBrightnessMax )
		{
			double min = Double.MAX_VALUE;
			double max = -Double.MAX_VALUE;

			for ( final DoubleType t : data.data().getExprData(gene) )
			{
				min = Math.min( min, t.get() );
				max = Math.max( max, t.get() );
			}

			final double minDisplay = getDisplayMin( min, max, relativeInitialBrightnessMin );
			final double maxDisplay = getDisplayMax( max, relativeInitialBrightnessMax );

			System.out.println( "min/max: " + min + "/" + max );
			System.out.println( "min/max display range: " + minDisplay + "/" + maxDisplay );

			final List< FilterFactory< DoubleType, DoubleType > > filterFactorys = new ArrayList<>();
			//filterFactorys.add( new MedianFilterFactory<DoubleType>( new DoubleType(), 3 * medianDistance ) );

			final Pair< RealRandomAccessible< DoubleType >, GaussianFilterFactory< DoubleType, DoubleType > > rendered = 
					Render.getRealRandomAccessible2( data, gene, smoothnessFactor, filterFactorys );

			final RealRandomAccessible< DoubleType > rra = rendered.getA();
			final GaussianFilterFactory< DoubleType, DoubleType > factory = rendered.getB();

			final Interval interval =
						STDataUtils.getIterableInterval(
								new TransformedIterableRealInterval<>(
										data.data(),
										data.transform() ) );

			BdvOptions options = BdvOptions.options().numRenderingThreads(Math.max(2,Runtime.getRuntime().availableProcessors() / 2))
					.addTo(bdv).is2D().preferredSize(1000, 800);

			BdvStackSource< ? > source = BdvFunctions.show( rra, interval, gene, options );

			source.setDisplayRangeBounds( 0, max );
			source.setDisplayRange( minDisplay, maxDisplay );
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

			return new AddedGene( factory, source, min, max );
		}
	}

	public static void main(final String... args) {
		CommandLine.call(new InteractiveAlignment(), args);
	}
}
