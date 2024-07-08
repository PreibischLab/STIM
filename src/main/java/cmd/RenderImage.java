package cmd;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import data.STData;
import data.STDataStatistics;
import data.STDataUtils;
import filter.FilterFactory;
import filter.GaussianFilterFactory;
import filter.GaussianFilterFactory.WeightType;
import filter.MeanFilterFactory;
import filter.MedianFilterFactory;
import filter.SingleSpotRemovingFilterFactory;
import gui.STDataAssembly;
import gui.bdv.AddedGene;
import gui.bdv.AddedGene.Rendering;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import imglib2.ImgLib2Util;
import io.SpatialDataContainer;
import io.SpatialDataIO;
import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.RealFloatConverter;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import render.MaxDistanceParam;
import render.Render;
import org.apache.logging.log4j.Logger;
import util.LoggerUtil;

@Command(name = "st-render", mixinStandardHelpOptions = true, version = "0.3.0", description = "Spatial Transcriptomics as IMages project - render ST data as images in Fiji/ImageJ")
public class RenderImage implements Callable<Void> {
	
	private static final Logger logger = LoggerUtil.getLogger();
	// st-render -i /Users/preibischs/Documents/BIMSB/Publications/imglib2-st/slide-seq/raw/slide-seq.n5 -d Puck_180531_22.n5,Puck_180531_23.n5 -g Malat1
	// -dm Gauss -bMin 0.0 -bMax 0.1579 -rf 2.0922  --ffSingleSpot 1.25 --scale 0.10557775847089489
	@Option(names = {"-i", "--input"}, required = true, description = "input file or N5 container path, e.g. -i /home/ssq.n5.")
	private String inputPath = null;

	@Option(names = {"-g", "--genes"}, required = true, description = "comma separated list of one or more genes, e.g. -g 'Calm2,Hpca,Ptgds'")
	private String genes = null;

	@Option(names = {"-o", "--output"}, required = false, description = "output folder for saving rendered images as TIFF, e.g. -o /home/export (default: display with ImageJ)")
	private String output = null;

	@Option(names = {"-d", "--datasets"}, required = false, description = "if --container is given: comma separated list of datasets, e.g. -d 'Puck_180528_20,Puck_180528_22' (default: open all datasets)")
	private String datasets = null;

	@Option(names = {"-s", "--scale"}, required = false, description = "scaling of the image, e.g. -s 0.5 (default: 0.05)")
	private double scale = 0.05;

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

	@Option(names = {"--border"}, required = false, description = "extra empty border around spatial sequencing locations, e.g. -b 100 (default: 20)")
	private int border = 20;

	@Option(names = {"--ignoreTransforms"}, required = false, description = "ignore the transforms stored in the metadata when rendering (default: false)")
	private boolean ignoreTransforms = false;

