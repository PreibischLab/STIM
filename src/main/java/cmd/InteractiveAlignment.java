package cmd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.SwingUtilities;

import align.Pairwise;
import analyze.Entropy;
import analyze.ExtractGeneLists;
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
import gui.bdv.STIMCardManualAlign;
import io.SpatialDataContainer;
import io.SpatialDataIO;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import util.Threads;
import org.apache.logging.log4j.Logger;
import util.LoggerUtil;

// -c /Users/preibischs/Documents/BIMSB/Publications/imglib2-st/slide-seq/raw/slide-seq.n5 -d1 Puck_180531_22.n5 -d2 Puck_180531_23.n5 -n 4 -sk 2
// -c /Users/preibischs/Documents/BIMSB/Publications/imglib2-st/slide-seq/raw/slide-seq.n5 -d1 Puck_180602_15.n5 -d2 Puck_180602_16.n5 -n 4 -sk 2
// -c visium.n5 -d1 slice1.h5ad -d2 slice2.n5 -n 9 -sk 14

// align all
//-c /Users/preibischs/Documents/BIMSB/Publications/imglib2-st/slide-seq/raw/slide-seq.n5 -d1 Puck_180528_22.n5 -d2 Puck_180528_20.n5 -n 4 -sk 2 // huge shift
//-c /Users/preibischs/Documents/BIMSB/Publications/imglib2-st/slide-seq/raw/slide-seq.n5 -d1 Puck_180531_13.n5 -d2 Puck_180528_22.n5 -n 4 -sk 2
//-c /Users/preibischs/Documents/BIMSB/Publications/imglib2-st/slide-seq/raw/slide-seq.n5 -d1 Puck_180531_17.n5 -d2 Puck_180531_13.n5 -n 4 -sk 2
//-c /Users/preibischs/Documents/BIMSB/Publications/imglib2-st/slide-seq/raw/slide-seq.n5 -d1 Puck_180531_18.n5 -d2 Puck_180531_17.n5 -n 4 -sk 2
//-c /Users/preibischs/Documents/BIMSB/Publications/imglib2-st/slide-seq/raw/slide-seq.n5 -d1 Puck_180531_19.n5 -d2 Puck_180531_18.n5 -n 4 -sk 2 --ffSingleSpot 1.5
//-c /Users/preibischs/Documents/BIMSB/Publications/imglib2-st/slide-seq/raw/slide-seq.n5 -d1 Puck_180531_22.n5 -d2 Puck_180531_19.n5 -n 8 -sk 2 --ffSingleSpot 1.5
//-c /Users/preibischs/Documents/BIMSB/Publications/imglib2-st/slide-seq/raw/slide-seq.n5 -d1 Puck_180531_23.n5 -d2 Puck_180531_22.n5 -n 8 -sk 2 --ffSingleSpot 1.5
//-c /Users/preibischs/Documents/BIMSB/Publications/imglib2-st/slide-seq/raw/slide-seq.n5 -d1 Puck_180602_15.n5 -d2 Puck_180531_23.n5 -n 8 -sk 2 --ffSingleSpot 1.5
//-c /Users/preibischs/Documents/BIMSB/Publications/imglib2-st/slide-seq/raw/slide-seq.n5 -d1 Puck_180602_16.n5 -d2 Puck_180602_15.n5 -n 8 -sk 2 --ffSingleSpot 1.5
//-c /Users/preibischs/Documents/BIMSB/Publications/imglib2-st/slide-seq/raw/slide-seq.n5 -d1 Puck_180602_17.n5 -d2 Puck_180602_16.n5 -n 8 -sk 2 --ffSingleSpot 1.5
//-c /Users/preibischs/Documents/BIMSB/Publications/imglib2-st/slide-seq/raw/slide-seq.n5 -d1 Puck_180602_18.n5 -d2 Puck_180602_17.n5 -n 8 -sk 2 --ffSingleSpot 1.5
//-c /Users/preibischs/Documents/BIMSB/Publications/imglib2-st/slide-seq/raw/slide-seq.n5 -d1 Puck_180602_20.n5 -d2 Puck_180602_18.n5 -n 8 -sk 2 --ffSingleSpot 1.5
public class InteractiveAlignment implements Callable<Void> {
	
	private static final Logger logger = LoggerUtil.getLogger();
	@Option(names = {"-c", "--container"}, required = true, description = "input N5 container path, e.g. -c /home/ssq.n5.")
	private String inputPath = null;

	@Option(names = {"-f", "--fixed"}, required = false, description = "the fixed dataset (slice), e.g. -f 'Puck_180528_20'")
	private String fixedDataset = null;

	@Option(names = {"-m", "--moving"}, required = false, description = "the moving dataset (slice), e.g. -m 'Puck_180528_22'")
	private String movingDataset = null;

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

		if (!SpatialDataContainer.isCompatibleContainer(inputPath)) {
			logger.error("'{}' is not a container. Stopping.", inputPath);
			return null;
		}

		if (numGenes <= 0) {
			logger.error("Number of genes must be greater than 0 (is {}). Stopping.", numGenes);
			return null;
		}

