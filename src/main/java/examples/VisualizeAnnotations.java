package examples;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import data.STData;
import data.STDataUtils;
import filter.FilterFactory;
import gui.STDataAssembly;
import gui.celltype.CellTypeExplorer;
import imglib2.StackedIterableRealInterval;
import imglib2.TransformedIterableRealInterval;
import io.Path;
import io.SpatialDataContainer;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import render.Render;

public class VisualizeAnnotations
{
	// minimal GUI for cell type selection
	// alignment

	public static Pair< RealRandomAccessible< IntType >, Interval > createStack(
			final List< STDataAssembly > stdata,
			final String annotation,
			final double spotSize,
			final double spacingFactor,
			final IntType outofbounds,
			final List< FilterFactory< IntType, IntType > > filterFactorys,
			final HashMap<Long, ARGBType> lut )
	{
		final ArrayList< IterableRealInterval< IntType > > slices = new ArrayList<>();

		for ( int i = 0; i < stdata.size(); ++i )
			slices.add( Render.getRealIterable( stdata.get( i ).data(), annotation, stdata.get( i ).transform(), filterFactorys, lut ) );

		final double medianDistance = stdata.get( 0 ).statistics().getMedianDistance();

		// gauss crisp
		double gaussRenderSigma = medianDistance * 1.0;
		//double gaussRenderRadius = medianDistance * 4;

		final double spacing = medianDistance * spacingFactor;

		final Interval interval2d = STDataUtils.getCommonIterableInterval( slices );
		final long[] minI = new long[] { interval2d.min( 0 ), interval2d.min( 1 ), 0 - Math.round( Math.ceil( gaussRenderSigma * 3 ) ) };
		final long[] maxI = new long[] { interval2d.max( 0 ), interval2d.max( 1 ), Math.round( ( stdata.size() - 1 ) * spacing ) + Math.round( Math.ceil( gaussRenderSigma * 3 ) ) };
		final Interval interval = new FinalInterval( minI, maxI );

		final StackedIterableRealInterval< IntType > stack = new StackedIterableRealInterval<>( slices, spacing );

		return new ValuePair<>(
				Render.renderNN( stack, outofbounds, spotSize ),
				interval );
	}

	public static void visualize2d(
			final STData stdata,
			final String annotation,
			final double spotSize,
			final AffineTransform2D transform )
	{
		final HashMap<Long, ARGBType > lut = new HashMap<>();

		final RealRandomAccessible< IntType > rra = VisualizeAnnotations.visualize2d(
				stdata,
				annotation,
				spotSize,
				transform,
				new IntType( -1 ),
				null ,
				lut );

		final Interval interval = STDataUtils.getIterableInterval(
				new TransformedIterableRealInterval<>(
						stdata,
						transform ) );

		CellTypeExplorer cte = new CellTypeExplorer( lut );

		BdvOptions options = BdvOptions.options().numRenderingThreads( Runtime.getRuntime().availableProcessors() ).is2D();
		BdvStackSource< ? > source = BdvFunctions.show(
				Render.switchableConvertToRGB( rra, new IntType( -1 ), new ARGBType(), lut, cte.panel() ),
				interval,
				annotation,
				options );
		source.setDisplayRange( 0, 255 );
		source.setDisplayRangeBounds( 0, 2550 );
		cte.panel().setBDV( source.getBdvHandle().getViewerPanel() );
		//source.getBdvHandle().getViewerPanel().requestRepaint();
	}

	public static RealRandomAccessible< IntType > visualize2d(
			final STData stdata,
			final String annotation,
			final double spotSize,
			final AffineTransform2D transform,
			final IntType outofbounds,
			final List< FilterFactory< IntType, IntType > > filterFactorys, // optional
			final HashMap<Long, ARGBType> lut )
	{
		final IterableRealInterval< IntType > data = Render.getRealIterable(stdata, annotation, transform, filterFactorys, lut);

		return Render.renderNN( data, outofbounds, spotSize );
	}

	public static void main( String[] args ) throws IOException
	{
		final ExecutorService service = Executors.newFixedThreadPool(8);
		final List<STDataAssembly> puckData =
				SpatialDataContainer.openExisting(Path.getPath() + "slide-seq-test.n5", service).openAllDatasets().stream()
								.map(sdio ->
									 {try {return sdio.readData();} catch (IOException e) {throw new RuntimeException(e);}
								}).collect(Collectors.toList());

		visualize2d(
				puckData.get( 12 ).data(),
				"celltype",
				puckData.get( 12 ).statistics().getMedianDistance() / 1.5,
				puckData.get( 12 ).transform() );
		service.shutdown();
	}
}
