package test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import net.imglib2.Interval;
import net.imglib2.RealInterval;
import net.imglib2.RealPointSampleList;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.ui.InteractiveDisplayCanvas;
import net.imglib2.ui.overlay.LogoPainter;
import net.imglib2.ui.viewer.InteractiveRealViewer2D;
import test.ImgLib2.SimpleStats;

public class InteractiveBrowsing< T extends RealType< T > >
{
	final protected InteractiveRealViewer2D< T > viewer;

	protected double scale;

	private final int width = 800;

	private final int height = 600;

	public InteractiveBrowsing( final RealRandomAccessible< T > rra, final Converter< T, ARGBType > converter )
	{
		/* center shift */
		final AffineTransform2D centerShift = new AffineTransform2D();
		centerShift.set( 1, 0, -width / 2.0, 0, 1, -height / 2.0 );

		/* center un-shift */
		final AffineTransform2D centerUnShift = new AffineTransform2D();
		centerUnShift.set( 1, 0, width / 2.0, 0, 1, height / 2.0 );

		/* initialize rotation */
		final AffineTransform2D rotation = new AffineTransform2D();
		rotation.scale( 0.05 );

		rotation.preConcatenate( centerUnShift );

		viewer = new InteractiveRealViewer2D< T >( width, height, rra, rotation, converter );
		final InteractiveDisplayCanvas< AffineTransform2D > canvas = viewer.getDisplayCanvas();
		//canvas.setTransformEventHandler( new JuliaListener( canvas ) );
		//canvas.addTransformListener( new TransformScaleHandler() );
		canvas.addOverlayRenderer( new LogoPainter() );
		viewer.requestRepaint();
	}

	public static void main( String[] args )
	{
		final ArrayList< double[] > coordinates = Reader.readCoordinates( new File( "/Users/spreibi/Downloads/patterns_examples/locations.txt" ) );

		System.out.println( "Loaded " + coordinates.size() + " coordinates." );

		final RealInterval interval = Reader.getInterval( coordinates );
		final Interval renderInterval = ImgLib2.roundRealInterval( interval );

		System.out.println( "Interval: " + Util.printRealInterval( interval ) );

		final SimpleStats distanceStats = ImgLib2.distanceStats( coordinates );

		System.out.println( "Median Distance: " + distanceStats.median );
		System.out.println( "Average Distance: " + distanceStats.avg );
		System.out.println( "Min Distance: " + distanceStats.min );
		System.out.println( "Max Distance: " + distanceStats.max );

		System.out.println( "Interval: " + Util.printRealInterval( interval ) );

		final HashMap< String, double[] > values = Reader.readGenes( new File("/Users/spreibi/Downloads/patterns_examples/dge_normalized_small.txt" ), coordinates.size() );

		System.out.println( "Loaded: " + values.keySet().size() + " genes with " + coordinates.size() + " values each." );

		for ( final String gene : values.keySet() )
			System.out.println( gene );

		System.out.println( "Computing ... " );

		final FloatType outofboundsFloat = new FloatType( -1 );

		final RealPointSampleList< FloatType > data = ImgLib2.wrapFloat( coordinates, values.get( "Pcp4" ) );

		final Converter< FloatType, ARGBType > converter = new Converter< FloatType, ARGBType >()
		{
			@Override
			public void convert( final FloatType input, final ARGBType output )
			{
				final int value = Math.min( 255, Math.max( 0, Math.round( ( (input.get() + 1.0f)/6.0f)*255f ) ) );
				output.set( ( ( ( value << 8 ) | value ) << 8 ) | value | 0xff000000 );
			}
		};

		new InteractiveBrowsing<>( ImgLib2.render( data, outofboundsFloat, distanceStats.median / 2.0 ), converter );
	}
}
