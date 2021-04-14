package examples;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.janelia.saalfeldlab.n5.N5FSReader;

import data.STData;
import filter.FilterFactory;
import filter.Filters;
import imglib2.ExpValueRealIterable;
import imglib2.TransformedIterableRealInterval;
import io.N5IO;
import net.imglib2.FinalRealInterval;
import net.imglib2.IterableRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converters;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.view.Views;
import render.Render;

public class VisualizeMetadata
{
	// 3d cell types
	// minimal GUI for cell type selection
	// alignment

	public static RealRandomAccessible< IntType > visualize2d(
			final STData stdata,
			final String meta,
			final double spotSize,
			final AffineTransform2D transform,
			final IntType outofbounds,
			final List< FilterFactory< IntType, IntType > > filterFactorys, // optional
			final HashMap<Long, ARGBType> lut ) // optional
	{
		final RandomAccessibleInterval idsIn = stdata.getMetaData().get( meta );

		if ( idsIn == null )
		{
			System.out.println( "WARNING: metadata '" + meta + "' does not exist. skipping.");
			return null;
		}

		final Object type = Views.iterable( idsIn ).firstElement();
		if ( !IntegerType.class.isInstance( type ) )
		{
			System.out.println( "WARNING: metadata '" + meta + "' is not an integer type (but "+ type.getClass().getSimpleName() +". don't know how to render it. skipping.");
			return null;
		}

		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		Random rnd = new Random( 243 );

		for ( final IntegerType<?> t : Views.iterable( (RandomAccessibleInterval<IntegerType<?>>)idsIn ) )
		{
			final long l = t.getIntegerLong();

			min = Math.min( min, l );
			max = Math.max( max, l );

			if ( lut != null )
			{
				if ( !lut.containsKey( l ) )
					lut.put( l, Render.randomColor( rnd ) );
			}
		}

		System.out.println( "Rendering metadata '" + meta + "', type="+ type.getClass().getSimpleName() + ", min=" + min + ", max= " + max + " as integers" );

		final RandomAccessibleInterval< IntType > ids;
		if ( IntType.class.isInstance( type ) )
			ids = (RandomAccessibleInterval< IntType >)idsIn;
		else
			ids = Converters.convert( idsIn, (i,o) -> o.setInt( ((IntegerType)i).getInteger() ), new IntType() );

		IterableRealInterval< IntType > data = new ExpValueRealIterable(
				stdata.getLocations(),
				ids,
				new FinalRealInterval( stdata ));

		if ( transform != null && !transform.isIdentity() )
			data = new TransformedIterableRealInterval<>(
					data,
					transform );

		// filter the iterable
		if ( filterFactorys != null )
			for ( final FilterFactory<IntType, IntType> filterFactory : filterFactorys )
				data = Filters.filter( data, filterFactory );

		return Render.renderNN( data, outofbounds, spotSize );
	}

	public static void main( String[] args ) throws IOException
	{
		final N5FSReader n5 = N5IO.openN5( new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/slide-seq-test.n5" ) );
	}
}
