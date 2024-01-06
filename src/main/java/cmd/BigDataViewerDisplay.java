package cmd;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import bdv.ui.splitpanel.SplitPanel;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import bdv.viewer.SourceGroup;
import bdv.viewer.SynchronizedViewerState;
import data.STDataUtils;
import examples.VisualizeAnnotations;
import filter.FilterFactory;
import gui.DisplayScaleOverlay;
import gui.STDataAssembly;
import gui.bdv.AddedGene;
import gui.bdv.AddedGene.Rendering;
import gui.bdv.STIMCard;
import gui.bdv.STIMCardFilter;
import gui.celltype.CellTypeExplorer;
import imglib2.TransformedIterableRealInterval;
import io.SpatialDataContainer;
import io.SpatialDataIO;
import net.imglib2.Interval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.util.ValuePair;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import render.Render;

@Command(name = "st-bdv-view", mixinStandardHelpOptions = true, version = "0.2.0", description = "Spatial Transcriptomics as IMages project - visualize ST data in BigDataViewer")
public class BigDataViewerDisplay implements Callable<Void>
{
	@Option(names = {"-i", "--input"}, required = true, description = "input file (AnnData) or N5 container, e.g. -i /home/ssq.n5")
	private String inputPath = null;

	@Option(names = {"-g", "--genes"}, required = true, description = "comma separated list of one or more gene to visualize, e.g. -g Calm2,Ubb")
	private String genes = null;

	@Option(names = {"-a", "--annotation"}, required = false, description = "comma separated list of annotations to visualize, e.g. -a celltype")
	private String annotations = null;

	@Option(names = {"-ar", "--annotationRadius"}, required = false, description = "radius of annotation spots as a factor of their median distance, e.g. -ar 2.0 (default: 0.75; in 3d: zSpacing*0.75)")
	private double annotationRadius = 0.75;

	@Option(names = {"-d", "--dataset"}, required = true, description = "dataset to display, e.g. -d Puck_180528_20")
	private String dataset = null;

	@Option(names = {"-bmin", "--brightnessMin"}, required = false, description = "min initial brightness relative to the maximal value + overall min intensity (default: 0.0)")
	private double brightnessMin = 0.0;

	@Option(names = {"-bmax", "--brightnessMax"}, required = false, description = "max initial brightness relative to the maximal value (default: 0.5)")
	private double brightnessMax = 0.5;

	@Option(names = {"--rendering"}, required = false, description = "inital rendering type (Gauss, Mean, NearestNeighbor, Linear), e.g --rendering Gauss (default: Gauss)")
	private Rendering rendering = Rendering.Gauss;

	@Option(names = {"-rf", "--renderingFactor"}, required = false, description = "factor for the amount of filtering or radius used for rendering, corresponds to smoothness for Gauss, e.g -rf 2.0 (default: 1.5)")
	private double renderingFactor = 1.5;

	@Option(names = {"--ffSingleSpot"}, required = false, description = "filter single spots using the median distance between all spots as threshold, e.g. --ffSingleSpot 1.5 (default: no filtering)")
	private Double ffSingleSpot = null;

	@Option(names = {"--ffMedian"}, required = false, description = "median-filter all spots using a given radius, e.g --ffMedian 5.0 (default: no filtering)")
	private Double ffMedian = null;

	@Option(names = {"--ffGauss"}, required = false, description = "Gauss-filter all spots using a given radius, e.g --ffGauss 2.0 (default: no filtering)")
	private Double ffGauss = null;

	@Option(names = {"--ffMean"}, required = false, description = "mean/avg-filter all spots using a given radius, e.g --ffMean 2.5 (default: no filtering)")
	private Double ffMean = null;

