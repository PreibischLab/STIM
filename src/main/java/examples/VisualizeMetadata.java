package examples;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.janelia.saalfeldlab.n5.N5FSReader;

import data.STData;
import data.STDataUtils;
import filter.FilterFactory;
import gui.STDataAssembly;
import imglib2.StackedIterableRealInterval;
import io.N5IO;
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

public class VisualizeMetadata
{
	// 3d cell types
	// minimal GUI for cell type selection
	// alignment

	public static Pair< RealRandomAccessible< IntType >, Interval > createStack(
			final List< STDataAssembly > stdata,
			final String meta,
			final double spotSize,
			final double spacingFactor,
			final IntType outofbounds,
			final List< FilterFactory< IntType, IntType > > filterFactorys,
			final HashMap<Long, ARGBType> lut )
	{
		final ArrayList< IterableRealInterval< IntType > > slices = new ArrayList<>();

		for ( int i = 0; i < stdata.size(); ++i )
			slices.add( Render.getRealIterable( stdata.get( i ).data(), meta, stdata.get( i ).transform(), filterFactorys, lut ) );

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

	public static RealRandomAccessible< IntType > visualize2d(
			final STData stdata,
			final String meta,
			final double spotSize,
			final AffineTransform2D transform,
			final IntType outofbounds,
			final List< FilterFactory< IntType, IntType > > filterFactorys, // optional
			final HashMap<Long, ARGBType> lut )
	{
		final IterableRealInterval< IntType > data = Render.getRealIterable(stdata, meta, transform, filterFactorys, lut);

		return Render.renderNN( data, outofbounds, spotSize );
	}

	public static void main( String[] args ) throws IOException
	{
		final N5FSReader n5 = N5IO.openN5( new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/slide-seq-test.n5" ) );
	}
}
