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

import io.SpatialDataContainer;
import io.SpatialDataIO;

import data.STData;
import data.STDataStatistics;
import data.STDataUtils;
import filter.FilterFactory;
import filter.GaussianFilterFactory;
import filter.GaussianFilterFactory.WeightType;
import filter.MedianFilterFactory;
import filter.SingleSpotRemovingFilterFactory;
import gui.STDataAssembly;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import imglib2.ImgLib2Util;
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
import picocli.CommandLine.Option;
import render.Render;

public class RenderImage implements Callable<Void> {

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

	@Option(names = {"-b", "--border"}, required = false, description = "extra empty border around spatial sequencing locations, e.g. -b 100 (default: 20)")
	private int border = 20;

	@Option(names = {"-f", "--singleSpotFilter"}, required = false, description = "filter single spots using the median distance between all spots as threshold (default: false)")
	private boolean singleSpotFilter = false;

	@Option(names = {"-m", "--medianFilter"}, required = false, description = "median-filter all spots using a given radius, e.g -m 20.0 (default: no filtering)")
	private Double median = null;

	@Option(names = {"-sf", "--smoothnessFactor"}, required = false, description = "factor for the sigma of the gaussian used for rendering, corresponds to smoothness, e.g -sf 2.0 (default: 1.5)")
	private double smoothnessFactor = 1.5;

	@Option(names = {"--ignoreTransforms"}, required = false, description = "ignore the transforms stored in the metadata when rendering (default: false)")
	private boolean ignoreTransforms = false;

	@Override
	public Void call() throws Exception {
		if (!(new File(inputPath)).exists()) {
			System.out.println("Container / dataset '" + inputPath + "' does not exist. Stopping.");
			return null;
		}

		final ExecutorService service = Executors.newFixedThreadPool(8);
		final Map<String, SpatialDataIO> iodata = new HashMap<>();
		if (SpatialDataContainer.isCompatibleContainer(inputPath)) {
			SpatialDataContainer container = SpatialDataContainer.openExisting(inputPath, service);

			final List<String> datasetNames;
			if (datasets != null && datasets.length() != 0)
				datasetNames = Arrays.asList(datasets.split(","));
			else {
				System.out.println("Opening all datasets in '" + inputPath + "':");
				datasetNames = container.getDatasets();
			}

			for (String dataset : datasetNames) {
				System.out.println("Opening dataset '" + dataset + "' in '" + inputPath + "' ...");
				iodata.put(dataset.trim(), container.openDataset(dataset.trim()));
			}
		}
		else {
			System.out.println("Opening dataset '" + inputPath + "' ...");
			iodata.put(inputPath, SpatialDataIO.inferFromName(inputPath, service));
		}

		if (genes == null || genes.length() == 0) {
			System.out.println("No genes available. stopping.");
			return null;
		}
		String[] geneList = genes.split(",");

		final List<Pair<STData, AffineTransform2D>> dataToVisualize = new ArrayList<>();
		for (final Map.Entry<String, SpatialDataIO> entry : iodata.entrySet()) {
			final STDataAssembly stAssembly = entry.getValue().readData();

			if (stAssembly != null) {
				System.out.println("Assigning transform to " + entry.getKey());
				AffineTransform2D transform = ignoreTransforms ? new AffineTransform2D() : stAssembly.transform();
				dataToVisualize.add(new ValuePair<>(stAssembly.data(), transform));
				System.out.println(transform);
			}
		}

		if ( dataToVisualize.size() == 0 )
		{
			System.out.println( "No datasets that contain sequencing data. stopping." );
			return null;
		}

		final DoubleType outofbounds = new DoubleType( 0 );
		final List<FilterFactory<DoubleType, DoubleType>> filterFactories = new ArrayList<>();

		if ( singleSpotFilter )
		{
			STDataStatistics stats = new STDataStatistics( dataToVisualize.get( 0 ).getA() );
			System.out.println( "Using single-spot filtering, radius="  + (stats.getMedianDistance() * 1.5) );
			filterFactories.add( new SingleSpotRemovingFilterFactory<>( outofbounds,stats.getMedianDistance() * 1.5 ) );
		}

		if ( median != null && median > 0.0 )
		{
			System.out.println( "Using median filtering, radius=" + median );
			filterFactories.add( new MedianFilterFactory<>( outofbounds, median ) );
		}

		if ( output == null )
			new ImageJ();
		else
			if ( !new File( output ).exists() )
				new File( output ).mkdirs();

		for ( final String gene : geneList )
		{
			System.out.println( "Rendering gene " + gene );

			//ImagePlus imp = AlignTools.visualizeList( dataToVisualize, scale, gene, true );// filterFactories );
			ImagePlus imp = visualizeList( dataToVisualize, scale, gene, smoothnessFactor, border, filterFactories );
			imp.setTitle( gene );

			if ( output == null )
			{
				imp.show();
			}
			else
			{
				final String file = new File( output, gene + ".tif" ).getAbsolutePath();
				System.out.println( "Saving as " + file );
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
			final String gene,
			final double smoothnessFactor,
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

		System.out.println( "Rendering interval: " + Util.printInterval( finalInterval ) );

		final ImageStack stack = new ImageStack( (int)finalInterval.dimension( 0 ), (int)finalInterval.dimension( 1 ) );

		for ( Pair< STData, AffineTransform2D > pair : data )
		{
			final AffineTransform2D tA = pair.getB().copy();
			tA.preConcatenate( tS );

			final RandomAccessibleInterval<DoubleType> vis =
					display(
							pair.getA(),
							new STDataStatistics( pair.getA() ),
							pair.getB().copy().preConcatenate( tS ),
							null,
							smoothnessFactor,
							filterFactories,
							gene,
							finalInterval );

			System.out.println( "rendering  " + pair.getA().toString() );

			stack.addSlice(pair.getA().toString(), ImageJFunctions.wrapFloat( vis, new RealFloatConverter<>(), pair.getA().toString(), null ).getProcessor());
		}

		ImagePlus imp = new ImagePlus("all", stack );
		imp.resetDisplayRange();
		return imp;
	}

	public static RandomAccessibleInterval< DoubleType > display(
			final STData stdata,
			final STDataStatistics stStats,
			final AffineGet coordinateTransform,
			final AffineGet intensityTransform,
			final double smoothnessFactor,
			final List< FilterFactory< DoubleType, DoubleType > > filterFactories,
			final String gene,
			final Interval renderInterval )
	{
		// we work at full resolution so rendering and filter parameters are independent of the scale
		final IterableRealInterval< DoubleType > data = Render.getRealIterable( stdata, null, intensityTransform, gene, filterFactories );

		final RealRandomAccessible< DoubleType > renderRRA = Render.render( data, new GaussianFilterFactory<>( new DoubleType( 0 ), stStats.getMedianDistance()*smoothnessFactor, WeightType.PARTIAL_BY_SUM_OF_WEIGHTS ) );

		return Views.interval( RealViews.affine( renderRRA, coordinateTransform ), renderInterval );
	}

	public static final void main(final String... args) {
		CommandLine.call(new RenderImage(), args);
	}
}