	@Override
	public Void call() throws Exception {

		final boolean useTransform = true;

		if (!(new File(inputPath)).exists()) {
			System.out.println("Container / dataset '" + inputPath + "' does not exist. Stopping.");
			return null;
		}

		final ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

		final SpatialDataIO iodata;

		if (SpatialDataContainer.isCompatibleContainer(inputPath))
		{
			final SpatialDataContainer container = SpatialDataContainer.openForReading(inputPath, service);

			if (dataset != null && dataset.trim().length() != 0) {
					System.out.println("Opening dataset '" + dataset + "' in '" + inputPath + "' ...");
					iodata = container.openDatasetReadOnly(dataset.trim());
			}
			else
			{
				System.out.println("No dataset defined. stopping.");
				return null;
			}
		}
		else {
			System.out.println("Opening dataset '" + inputPath + "' ...");
			iodata = SpatialDataIO.openReadOnly(inputPath, service);
		}

		if (genes == null || genes.length() == 0) {
			System.out.println("No genes available. stopping.");
			return null;
		}

		List<String> genesToShow = Arrays.stream(genes.split(",")).map(String::trim).collect(Collectors.toList());
		if (genesToShow.size() == 0) {
			System.out.println("No genes available. stopping.");
			return null;
		}

		System.out.println( genesToShow.size() ) ;

		final STDataAssembly dataToVisualize = iodata.readData();

		if (!useTransform)
			dataToVisualize.transform().set(new AffineTransform2D());

		List< String > annotationList;
		if ( annotations != null && annotations.length() > 0 )
			annotationList = Arrays.asList(annotations.split("," ) );
		else
			annotationList = new ArrayList<>();


		BdvStackSource< ? > source = null;

		// TODO: TEST
		// Display annotations
		//
		for ( final String annotation : annotationList )
		{
			final IntType outofboundsInt = new IntType( -1 );
			final double spotSize = dataToVisualize.statistics().getMedianDistance() * annotationRadius;
			final HashMap<Long, ARGBType > lut = new HashMap<>();

			final List< FilterFactory< IntType, IntType > > filterFactorysInt = new ArrayList<>();
			/*
			if ( singleSpotFilter )
			{
				System.out.println( "Using single-spot filtering, radius="  + (dataToVisualize.get( 0 ).statistics().getMedianDistance() * 1.5) );
				filterFactorysInt.add( new SingleSpotRemovingFilterFactory<>( outofboundsInt, dataToVisualize.get( 0 ).statistics().getMedianDistance() * 1.5 ) );
			}
			*/
			final RealRandomAccessible< IntType > rra;
			final Interval interval;

			// 2d
			rra = VisualizeAnnotations.visualize2d(
					dataToVisualize.data(),
					annotation,
					spotSize,
					dataToVisualize.transform(),
					outofboundsInt,
					filterFactorysInt,
					lut );

			interval = STDataUtils.getIterableInterval(
					new TransformedIterableRealInterval<>(
							dataToVisualize.data(),
							dataToVisualize.transform() ) );

			CellTypeExplorer cte = new CellTypeExplorer( lut );

			final RealRandomAccessible< ARGBType > rraRGB = Render.switchableConvertToRGB( rra, outofboundsInt, new ARGBType(), lut, cte.panel() );

			BdvOptions options = BdvOptions.options().numRenderingThreads( Runtime.getRuntime().availableProcessors() ).addTo( source );
			options = options.is2D();
			source = BdvFunctions.show( rraRGB, interval, annotation, options );
			source.setDisplayRange( 0, 255 );
			source.setDisplayRangeBounds( 0, 2550 );

			cte.panel().setBDV( source.getBdvHandle().getViewerPanel() );
		}

		//
		// Display genes
		//

		// random gene coloring
		Random rnd = new Random( 43 );

		final HashMap< String, List< AddedGene > > sourceData = new HashMap<>();

		for ( int i = 0; i < genesToShow.size(); ++i )
		{
			final String gene = genesToShow.get( i );
			System.out.println( "Rendering gene: " + gene );

			final ARGBType col;

			if ( genesToShow.size() == 1 ) // one gene
				col = new ARGBType( ARGBType.rgba(255, 255, 255, 0) );
			else if ( i == 0 && genesToShow.size() == 2 ) // two genes
				col = new ARGBType( ARGBType.rgba(0, 255, 0, 0) );
			else if ( i == 1 && genesToShow.size() == 2 )
				col = new ARGBType( ARGBType.rgba(255, 0, 255, 0) );
			else if ( i == 0 && genesToShow.size() == 3 ) // three genes
				col = new ARGBType( ARGBType.rgba(255, 255, 0, 0) );
			else if ( i == 1 && genesToShow.size() == 3 )
				col = new ARGBType( ARGBType.rgba(0, 255, 255, 0) );
			else if ( i == 2 && genesToShow.size() == 3 )
				col = new ARGBType( ARGBType.rgba(255, 0, 255, 0) );
			else // many genes
				col = Render.randomColor( rnd );

			final AddedGene addedGene = AddedGene.addGene(
					inputPath,
					dataset,
					rendering,
					source,
					dataToVisualize,
					AddedGene.convert2Dto3D( dataToVisualize.transform() ), //m3d,
					gene,
					renderingFactor,
					col, //new ARGBType( ARGBType.rgba(0, 255, 0, 0) ),
					brightnessMin,
					brightnessMax );

			source = addedGene.source();

			sourceData.put( gene, new ArrayList<>( Arrays.asList( addedGene ) ) );
		}

		final SynchronizedViewerState state = source.getBdvHandle().getViewerPanel().state();
		final ArrayList< SourceGroup > oldGroups = new ArrayList<>( state.getGroups() );

		final HashMap< String, SourceGroup > geneToBDVSource = new HashMap<>();

		// TODO: ADD ANNOTATIONS AS GROUP

		for ( int i = 0; i < genesToShow.size(); ++i )
		{
			final String gene = genesToShow.get( i );

			final SourceGroup handle = new SourceGroup();
			state.addGroup( handle );
			state.setGroupName( handle, gene );
			state.setGroupActive( handle, true );
			state.addSourceToGroup( state.getSources().get(i), handle );

			geneToBDVSource.put( gene, handle );
		}

		source.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.GROUP );
		state.removeGroups( oldGroups );

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
		final STIMCard card =
				new STIMCard(
						new ArrayList<>( Arrays.asList( dataToVisualize ) ),
						dataToVisualize.data().getGeneNames().stream().map( s -> new ValuePair<String, Double>(s, null) ).collect( Collectors.toList() ),
						sourceData,
						geneToBDVSource,
						overlay,
						dataToVisualize.statistics().getMedianDistance(), rendering, renderingFactor, brightnessMin, brightnessMax, source.getBdvHandle());

		source.getBdvHandle().getCardPanel().addCard( "STIM Display Options", "STIM Display Options", card.getPanel(), true );

		// add STIMCardFilter panel
		final STIMCardFilter cardFilter = new STIMCardFilter( card, ffSingleSpot, ffMedian, ffGauss, ffMean, service );
		source.getBdvHandle().getCardPanel().addCard( "STIM Filtering Options", "STIM Filtering Options", cardFilter.getPanel(), true );

		// the side panel
		final SplitPanel splitPanel = source.getBdvHandle().getSplitPanel();

		// Expands the split Panel (after waiting 2 secs for the BDV to calm down)
		SimpleMultiThreading.threadWait( 2000 );
		splitPanel.setCollapsed(false);

		return null;
	}

	public static final void main(final String... args) {
		CommandLine.call(new BigDataViewerDisplay(), args);
	}

}