	@Override
	public Void call() throws Exception {
		if (!(new File(inputPath)).exists()) {
			logger.error("Container / dataset '" + inputPath + "' does not exist. Stopping.");
			return null;
		}

		final ExecutorService service = Executors.newFixedThreadPool(8);
		final Map<String, SpatialDataIO> iodata = new HashMap<>();
		if (SpatialDataContainer.isCompatibleContainer(inputPath)) {
			SpatialDataContainer container = SpatialDataContainer.openForReading(inputPath, service);

			final List<String> datasetNames;
			if (datasets != null && !datasets.isEmpty())
				datasetNames = Arrays.asList(datasets.split(","));
			else {
				logger.info("Opening all datasets in '" + inputPath + "':");
				datasetNames = container.getDatasets();
			}

			for (String dataset : datasetNames) {
				logger.info("Opening dataset '" + dataset + "' in '" + inputPath + "' ...");
				iodata.put(dataset.trim(), container.openDatasetReadOnly(dataset.trim()));
			}
		}
		else {
			logger.info("Opening dataset '" + inputPath + "' ...");
			iodata.put(inputPath, SpatialDataIO.openReadOnly(inputPath, service));
		}

		if (genes == null || genes.isEmpty()) {
			logger.error("No genes available. stopping.");
			return null;
		}
		String[] geneList = genes.split(",");

		final List<Pair<STData, AffineTransform2D>> dataToVisualize = new ArrayList<>();
		for (final Map.Entry<String, SpatialDataIO> entry : iodata.entrySet()) {
			final STDataAssembly stAssembly = entry.getValue().readData();

			if (stAssembly != null) {
				logger.debug("Assigning transform to " + entry.getKey());
				AffineTransform2D transform = ignoreTransforms ? new AffineTransform2D() : stAssembly.transform();
				dataToVisualize.add(new ValuePair<>(stAssembly.data(), transform));
				logger.debug(transform);
			}
		}

		if (dataToVisualize.isEmpty())
		{
			logger.error( "No datasets that contain sequencing data. stopping." );
			return null;
		}

		final List<FilterFactory<DoubleType, DoubleType>> filterFactories =
				assembleFilterFactories(
						new STDataStatistics( dataToVisualize.get( 0 ).getA() ),
						ffSingleSpot, ffMedian, ffGauss, ffMean );

		if ( output == null )
			new ImageJ();
		else
			if ( !new File( output ).exists() )
				new File( output ).mkdirs();

		for ( final String gene : geneList )
		{
			logger.info( "Rendering gene " + gene );

			//ImagePlus imp = AlignTools.visualizeList( dataToVisualize, scale, gene, true );// filterFactories );
			ImagePlus imp = visualizeList( dataToVisualize, scale, brightnessMin, brightnessMax, gene, rendering, renderingFactor, border, filterFactories );
			imp.setTitle( gene );

			if ( output == null )
			{
				imp.show();
			}
			else
			{
				final String file = new File( output, gene + ".tif" ).getAbsolutePath();
				logger.info( "Saving as " + file );
				IJ.saveAsTiff( imp, file );
				imp.close();
			}
		}

		service.shutdown();
		return null;
	}

	public static ImagePlus visualizeList(
			final List< Pair< STData, AffineTransform2D > > data,
			final double scale,
			final double brightnessMin,
			final double brightnessMax,
			final String gene,
			final Rendering renderType,
			final double renderingFactor,
			final int border,
			final List< FilterFactory< DoubleType, DoubleType > > filterFactories )
	{
		// visualize result using the global transform
		final AffineTransform2D tS = new AffineTransform2D();
		tS.scale( scale );

		final Interval interval =
				STDataUtils.getCommonIterableInterval(
						data.stream().map( entry -> 
							ImgLib2Util.transformInterval(
									entry.getA().getRenderInterval(),
									entry.getB().copy().preConcatenate( tS ) )
						).collect( Collectors.toList() ) );

		final Interval finalInterval = Intervals.expand( interval, border );

		logger.info( "Rendering interval: " + Util.printInterval( finalInterval ) );

		final ImageStack stack = new ImageStack( (int)finalInterval.dimension( 0 ), (int)finalInterval.dimension( 1 ) );

		double minDisplay = Double.MAX_VALUE;
		double maxDisplay = -Double.MAX_VALUE;

		for ( Pair< STData, AffineTransform2D > pair : data )
		{
			final AffineTransform2D tA = pair.getB().copy();
			tA.preConcatenate( tS );

			final RandomAccessibleInterval<DoubleType> vis =
					display(
							pair.getA(),
							new STDataStatistics( pair.getA() ),
							pair.getB().copy().preConcatenate( tS ),
							renderType,
							renderingFactor,
							filterFactories,
							gene,
							finalInterval );

			logger.info( "rendering  " + pair.getA().toString() );

			final double[] minmax = AddedGene.minmax( pair.getA().getExprData( gene ) );
			minDisplay = Math.min( minDisplay, AddedGene.getDisplayMin( minmax[ 0 ], minmax[ 1 ], brightnessMin ) );
			maxDisplay = Math.max( maxDisplay, AddedGene.getDisplayMax( minmax[ 1 ], brightnessMax ) );

			final ImageProcessor ip = ImageJFunctions.wrapFloat( vis, new RealFloatConverter<>(), pair.getA().toString(), null ).getProcessor();
			stack.addSlice(pair.getA().toString(), ip );
		}

		final ImagePlus imp = new ImagePlus("all", stack );
		//imp.setDimensions(data.size(), 1, 1 );
		//imp.resetDisplayRange();
		imp.setDisplayRange(minDisplay, maxDisplay);

		return imp;
	}

