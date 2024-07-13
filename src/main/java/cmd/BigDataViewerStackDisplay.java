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

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import data.STDataStatistics;
import examples.VisualizeAnnotations;
import examples.VisualizeStack;
import examples.VisualizeStack.STIMStack;
import filter.FilterFactory;
import filter.SingleSpotRemovingFilterFactory;
import gui.STDataAssembly;
import gui.bdv.AddedGene.Rendering;
import gui.celltype.CellTypeExplorer;
import io.SpatialDataContainer;
import io.SpatialDataIO;
import net.imglib2.Interval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import render.Render;
import org.apache.logging.log4j.Logger;
import util.LoggerUtil;

@Command(name = "st-bdv-view3d", mixinStandardHelpOptions = true, version = "0.3.1", description = "Spatial Transcriptomics as IMages project - visualize ST data in BigDataViewer")
public class BigDataViewerStackDisplay implements Callable<Void> {
	
	private static final Logger logger = LoggerUtil.getLogger();
	@Option(names = {"-i", "--input"}, required = true, description = "input file or N5 container, e.g. -i /home/ssq.n5")
	private String inputPath = null;

	@Option(names = {"-g", "--genes"}, required = true, description = "comma separated list of one or more gene to visualize, e.g. -g Calm2,Ubb")
	private String genes = null;

	@Option(names = {"-a", "--annotation"}, required = false, description = "comma separated list of annotations to visualize, e.g. -a celltype")
	private String annotations = null;

	@Option(names = {"-ar", "--annotationRadius"}, required = false, description = "radius of annotation spots as a factor of their median distance, e.g. -ar 2.0 (default: 0.75; in 3d: zSpacing*0.75)")
	private double annotationRadius = 0.75;

	@Option(names = {"-z", "--zSpacingFactor"}, required = false, description = "define the z-spacing between different sections (as a factor of median spacing between sequenced locations), e.g. -z 10.0 (default: 5.0)")
	private double zSpacingFactor = 5.0;