		// we might save the transformation, so open for writing
		final SpatialDataContainer container = SpatialDataContainer.openExisting(inputPath, service);

		logger.info("Opening dataset '{}' in '{}' ...", movingDataset, inputPath);

		final SpatialDataIO io1 = container.openDataset(movingDataset);
		final STDataAssembly data1 = io1.readData();

		//data1.transform().set( new AffineTransform2D() );
		logger.debug("Current transform: {}", data1.transform());

		logger.info("Opening dataset '{}' in '{}' ...", fixedDataset, inputPath);

		final SpatialDataIO io2 = container.openDataset(fixedDataset);
		final STDataAssembly data2 = io2.readData();

		//data2.transform().set( new AffineTransform2D() );
		logger.debug("Current transform: {}", data2.transform());

		//
		// assemble genes to test
		//
		logger.info("Assembling initial genes for alignment ({} genes)...", numGenes);

		final String stdevLabel = Entropy.STDEV.label();
		final List<STDataAssembly> dataToAlign = Arrays.asList(data1, data2);
		final List<String> datasetNames = Arrays.asList(movingDataset, fixedDataset);

		// ensure that the standard deviation of genes is present for all datasets
		logger.info("Retrieving standard deviation of genes for all sections");
		for (int i = 0; i < dataToAlign.size(); ++i) {
			final STDataAssembly stData = dataToAlign.get(i);
			final String datasetName = datasetNames.get(i);

			final RandomAccessibleInterval<DoubleType> entropyValues;
			if (container.hasEntropyValues(datasetName, Entropy.STDEV)) {
				logger.info("Standard deviation of genes for {} already computed. Loading.", datasetName);
				entropyValues = container.loadEntropyValues(datasetName, Entropy.STDEV);
			} else {
				logger.info("Computing standard deviation of genes for {} (may take a while)", datasetName);
				entropyValues = ExtractGeneLists.computeOrderedEntropy(stData.data(), Entropy.STDEV, Threads.numThreads());
				container.saveEntropyValues(entropyValues, datasetName, Entropy.STDEV);
			}
			stData.data().getGeneAnnotations().put(stdevLabel, entropyValues);
		}

		final List<Pair<String, Double>> allGenes = Pairwise.allGenes(data1.data(), data2.data(), stdevLabel);

		if (allGenes.isEmpty()) {
			logger.error("No common genes between both datasets. Stopping.");
			return null;
		} else {
			logger.debug("Automatically identified {} genes that can be used for alignment", allGenes.size());
		}

		BdvStackSource< ? > lastSource = null;
		final HashMap< String, List< AddedGene > > sourceData = new HashMap<>();

		logger.info( "Starting BDV ... " );
		logger.info("Starting with the top {} genes after skipping the first {} genes (you find them in the 'groups' panel, you can add/remove genes in the GUI.", numGenes, skipFirstNGenes);

		for ( int i = skipFirstNGenes; i < numGenes + skipFirstNGenes; ++i )
		{
			final String gene = allGenes.get( i ).getA(); //"Calm2";
			logger.info("Rendering gene (each available as its own source): {}", gene);

			final AddedGene addedGene1 = AddedGene.addGene(
					inputPath,
					movingDataset,
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
					fixedDataset,
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
		logger.debug("Median distance of spots: {}", medianDistance);

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
				new STIMCardAlignSIFT(movingDataset, fixedDataset, card, cardFilter, service );

		// add STIMCardAlignICP panel
		final STIMCardAlignICP cardAlignICP =
				new STIMCardAlignICP(movingDataset, fixedDataset, overlay, card, cardFilter, cardAlignSIFT, service );

		cardAlignSIFT.setICPCard( cardAlignICP );

		final STIMCardManualAlign cardAlignManual = new STIMCardManualAlign(card, cardAlignSIFT, cardAlignICP);
		
		cardAlignSIFT.setManualAlignCard( cardAlignManual );
		cardAlignICP.setManualAlignCard( cardAlignManual );

		final BdvStackSource<?> source = lastSource;

		SwingUtilities.invokeLater( () ->
		{
			source.getBdvHandle().getCardPanel().addCard( "Manual Alignment", "Manual Alignment", cardAlignManual.getPanel(), false );
			source.getBdvHandle().getCardPanel().addCard( "SIFT Alignment", "SIFT Alignment", cardAlignSIFT.getPanel(), true );
			source.getBdvHandle().getCardPanel().addCard( "ICP Alignment", "ICP Alignment", cardAlignICP.getPanel(), false );
		});

		// Expands the split Panel (after waiting 2 secs for the BDV to calm down)
		//SimpleMultiThreading.threadWait( 2000 );
		SwingUtilities.invokeLater( () -> splitPanel.setCollapsed(false) );

		// TODO: BDV should call the transform listener
		//SimpleMultiThreading.threadWait( 2000 );
		SwingUtilities.invokeLater(cardAlignSIFT::updateMaxOctaveSize);

		logger.debug("done");

		// service is used in alignment
		//service.shutdown();

		return null;
	}

	public static void main(final String... args) {
		final CommandLine cmd = new CommandLine(new InteractiveAlignment());
		cmd.execute(args);
	}
}
