package cmd;

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

import align.Pairwise;
import bdv.ui.splitpanel.SplitPanel;
import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import bdv.viewer.SourceGroup;
import bdv.viewer.SynchronizedViewerState;
import cmd.InteractiveAlignment.AddedGene.Rendering;
import data.STDataUtils;
import filter.GaussianFilterFactory;
import filter.GaussianFilterFactory.WeightType;
import filter.MeanFilterFactory;
import filter.RadiusSearchFilterFactory;
import gui.DisplayScaleOverlay;
import gui.STDataAssembly;
import gui.bdv.STIMCardAlignSIFT;
import gui.bdv.STIMCardFilter;
import gui.bdv.STIMCard;
import gui.bdv.STIMCardAlignICP;
import imglib2.TransformedIterableRealInterval;
import io.SpatialDataContainer;
import io.SpatialDataIO;
import io.TextFileAccess;
import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.KDTree;
import net.imglib2.RealRandomAccessible;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import render.MaxDistanceParam;
import render.Render;
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

	@Option(names = {"--rendering"}, required = false, description = "inital rendering type (Gauss, Mean, NearestNeighbor, Linear), e.g --rendering Gauss (default: Gauss)")
	private Rendering rendering = Rendering.Gauss;

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
		final HashMap< String, Pair< AddedGene, AddedGene > > sourceData = new HashMap<>();

		System.out.println( "Starting BDV ... " );
		System.out.println( "Starting with the top " + numGenes + " genes after skipping the first " + skipFirstNGenes +" genes (you find them in the 'groups' panel, you can add/remove genes in the GUI." );

		for ( int i = skipFirstNGenes; i < numGenes + skipFirstNGenes; ++i )
		{
			final String gene = allGenes.get( i ).getA(); //"Calm2";
			System.out.println( "Rendering gene (each available as its own source): " + gene );

			final AddedGene addedGene1 = AddedGene.addGene(
					rendering,
					lastSource,
					data1,
					gene,
					smoothnessFactor,
					new ARGBType( ARGBType.rgba(0, 255, 0, 0) ),
					brightnessMin,
					brightnessMax );

			final AddedGene addedGene2 = AddedGene.addGene(
					rendering,
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
		final STIMCard card =
				new STIMCard(
						data1, data2, allGenes, sourceData, geneToBDVSource, medianDistance, rendering, smoothnessFactor, brightnessMin, brightnessMax, lastSource.getBdvHandle());
		lastSource.getBdvHandle().getCardPanel().addCard( "STIM Display Options", "STIM Display Options", card.getPanel(), true );

		// add STIMCard panel
		final STIMCardFilter cardFilter = new STIMCardFilter(card);
		lastSource.getBdvHandle().getCardPanel().addCard( "STIM Filtering Options", "STIM Filtering Options", cardFilter.getPanel(), false );

		// add STIMCardAlignSIFT panel
		final STIMCardAlignSIFT cardAlignSIFT =
				new STIMCardAlignSIFT( dataset1, dataset2, overlay, card, cardFilter, service );
		lastSource.getBdvHandle().getCardPanel().addCard( "SIFT Alignment", "SIFT Alignment", cardAlignSIFT.getPanel(), true );

		// add STIMCardAlignICP panel
		final STIMCardAlignICP cardAlignICP =
				new STIMCardAlignICP( dataset1, dataset2, overlay, card, cardAlignSIFT, service );
		lastSource.getBdvHandle().getCardPanel().addCard( "ICP Alignment", "ICP Alignment", cardAlignICP.getPanel(), false );

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

	public static class AddedGene
	{
		public static enum Rendering { Gauss, Mean, NN, Linear };

		final RealRandomAccessible< DoubleType > rra;
		final KDTree< DoubleType > tree;
		final ArrayList< Double > originalValues;
		final private GaussianFilterFactory< DoubleType, DoubleType > gaussFactory;
		final private RadiusSearchFilterFactory< DoubleType, DoubleType > radiusFactory;
		final private MaxDistanceParam maxDistanceParam;
		final private BdvStackSource<?> source;
		final private double min, max;

		public AddedGene(
				final RealRandomAccessible< DoubleType > rra,
				final KDTree< DoubleType > tree,
				final GaussianFilterFactory< DoubleType, DoubleType > gaussFactory,
				final RadiusSearchFilterFactory< DoubleType, DoubleType > radiusFactory,
				final MaxDistanceParam maxDistanceParam,
				final BdvStackSource<?> source,
				final double min,
				final double max )
		{
			this.rra = rra;
			this.tree = tree;
			this.gaussFactory = gaussFactory;
			this.radiusFactory = radiusFactory;
			this.maxDistanceParam = maxDistanceParam;
			this.source = source;
			this.min = min;
			this.max = max;

			this.originalValues = new ArrayList<>();
			tree.forEach( t -> originalValues.add( t.get() ) );
		}

		public List< Double > originalValues() { return originalValues; }
		public RealRandomAccessible< DoubleType > rra() { return rra; }
		public KDTree< DoubleType > tree() { return tree; }
		public GaussianFilterFactory< DoubleType, DoubleType > gaussFactory(){ return gaussFactory; }
		public RadiusSearchFilterFactory< DoubleType, DoubleType > radiusFactory(){ return radiusFactory; }
		public MaxDistanceParam maxDistanceParam(){ return maxDistanceParam; }
		public BdvStackSource<?> source(){ return source; }
		public double min(){ return min; }
		public double max(){ return max; }

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

		public static < T extends RealType<T>> double[] minmax( final Iterable< T > data )
		{
			double min = Double.MAX_VALUE;
			double max = -Double.MAX_VALUE;

			for ( final T t : data )
			{
				min = Math.min( min, t.getRealDouble() );
				max = Math.max( max, t.getRealDouble() );
			}

			return new double[] { min, max };
		}

		public static AddedGene addGene(
				final Rendering renderType,
				final Bdv bdv,
				final STDataAssembly data,
				final String gene,
				final double smoothnessFactor,
				final ARGBType color,
				final double relativeInitialBrightnessMin,
				final double relativeInitialBrightnessMax )
		{
			final double[] minmax = minmax( data.data().getExprData( gene ) );

			final double min = minmax[ 0 ];
			final double max = minmax[ 1 ];

			final double minDisplay = getDisplayMin( min, max, relativeInitialBrightnessMin );
			final double maxDisplay = getDisplayMax( max, relativeInitialBrightnessMax );

			System.out.println( "min/max: " + min + "/" + max );
			System.out.println( "min/max display range: " + minDisplay + "/" + maxDisplay );

			// TODO: prefiltering...
			//final List< FilterFactory< DoubleType, DoubleType > > filterFactorys = new ArrayList<>();
			//filterFactorys.add( new MedianFilterFactory<DoubleType>( new DoubleType(), 3 * medianDistance ) );

			final RealRandomAccessible< DoubleType > rra;
			final KDTree< DoubleType > tree;
			final GaussianFilterFactory< DoubleType, DoubleType > gaussFactory;
			final RadiusSearchFilterFactory< DoubleType, DoubleType > radiusFactory;
			final MaxDistanceParam maxDistanceParam;

			final IterableRealInterval< DoubleType > iri = Render.getRealIterable( data, gene, null );
			final double medianDistance = data.statistics().getMedianDistance();

			if ( renderType == Rendering.Gauss )
			{
				gaussFactory = new GaussianFilterFactory<>( new DoubleType( 0 ), smoothnessFactor * medianDistance, WeightType.PARTIAL_BY_SUM_OF_WEIGHTS );
				radiusFactory = null;
				maxDistanceParam = null;

				final Pair<RealRandomAccessible<DoubleType>, KDTree<DoubleType>> r = Render.render2( iri, gaussFactory );
				rra = r.getA();
				tree = r.getB();
			}
			else if ( renderType == Rendering.NN )
			{
				maxDistanceParam = new MaxDistanceParam( smoothnessFactor * medianDistance );
				radiusFactory = null;
				gaussFactory = null;

				final Pair<RealRandomAccessible<DoubleType>, KDTree<DoubleType>> r = Render.renderNN2( iri, new DoubleType( 0 ), maxDistanceParam );
				rra = r.getA();
				tree = r.getB();
			}
			else if ( renderType == Rendering.Mean )
			{
				radiusFactory = new MeanFilterFactory<>( new DoubleType( 0 ), smoothnessFactor * medianDistance );
				maxDistanceParam = null;
				gaussFactory = null;

				final Pair<RealRandomAccessible<DoubleType>, KDTree<DoubleType>> r = Render.render2( iri, radiusFactory );
				rra = r.getA();
				tree = r.getB();
			}
			else // LINEAR
			{
				radiusFactory = null;
				gaussFactory = null;
				maxDistanceParam = new MaxDistanceParam( smoothnessFactor * medianDistance );

				final Pair<RealRandomAccessible<DoubleType>, KDTree<DoubleType>> r = Render.renderLinear2( iri, 5, 3.0, new DoubleType( 0 ), maxDistanceParam );
				rra = r.getA();
				tree = r.getB();
			}

			final Interval interval =
						STDataUtils.getIterableInterval(
								new TransformedIterableRealInterval<>(
										data.data(),
										new AffineTransform2D()/*data.transform()*/ ) );

			final BdvOptions options = BdvOptions.options().numRenderingThreads(Math.max(2,Runtime.getRuntime().availableProcessors() / 2))
					.addTo(bdv).is2D().preferredSize(1000, 890);

			final BdvStackSource< ? > source = BdvFunctions.show( rra, interval, gene, options );

			source.setDisplayRangeBounds( 0, max );
			source.setDisplayRange( minDisplay, maxDisplay );
			source.setColor( color );
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

			return new AddedGene( rra, tree, gaussFactory, radiusFactory, maxDistanceParam, source, min, max );
		}
	}

	public static void main(final String... args) {
		CommandLine.call(new InteractiveAlignment(), args);
	}
}