	@Option(names = {"-d", "--datasets"}, required = false, description = "comma separated list of one or more datasets, e.g. -d 'Puck_180528_20,Puck_180528_22' (default: all)")
	private String datasets = null;

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
			logger.error("Container / dataset '{}' does not exist. Stopping.", inputPath);
			return null;
		}

		final ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

		final List<SpatialDataIO> iodata = new ArrayList<>();
		if (SpatialDataContainer.isCompatibleContainer(inputPath)) {
			SpatialDataContainer container = SpatialDataContainer.openForReading(inputPath, service);

			if (datasets != null && !datasets.isEmpty()) {
				for (String dataset : datasets.split(",")) {
					logger.info("Opening dataset '{}' in '{}' ...", dataset, inputPath);
					iodata.add(container.openDatasetReadOnly(dataset.trim()));
				}
			}
			else {
				logger.info("Opening all datasets in '{}' ...", inputPath);
				iodata.addAll(container.openAllDatasets());
			}
		}
		else {
			logger.info("Opening dataset '{}' ...", inputPath);
			iodata.add(SpatialDataIO.openReadOnly(inputPath, service));
		}

		if ( iodata.size() <= 1 )
		{
			logger.error("Only one dataset selected, cannot be displayed in 3D. Please use 'st-bdv-view' instead for 2D. Stopping.");
			return null;
		}

		if (genes == null || genes.isEmpty()) {
			logger.error("No genes available. stopping.");
			return null;
		}

		List<String> genesToShow = Arrays.stream(genes.split(",")).map(String::trim).collect(Collectors.toList());
		if (genesToShow.isEmpty()) {
			logger.error("No genes available. stopping.");
			return null;
		}

		final List<STDataAssembly> dataToVisualize = new ArrayList<>();
		for (final SpatialDataIO sdio : iodata) {
			STDataAssembly data = sdio.readData();
			dataToVisualize.add(data);

			if (!useTransform)
				data.transform().set(new AffineTransform2D());
		}

		if (dataToVisualize.isEmpty()) {
			logger.error("No datasets that contain sequencing data. stopping.");
			return null;
		}

		List< String > annotationList;
		if ( annotations != null && !annotations.isEmpty())
			annotationList = Arrays.asList(annotations.split("," ) );
		else
			annotationList = new ArrayList<>();

		BdvStackSource< ? > source = null;

		//
		// Display annotations
		//
		for ( final String annotation : annotationList )
		{
			final IntType outofboundsInt = new IntType( -1 );
			final double spotSize = dataToVisualize.get( 0 ).statistics().getMedianDistance() * annotationRadius;
			final HashMap<Long, ARGBType > lut = new HashMap<>();

			final List< FilterFactory< IntType, IntType > > filterFactorysInt = new ArrayList<>();

			if ( ffSingleSpot != null && ffSingleSpot > 0  )
			{
				logger.debug("Using single-spot filtering, effective radius={}", dataToVisualize.get(0).statistics().getMedianDistance() * ffSingleSpot);
				filterFactorysInt.add( new SingleSpotRemovingFilterFactory<>( outofboundsInt, dataToVisualize.get( 0 ).statistics().getMedianDistance() * ffSingleSpot ) );
			}

			final RealRandomAccessible< IntType > rra;
			final Interval interval;

			final Pair< RealRandomAccessible< IntType >, Interval > stack =
					VisualizeAnnotations.createStack(dataToVisualize, annotation, zSpacingFactor * 0.75 * spotSize, zSpacingFactor, outofboundsInt, filterFactorysInt, lut );
			rra = stack.getA();
			interval = stack.getB();

			CellTypeExplorer cte = new CellTypeExplorer( lut );

			final RealRandomAccessible< ARGBType > rraRGB = Render.switchableConvertToRGB( rra, outofboundsInt, new ARGBType(), lut, cte.panel() );

			BdvOptions options = BdvOptions.options().numRenderingThreads( Runtime.getRuntime().availableProcessors() ).addTo( source );
			if ( dataToVisualize.size() == 1 )
				options = options.is2D();
			source = BdvFunctions.show( rraRGB, interval, annotation, options );
			source.setDisplayRange( 0, 255 );
			source.setDisplayRangeBounds( 0, 2550 );

			cte.panel().setBDV( source.getBdvHandle().getViewerPanel() );
		}

		//
		// Display genes
		//
		final DoubleType outofbounds = new DoubleType( 0 );
		final List<FilterFactory<DoubleType, DoubleType>> filterFactories =
				RenderImage.assembleFilterFactories(
						new STDataStatistics( dataToVisualize.get( 0 ).data() ),
						ffSingleSpot, ffMedian, ffGauss, ffMean );

		// random gene coloring
		Random rnd = new Random( 343 );

		for ( int i = 0; i < genesToShow.size(); ++i )
		{
			final String gene = genesToShow.get( i );
			logger.debug("Rendering gene: {}", gene);

			final STIMStack stack =
					VisualizeStack.createStack(
							dataToVisualize,
							gene,
							outofbounds,
							zSpacingFactor,
							brightnessMin,
							brightnessMax,
							rendering,
							renderingFactor,
							filterFactories );

			BdvOptions options = BdvOptions.options().numRenderingThreads( Runtime.getRuntime().availableProcessors() ).addTo( source );
			source = BdvFunctions.show( stack.rra, stack.interval, gene, options );
			source.setDisplayRange( stack.minDisplay, stack.maxDisplay );
			source.setDisplayRangeBounds( stack.minDisplay, stack.maxDisplay * 2);
			source.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.FUSED );
			source.setCurrent();

			source.setColor( BigDataViewerDisplay.getColor(genesToShow, i, rnd) );
		}

		final AffineTransform3D t = new AffineTransform3D();
		source.getBdvHandle().getViewerPanel().state().getViewerTransform( t );
		t.set(0, 2, 3 );
		source.getBdvHandle().getViewerPanel().state().setViewerTransform( t );

		service.shutdown();
		return null;
	}

	public static void main(final String... args) {
		final CommandLine cmd = new CommandLine(new BigDataViewerStackDisplay());
		cmd.execute(args);
	}

}
