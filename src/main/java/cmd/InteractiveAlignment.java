package cmd;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import align.AlignTools;
import align.Pairwise;
import bdv.ui.splitpanel.SplitPanel;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import bdv.viewer.SourceGroup;
import bdv.viewer.SynchronizedViewerState;
import gui.DisplayScaleOverlay;
import gui.STDataAssembly;
import gui.bdv.AddedGene;
import gui.bdv.AddedGene.Rendering;
import gui.bdv.STIMCard;
import gui.bdv.STIMCardAlignICP;
import gui.bdv.STIMCardAlignSIFT;
import gui.bdv.STIMCardFilter;
import io.SpatialDataContainer;
import io.SpatialDataIO;
import io.TextFileAccess;
import mpicbg.models.AffineModel2D;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import picocli.CommandLine;
import picocli.CommandLine.Option;
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

	@Option(names = {"-rf", "--renderingFactor"}, required = false, description = "factor for the amount of filtering or radius used for rendering, corresponds to smoothness for Gauss, e.g -rf 2.0 (default: 1.0)")
	private double renderingFactor = 1.0;

	@Option(names = {"--rendering"}, required = false, description = "inital rendering type (Gauss, Mean, NearestNeighbor, Linear), e.g --rendering Gauss (default: Gauss)")
	private Rendering rendering = Rendering.Gauss;

	@Option(names = {"-n", "--numGenes"}, required = false, description = "initial number of genes for alignment that have the highest entropy (default: 10)")
	private int numGenes = 10;

	@Option(names = {"-sk", "--skip"}, required = false, description = "skips the first N genes when selecting by highest entropy, as they can be outliers (default: 10)")
	private int skipFirstNGenes = 10;

	@Option(names = {"--ffSingleSpot"}, required = false, description = "filter single spots using the median distance between all spots as threshold, e.g. --ffSingleSpot 1.5 (default: no filtering)")
	private Double ffSingleSpot = null;

	@Option(names = {"--ffMedian"}, required = false, description = "median-filter all spots using a given radius, e.g --ffMedian 5.0 (default: no filtering)")
	private Double ffMedian = null;

	@Option(names = {"--ffGauss"}, required = false, description = "Gauss-filter all spots using a given radius, e.g --ffGauss 2.0 (default: no filtering)")
	private Double ffGauss = null;

	@Option(names = {"--ffMean"}, required = false, description = "mean/avg-filter all spots using a given radius, e.g --ffMean 2.5 (default: no filtering)")
	private Double ffMean = null;

	@Override
	public Void call() throws Exception
	{
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

		final SpatialDataIO io2 = container.openDatasetReadOnly( dataset2 );
		final STDataAssembly data2 = io2.readData();

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

		BdvStackSource< ? > lastSource = null;
		final HashMap< String, List< AddedGene > > sourceData = new HashMap<>();

		System.out.println( "Starting BDV ... " );
		System.out.println( "Starting with the top " + numGenes + " genes after skipping the first " + skipFirstNGenes +" genes (you find them in the 'groups' panel, you can add/remove genes in the GUI." );

		for ( int i = skipFirstNGenes; i < numGenes + skipFirstNGenes; ++i )
		{
			final String gene = allGenes.get( i ).getA(); //"Calm2";
			System.out.println( "Rendering gene (each available as its own source): " + gene );

			final AddedGene addedGene1 = AddedGene.addGene(
					inputPath,
					dataset1,
					rendering,
					lastSource,
					data1,
					null,
					gene,
					renderingFactor,
					new ARGBType( ARGBType.rgba(0, 255, 0, 0) ),
					brightnessMin,
					brightnessMax );

			final AddedGene addedGene2 = AddedGene.addGene(
					inputPath,
					dataset2,
					rendering,
					addedGene1.source(),
					data2,
					null,
					gene,
					renderingFactor,
					new ARGBType( ARGBType.rgba(255, 0, 255, 0) ),
					brightnessMin,
					brightnessMax );

			sourceData.put(gene, new ArrayList<>(Arrays.asList( addedGene1, addedGene2 ) ) );
			lastSource = addedGene2.source();
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
		final STIMCard card =
				new STIMCard(
						new ArrayList<>(Arrays.asList( data1, data2 ) ), allGenes, sourceData, geneToBDVSource, overlay,
						medianDistance, rendering, renderingFactor, brightnessMin, brightnessMax, lastSource.getBdvHandle());
		lastSource.getBdvHandle().getCardPanel().addCard( "STIM Display Options", "STIM Display Options", card.getPanel(), true );

		// add STIMCardFilter panel
		final STIMCardFilter cardFilter = new STIMCardFilter( card, ffSingleSpot, ffMedian, ffGauss, ffMean, service );
		lastSource.getBdvHandle().getCardPanel().addCard( "STIM Filtering Options", "STIM Filtering Options", cardFilter.getPanel(), false );

		// add STIMCardAlignSIFT panel
		final STIMCardAlignSIFT cardAlignSIFT =
				new STIMCardAlignSIFT( dataset1, dataset2, card, cardFilter, service );
		lastSource.getBdvHandle().getCardPanel().addCard( "SIFT Alignment", "SIFT Alignment", cardAlignSIFT.getPanel(), true );

		// TODO: REMOVE
		AffineModel2D model = new AffineModel2D();
		model.set(0.323679918598243, -0.9185551542794,  1.002878826719069, 0.351176728134501, -546.6035992226643, 4231.453000084942 );
		cardAlignSIFT.setModel( model );
		card.applyTransformationToBDV( true );
		// TODO: REMOVE

		// add STIMCardAlignICP panel
		final STIMCardAlignICP cardAlignICP =
				new STIMCardAlignICP( dataset1, dataset2, overlay, card, cardAlignSIFT, service );
		lastSource.getBdvHandle().getCardPanel().addCard( "ICP Alignment", "ICP Alignment", cardAlignICP.getPanel(), false );

		cardAlignSIFT.setICPCard( cardAlignICP );
		cardAlignICP.setSIFTCard( cardAlignSIFT );

		// Expands the split Panel (after waiting 2 secs for the BDV to calm down)
		SimpleMultiThreading.threadWait( 2000 );
		splitPanel.setCollapsed(false);

		// TODO: BDV should call the transform listener
		SimpleMultiThreading.threadWait( 2000 );
		cardAlignSIFT.updateMaxOctaveSize();

		System.out.println("done");

		// service is used in alignment
		//service.shutdown();

		return null;
	}

	public static void main(final String... args) {
		CommandLine.call(new InteractiveAlignment(), args);
	}
}
