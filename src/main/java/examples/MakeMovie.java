package examples;

import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.view.Views;

public class MakeMovie
{
	public static < T extends NumericType< T > > RandomAccessibleInterval< T > scale(
			final RandomAccessibleInterval< T > img,
			final Interval outputInterval,
			final double scale )
	{
		return scale(img, outputInterval, new double[] { outputInterval.dimension( 0 ) / 2, outputInterval.dimension( 1 ) / 2 }, scale);
	}

	public static < T extends NumericType< T > > RandomAccessibleInterval< T > scale(
			final RandomAccessibleInterval< T > img,
			final Interval outputInterval,
			final double[] vector,
			final double scale )
	{
		final AffineTransform2D scaling = new AffineTransform2D();
		scaling.translate( -img.dimension( 0 ) / 2 - img.min( 0 ), -img.dimension( 1 ) / 2 - img.min( 1 ) );
		scaling.scale( scale, scale );
		scaling.translate( vector );

		RandomAccessibleInterval< T > scaledImg =
				Views.interval(
						RealViews.affine(
								Views.interpolate(
										Views.extendZero( img ),
										new NearestNeighborInterpolatorFactory<>() ),
								scaling ),
						outputInterval );

		return scaledImg;
	}

	public static void main( String[] args )
	{
		new ImageJ();

		final ImagePlusImg< ARGBType, ?> imgStack = ImagePlusImgs.from( new ImagePlus( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/3D vis/Projection low-res-rgb.tif") );
		final ImagePlusImg< ARGBType, ?> imgStack2 = ImagePlusImgs.from( new ImagePlus( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/3D vis/Projection high-res-rgb.tif") );

		final Interval outputInterval = new FinalInterval(imgStack.dimension(0 ) / 2, imgStack.dimension(1 ) / 2);
		final ImageStack stack = new ImageStack( (int)outputInterval.dimension( 0 ), (int)outputInterval.dimension( 1 ) );

		/*
		// blending scaling
		for ( double s = 0.5; s <= 2.005; s += 0.01 )
		{
			final double sf = s;

			double a1 = Math.min( 1, Math.max( 0, 1.0 - (( sf - 0.5 ) / 0.75 ) ) );
			double a2 = Math.min( 1, Math.max( 0, ( sf - 0.75 ) / 1.25 ) );

			final double sum = a1 + a2;

			final double a1f = a1 / sum;
			final double a2f = a2 / sum;

			System.out.println( sf + ", " + a1 + ", " + a2 );

			final double[] vector = new double[] {
					outputInterval.dimension( 0 ) / 2,
					outputInterval.dimension( 1 ) / 2 + 150 * (s-0.5)/(2.0-0.5) };
			final RandomAccessibleInterval<ARGBType> scaled1 = scale( Views.hyperSlice( imgStack, 2, 20 ), outputInterval, vector, s );
			final RandomAccessibleInterval<ARGBType> scaled2 = scale( Views.hyperSlice( imgStack2, 2, 20 ), outputInterval, vector, s / 2.0 );

			RandomAccessibleInterval<ARGBType> averaged =
					Converters.convert(
							scaled1,
							scaled2,
							(i1,i2,o) -> {
								o.set( i1 );
								o.mul( a1f );
								final ARGBType tmp = new ARGBType();
								tmp.set( i2 );
								tmp.mul( a2f );
								o.add( tmp );
							},
							new ARGBType() );

			stack.addSlice( ImageJFunctions.wrapRGB( averaged, "s=" + s ).getProcessor() );
		}
		*/

		/*
		//
		// LOW RES
		//
		int count = 0;

		// add every second slice only
		for ( ; count < imgStack.dimension( 2 ) - 114; )
		{
			System.out.println( count );
			stack.addSlice( ImageJFunctions.wrapRGB( scale( Views.hyperSlice( imgStack, 2, count ), outputInterval, 0.5 ), "" ).getProcessor() );
			count += 2;
			count = count % (int)imgStack.dimension( 2 );
		}

		// zoom in while rotating
		for ( double s = 0.5; s <= 1.005; s += 0.0075 )
		{
			System.out.println( count );
			stack.addSlice( ImageJFunctions.wrapRGB( scale( Views.hyperSlice( imgStack, 2, count ), outputInterval, s), "s=" + s ).getProcessor() );
			count += 2;
			count = count % (int)imgStack.dimension( 2 );
		}

		System.out.println( "fullres");

		// rotating while at full zoom
		for ( int i = 0; i < 50; ++i )
		{
			System.out.println( count );
			stack.addSlice( ImageJFunctions.wrapRGB( scale( Views.hyperSlice( imgStack, 2, count ), outputInterval, 1.0 ), "s=" + 1.0 ).getProcessor() );
			count += 2;
			count = count % (int)imgStack.dimension( 2 );
		}

		// zoom out while rotating
		for ( double s = 1.0; s >= 0.495; s -= 0.0075 )
		{
			System.out.println( count );
			stack.addSlice( ImageJFunctions.wrapRGB( scale( Views.hyperSlice( imgStack, 2, count ), outputInterval, s), "s=" + s ).getProcessor() );
			count += 2;
			count = count % (int)imgStack.dimension( 2 );
		}

		// rotate until end
		while ( count < imgStack.dimension( 2 ) - 2 )
		{
			System.out.println( count );
			stack.addSlice( ImageJFunctions.wrapRGB( scale( Views.hyperSlice( imgStack, 2, count ), outputInterval, 0.5 ), "s=" + 0.5 ).getProcessor() );
			count += 2;
			count = count % (int)imgStack.dimension( 2 );
		}
		*/


		//
		// HIGH RES
		//
		int count = 0;

		// add every second slice only
		while (count < imgStack.dimension(2 ) - 114) {
			System.out.println( count );
			stack.addSlice( ImageJFunctions.wrapRGB( scale( Views.hyperSlice( imgStack2, 2, count ), outputInterval, 0.5 / 2.0 ), "" ).getProcessor() );
			count += 2;
			count = count % (int)imgStack2.dimension( 2 );
		}

		// zoom in while rotating
		for ( double s = 0.5; s <= 2.005; s += 0.0075*3 )
		{
			System.out.println( count );
			final double[] vector = new double[] {
					outputInterval.dimension( 0 ) / 2,
					outputInterval.dimension( 1 ) / 2 + 150 * (s-0.5)/(2.0-0.5) };

			stack.addSlice( ImageJFunctions.wrapRGB( scale( Views.hyperSlice( imgStack2, 2, count ), outputInterval, vector, s / 2.0), "s=" + s ).getProcessor() );
			count += 2;
			count = count % (int)imgStack2.dimension( 2 );
		}

		System.out.println( "fullres");

		// rotating while at full zoom
		for ( int i = 0; i < 50; ++i )
		{
			System.out.println( count );
			final double[] vector = new double[] {
					outputInterval.dimension( 0 ) / 2,
					outputInterval.dimension( 1 ) / 2 + 150 };

			stack.addSlice( ImageJFunctions.wrapRGB( scale( Views.hyperSlice( imgStack2, 2, count ), outputInterval, vector, 1.0 ), "s=" + 1.0 ).getProcessor() );
			count += 2;
			count = count % (int)imgStack2.dimension( 2 );
		}

		// zoom out while rotating
		for ( double s = 2.0; s >= 0.495; s -= 0.0075*3 )
		{
			System.out.println( count );
			final double[] vector = new double[] {
					outputInterval.dimension( 0 ) / 2,
					outputInterval.dimension( 1 ) / 2 + 150 * (s-0.5)/(2.0-0.5) };

			stack.addSlice( ImageJFunctions.wrapRGB( scale( Views.hyperSlice( imgStack2, 2, count ), outputInterval, vector, s / 2.0), "s=" + s ).getProcessor() );
			count += 2;
			count = count % (int)imgStack2.dimension( 2 );
		}

		// rotate until end
		while ( count < imgStack.dimension( 2 ) - 2 )
		{
			System.out.println( count );
			stack.addSlice( ImageJFunctions.wrapRGB( scale( Views.hyperSlice( imgStack2, 2, count ), outputInterval, 0.5 / 2.0 ), "s=" + 0.5 ).getProcessor() );
			count += 2;
			count = count % (int)imgStack2.dimension( 2 );
		}

		/*
		//
		// HIGH RES INTERPOLATED WITH LOW RES
		//
		int count = 0;

		// add every second slice only
		for ( ; count < imgStack.dimension( 2 ) - 114; )
		{
			System.out.println( count );
			stack.addSlice( ImageJFunctions.wrapRGB( scale( Views.hyperSlice( imgStack, 2, count ), outputInterval, 0.5 ), "" ).getProcessor() );
			count += 2;
			count = count % (int)imgStack.dimension( 2 );
		}

		// zoom in while rotating
		for ( double s = 0.5; s <= 2.005; s += 0.0075*3 )
		{
			final double sf = s;

			double a1 = Math.min( 1, Math.max( 0, 1.0 - (( sf - 0.5 ) / 0.75 ) ) );
			double a2 = Math.min( 1, Math.max( 0, ( sf - 0.75 ) / 1.25 ) );

			final double sum = a1 + a2;

			final double a1f = a1 / sum;
			final double a2f = a2 / sum;

			System.out.println( sf + ", " + a1 + ", " + a2 );

			final double[] vector = new double[] {
					outputInterval.dimension( 0 ) / 2,
					outputInterval.dimension( 1 ) / 2 + 150 * (s-0.5)/(2.0-0.5) };
			final RandomAccessibleInterval<ARGBType> scaled1 = scale( Views.hyperSlice( imgStack, 2, count ), outputInterval, vector, s );
			final RandomAccessibleInterval<ARGBType> scaled2 = scale( Views.hyperSlice( imgStack2, 2, count ), outputInterval, vector, s / 2.0 );

			RandomAccessibleInterval<ARGBType> averaged =
					Converters.convert(
							scaled1,
							scaled2,
							(i1,i2,o) -> {
								o.set( i1 );
								o.mul( a1f );
								final ARGBType tmp = new ARGBType();
								tmp.set( i2 );
								tmp.mul( a2f );
								o.add( tmp );
							},
							new ARGBType() );

			stack.addSlice( ImageJFunctions.wrapRGB( averaged, "s=" + s ).getProcessor() );
			count += 2;
			count = count % (int)imgStack2.dimension( 2 );
		}

		System.out.println( "fullres");

		// rotating while at full zoom
		for ( int i = 0; i < 50; ++i )
		{
			System.out.println( count );
			final double[] vector = new double[] {
					outputInterval.dimension( 0 ) / 2,
					outputInterval.dimension( 1 ) / 2 + 150 };

			stack.addSlice( ImageJFunctions.wrapRGB( scale( Views.hyperSlice( imgStack2, 2, count ), outputInterval, vector, 1.0 ), "s=" + 1.0 ).getProcessor() );
			count += 2;
			count = count % (int)imgStack2.dimension( 2 );
		}

		// zoom out while rotating
		for ( double s = 2.0; s >= 0.495; s -= 0.0075*3 )
		{
			System.out.println( count );

			final double sf = s;

			double a1 = Math.min( 1, Math.max( 0, 1.0 - (( sf - 0.5 ) / 0.75 ) ) );
			double a2 = Math.min( 1, Math.max( 0, ( sf - 0.75 ) / 1.25 ) );

			final double sum = a1 + a2;

			final double a1f = a1 / sum;
			final double a2f = a2 / sum;

			System.out.println( sf + ", " + a1 + ", " + a2 );

			final double[] vector = new double[] {
					outputInterval.dimension( 0 ) / 2,
					outputInterval.dimension( 1 ) / 2 + 150 * (s-0.5)/(2.0-0.5) };
			final RandomAccessibleInterval<ARGBType> scaled1 = scale( Views.hyperSlice( imgStack, 2, count ), outputInterval, vector, s );
			final RandomAccessibleInterval<ARGBType> scaled2 = scale( Views.hyperSlice( imgStack2, 2, count ), outputInterval, vector, s / 2.0 );

			RandomAccessibleInterval<ARGBType> averaged =
					Converters.convert(
							scaled1,
							scaled2,
							(i1,i2,o) -> {
								o.set( i1 );
								o.mul( a1f );
								final ARGBType tmp = new ARGBType();
								tmp.set( i2 );
								tmp.mul( a2f );
								o.add( tmp );
							},
							new ARGBType() );

			stack.addSlice( ImageJFunctions.wrapRGB( averaged, "s=" + s ).getProcessor() );
			count += 2;
			count = count % (int)imgStack2.dimension( 2 );
		}

		// rotate until end
		while ( count < imgStack.dimension( 2 ) - 2 )
		{
			System.out.println( count );
			stack.addSlice( ImageJFunctions.wrapRGB( scale( Views.hyperSlice( imgStack, 2, count ), outputInterval, 0.5 ), "s=" + 0.5 ).getProcessor() );
			count += 2;
			count = count % (int)imgStack2.dimension( 2 );
		}
		*/
		new ImagePlus( "scale in", stack ).show();
	}
}