	public static ArrayList<FilterFactory<DoubleType, DoubleType>> assembleFilterFactories(
			STDataStatistics stats,
			Double ffSingleSpot,
			Double ffMedian,
			Double ffGauss,
			Double ffMean)
	{
		final DoubleType outofbounds = new DoubleType( 0 );
		final ArrayList<FilterFactory<DoubleType, DoubleType>> filterFactories = new ArrayList<>();

		if ( ffSingleSpot != null && ffSingleSpot > 0  )
		{
			logger.debug( "Using single-spot filtering, radius="  + ffSingleSpot );
			filterFactories.add( new SingleSpotRemovingFilterFactory<>( outofbounds, stats.getMedianDistance() * ffSingleSpot ) );
		}

		if ( ffMedian != null && ffMedian > 0.0 )
		{
			logger.debug( "Using median filtering, radius=" + ffMedian );
			filterFactories.add( new MedianFilterFactory<>( outofbounds, stats.getMedianDistance() * ffMedian ) );
		}

		if ( ffGauss != null && ffGauss > 0.0 )
		{
			logger.debug( "Using Gauss filtering, radius=" + ffGauss );
			filterFactories.add( new GaussianFilterFactory<>( outofbounds, stats.getMedianDistance() * ffGauss ) );
		}

		if ( ffMean != null && ffMean > 0.0 )
		{
			logger.debug( "Using mean/avg filtering, radius=" + ffMean );
			filterFactories.add( new MeanFilterFactory<>( outofbounds, stats.getMedianDistance() * ffMean ) );
		}

		return filterFactories;
	}

	public static RealRandomAccessible< DoubleType > createRRA(
			final IterableRealInterval< DoubleType > data,
			final double medianDistance,
			final Rendering renderType,
			final double renderingFactor )
	{
		final RealRandomAccessible< DoubleType > renderRRA;

		if ( renderType == Rendering.Gauss )
		{
			renderRRA = Render.render( data, new GaussianFilterFactory<>( new DoubleType( 0 ), medianDistance*renderingFactor, WeightType.PARTIAL_BY_SUM_OF_WEIGHTS ) );
		}
		else if ( renderType == Rendering.NN )
		{
			renderRRA = Render.renderNN( data, new DoubleType( 0 ), new MaxDistanceParam( medianDistance*renderingFactor ) );
		}
		else if ( renderType == Rendering.Mean )
		{
			renderRRA = Render.render( data, new MeanFilterFactory<>( new DoubleType( 0 ), medianDistance*renderingFactor ) );
		}
		else // LINEAR
		{
			renderRRA = Render.renderLinear( data, 5, 3.0, new DoubleType( 0 ),  new MaxDistanceParam( medianDistance*renderingFactor ) );
		}

		return renderRRA;
	}

	public static RandomAccessibleInterval< DoubleType > display(
			final STData stdata,
			final STDataStatistics stStats,
			final AffineGet coordinateTransform,
			final Rendering renderType,
			final double renderingFactor,
			final List< FilterFactory< DoubleType, DoubleType > > filterFactories,
			final String gene,
			final Interval renderInterval )
	{
		// we work at full resolution so rendering and filter parameters are independent of the scale
		final IterableRealInterval< DoubleType > data = Render.getRealIterable( stdata, null, gene, filterFactories );

		final RealRandomAccessible< DoubleType > renderRRA =
				createRRA( data, stStats.getMedianDistance(), renderType, renderingFactor );

		return Views.interval( RealViews.affine( renderRRA, coordinateTransform ), renderInterval );
	}

	public static void main(final String... args) {
		final CommandLine cmd = new CommandLine(new RenderImage());
		cmd.execute(args);
	}
}
