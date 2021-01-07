package tools;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import bdv.BigDataViewer;
import bdv.cache.CacheControl;
import bdv.util.BdvStackSource;
import bdv.viewer.ViewerPanel;
import bdv.viewer.overlay.MultiBoxOverlayRenderer;
import bdv.viewer.overlay.ScaleBarOverlayRenderer;
import bdv.viewer.render.MultiResolutionRenderer;
import bdv.viewer.state.ViewerState;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.process.ColorProcessor;
import imglib2.NumericAffineTransform3D;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPositionable;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.img.list.ListImg;
import net.imglib2.img.list.ListLocalizingCursor;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.PainterThread;
import net.imglib2.ui.RenderTarget;
import net.imglib2.view.Views;

public class BDVFlyThrough
{
	final public static ArrayList< AffineTransform3D > viewerTransforms = new ArrayList<>();
	public static boolean skipDialog = false;
	public static String defaultPath = "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st";
	public static int interpolateSteps = 100;
	public static double defaultSigma = 0;
	public static boolean goBackToInitialTransform = true;

	public static String[] interpolationMethods = new String[] { "Linear",  "Linear with Smoothing", "Cubic Spline" };
	public static int defaultMethod = 2;

	public static boolean defaultScalebar = false;
	public static boolean defaultBoxes = false;
	public static int defaultWidth = 0;

	public interface CallbackBDV
	{
		public BdvStackSource< ? > updateBDV( final int frame, final BdvStackSource< ? > currentStackSource );
	}

	public static void addCurrentViewerTransform( final ViewerPanel bdvViewerPanel )
	{
		AffineTransform3D currentViewerTransform = bdvViewerPanel.getDisplay().getTransformEventHandler().getTransform().copy();
		viewerTransforms.add( currentViewerTransform );
		System.out.println( "Added transform: " + currentViewerTransform  + ", #transforms=" + viewerTransforms.size() );
	}

	public static void clearAllViewerTransform()
	{
		viewerTransforms.clear();
		System.out.println( "Cleared all transforms." );
	}

	public static void renderScreenshot( final ViewerPanel bdvViewerPanel )
	{
		final ViewerState renderState = bdvViewerPanel.getState();
		final int canvasW = bdvViewerPanel.getDisplay().getWidth();
		final int canvasH = bdvViewerPanel.getDisplay().getHeight();

		int width = canvasW;
		int height = canvasH;

		final GenericDialog gd = new GenericDialog( "Select screenshot size" );

		if ( defaultWidth <= 0 )
			defaultWidth = width;

		gd.addNumericField( "Width (current width=" + width + ", height scaled accordingly)", defaultWidth, 0 );
		gd.addCheckbox( "Show_scalebar", defaultScalebar );
		gd.addCheckbox( "Show_boxes", defaultBoxes );
		
		gd.showDialog();
		if ( gd.wasCanceled())
			return;

		width = defaultWidth = (int)Math.round( gd.getNextNumber() );
		height = (int)Math.round( ( (double)width / (double)canvasW ) * height );
		final boolean showScaleBar = defaultScalebar = gd.getNextBoolean();
		final boolean showBoxes = defaultBoxes = gd.getNextBoolean();

		System.out.println( "Making screenshot with resolution: " + width + "x" + height + ", boxes=" + showBoxes + ", scalebar=" + showScaleBar );

		final AffineTransform3D affine = new AffineTransform3D();
		renderState.getViewerTransform( affine );
		affine.set( affine.get( 0, 3 ) - canvasW / 2, 0, 3 );
		affine.set( affine.get( 1, 3 ) - canvasH / 2, 1, 3 );
		affine.scale( ( double ) width / canvasW );
		affine.set( affine.get( 0, 3 ) + width / 2, 0, 3 );
		affine.set( affine.get( 1, 3 ) + height / 2, 1, 3 );
		renderState.setViewerTransform( affine );

		final MyRenderTarget target = new MyRenderTarget( width, height );
		final MultiResolutionRenderer renderer = new MultiResolutionRenderer(
				target, new PainterThread( null ), new double[] { 1 }, 0, false, 1, null, false,
				bdvViewerPanel.getOptionValues().getAccumulateProjectorFactory(), new CacheControl.Dummy() );

		renderer.requestRepaint();
		renderer.paint( renderState );

		renderScalebar( showScaleBar ? new ScaleBarOverlayRenderer() : null, target, renderState, width, height );
		renderBoxes( showBoxes ? new MultiBoxOverlayRenderer( width, height ) : null, target, renderState, width, height );

		new ImagePlus( "BDV Screenshot", new ColorProcessor( target.bi ) ).show();
	}

	public static void record( final CallbackBDV callback )
	{
		if ( viewerTransforms.size() < 2 )
		{
			System.out.println( "At least two transformations are required. Stopping. (Press 'a' while the BigStitcher window is in focus to define them, you will get a confirmation in the Log window)" );
			return;
		}

		final ArrayList< AffineTransform3D > viewerTransformsLocal = new ArrayList<>();

		for ( int i = 0; i < viewerTransforms.size(); ++i )
			viewerTransformsLocal.add( viewerTransforms.get( i ).copy() );

		if ( !skipDialog )
		{
			final GenericDialog gd = new GenericDialog( "Options for movie recording" );
			gd.addStringField( "Movie directory", defaultPath, 45 );
			gd.addNumericField( "Interpolation steps between keypoints", interpolateSteps, 0 );
			gd.addChoice( "Transformation_interpolation method", interpolationMethods, interpolationMethods[ defaultMethod ] );
			gd.addCheckbox( "Go_back to initial transform", goBackToInitialTransform );
			gd.addMessage( "" );
			gd.addCheckbox( "Show_scalebar", defaultScalebar );
			gd.addCheckbox( "Show_boxes", defaultBoxes );

			gd.showDialog();
			if ( gd.wasCanceled())
				return;

			defaultPath = gd.getNextString();
			interpolateSteps = (int)Math.round( gd.getNextNumber() );
			defaultMethod = gd.getNextChoiceIndex();
			goBackToInitialTransform = gd.getNextBoolean();
			defaultScalebar = gd.getNextBoolean();
			defaultBoxes = gd.getNextBoolean();

			if ( defaultMethod == 1 )
			{
				final GenericDialog gd2 = new GenericDialog( "Smoothing option" );
				gd2.addNumericField( "Sigma", defaultSigma, 1 );

				gd2.showDialog();
				if ( gd2.wasCanceled())
					return;

				defaultSigma = Math.max( 0, gd2.getNextNumber() );
			}

			if ( goBackToInitialTransform )
				viewerTransformsLocal.add( viewerTransformsLocal.get( 0 ).copy() );
		}

		System.out.println( "Recording images for " + viewerTransformsLocal.size() + " transforms, interpolated with " + interpolateSteps + " steps using '" + interpolationMethods[ defaultMethod ] + "' in between to directory " + defaultPath );

		BdvStackSource< ? > source = callback.updateBDV( 0, null );
		ViewerPanel bdvViewerPanel = source.getBdvHandle().getViewerPanel();
		ViewerState renderState = bdvViewerPanel.getState();
		final int canvasW = bdvViewerPanel.getDisplay().getWidth();
		final int canvasH = bdvViewerPanel.getDisplay().getHeight();

		final int width = canvasW;
		final int height = canvasH;
		
		final AffineTransform3D affine = new AffineTransform3D();
		renderState.getViewerTransform( affine );
		affine.set( affine.get( 0, 3 ) - canvasW / 2, 0, 3 );
		affine.set( affine.get( 1, 3 ) - canvasH / 2, 1, 3 );
		affine.scale( ( double ) width / canvasW );
		affine.set( affine.get( 0, 3 ) + width / 2, 0, 3 );
		affine.set( affine.get( 1, 3 ) + height / 2, 1, 3 );
		renderState.setViewerTransform( affine );

		final ScaleBarOverlayRenderer scalebar = defaultScalebar ? new ScaleBarOverlayRenderer() : null;
		final MultiBoxOverlayRenderer boxRender = defaultBoxes ? new MultiBoxOverlayRenderer( width, height ) : null;

		final MyRenderTarget target = new MyRenderTarget( width, height );
		MultiResolutionRenderer renderer = new MultiResolutionRenderer(
				target, new PainterThread( null ), new double[] { 1 }, 0, false, 1, null, false,
				bdvViewerPanel.getOptionValues().getAccumulateProjectorFactory(), new CacheControl.Dummy() );

		final ArrayList< AffineTransform3D > transforms = interpolateTransforms( viewerTransformsLocal, defaultMethod == 2, defaultSigma, interpolateSteps );

		IJ.showProgress( 0.0 );

		final File dir = new File( defaultPath, "movie" );

		if ( !dir.exists() )
		{
			System.out.println( "Creating directory: " + dir.getAbsolutePath() );
			dir.mkdirs();
		}

		for ( int i = 0; i < transforms.size(); ++i )
		{
			System.out.println( (i+1) + "/" + transforms.size() + ": " + transforms.get( i ) );

			BdvStackSource< ? > oldSource = source;
			source = callback.updateBDV( i, source );

			if ( oldSource != source )
			{
				bdvViewerPanel = source.getBdvHandle().getViewerPanel();
				renderState = bdvViewerPanel.getState();
				renderer = new MultiResolutionRenderer(
						target, new PainterThread( null ), new double[] { 1 }, 0, false, 1, null, false,
						bdvViewerPanel.getOptionValues().getAccumulateProjectorFactory(), new CacheControl.Dummy() );
			}

			renderState.setViewerTransform( transforms.get( i ) );

			renderer.requestRepaint();
			renderer.paint( renderState );

			renderScalebar( scalebar, target, renderState, width, height );
			renderBoxes( boxRender, target, renderState, width, height );

			try
			{
				final File file = new File( String.format( "%s/img-%05d.png", dir, i ) );
				System.out.println( "Writing file: " + file.getAbsolutePath() );

				ImageIO.write( target.bi, "png", file );
			}
			catch ( IOException e )
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			IJ.showProgress( (double)(i+1)/(double)transforms.size() );
		}

		IJ.showProgress( 1.0 );

		bdvViewerPanel.setCurrentViewerTransform( transforms.get( 0 ) );

		System.out.println( "Done" );
	}

	protected static void renderScalebar( final ScaleBarOverlayRenderer scalebar, final MyRenderTarget target, final ViewerState renderState, final int width, final int height )
	{
		if ( scalebar != null )
		{
			final Graphics2D g2 = target.bi.createGraphics();
			g2.setClip( 0, 0, width, height );
			scalebar.setViewerState( renderState );
			scalebar.paint( g2 );
		}
	}

	protected static void renderBoxes( final MultiBoxOverlayRenderer boxRender, final MyRenderTarget target, final ViewerState renderState, final int width, final int height )
	{
		if ( boxRender != null )
		{
			final Graphics2D g2 = target.bi.createGraphics();
			g2.setClip( 0, 0, width, height );
			boxRender.setViewerState( renderState );
			boxRender.paint( g2 );
		}
	}

	public static ArrayList< AffineTransform3D > interpolateTransforms( final ArrayList< AffineTransform3D > steps, final boolean useSpline, final double sigma, final int interpolateSteps )
	{
		if ( steps.size() == 1 )
			return steps;
		else
		{
			final ArrayList< AffineTransform3D > interpolated = new ArrayList<>();

			if ( useSpline )
			{
				final ArrayList< RealPoint > transforms = new ArrayList<>();

				for ( final AffineTransform3D transform : steps )
					transforms.add( new RealPoint( transform.getRowPackedCopy() ) );

				final MonotoneCubicSpline spline = MonotoneCubicSpline.createMonotoneCubicSpline( transforms );

				final RealPoint p = new RealPoint( transforms.get( 0 ).numDimensions() );
				final double[] matrix = new double[ p.numDimensions() ];

				for ( int i = 0; i < transforms.size() - 1; ++i )
					for ( int f = 0; f < interpolateSteps; ++f )
					{
						final double x = i + (double)f / (double)interpolateSteps;

						spline.interpolate( x, p );

						for ( int d = 0; d < matrix.length; ++d )
							matrix[ d ] = p.getDoublePosition( d );

						final AffineTransform3D interp = new AffineTransform3D();
						interp.set( matrix );
						interpolated.add( interp );
					}
			}
			else
			{
				for ( int i = 0; i < steps.size() - 1; ++i )
				{
					final AffineTransform3D first = steps.get( i );
					final AffineTransform3D second = steps.get( i + 1 );
	
					final double[] a = first.getRowPackedCopy();
					final double[] b = second.getRowPackedCopy();
					
					for ( int j = 0; j <= interpolateSteps; ++j )
					{
						final double ratioA = 1.0 - (double)j/(double)interpolateSteps;
						final double ratioB = (double)j/(double)interpolateSteps;
	
						final double[] c = new double[ a.length ];
	
						for ( int k = 0; k < a.length; ++k )
							c[ k ] = a[ k ] * ratioA + b[ k ] * ratioB;
	
						final AffineTransform3D interp = new AffineTransform3D();
						interp.set( c );
						interpolated.add( interp );
					}
				}
			}

			if ( sigma > 0 )
			{
				System.out.println( "Smoothing transforms with sigma=" + sigma );

				final ListImg< NumericAffineTransform3D > transformImg = new ListImg< NumericAffineTransform3D >( new long[]{ interpolated.size() }, new NumericAffineTransform3D( new AffineTransform3D() ) );
				final ListLocalizingCursor< NumericAffineTransform3D > it = transformImg.localizingCursor();

				for ( final AffineTransform3D model : interpolated )
				{
					it.fwd();
					it.set( new NumericAffineTransform3D( model.copy() ) );
				}

				Gauss3.gauss( sigma, Views.extendBorder( transformImg ), transformImg );

				it.reset();
				for ( int i = 0; i < interpolated.size(); ++i )
					interpolated.set( i, it.next().getTransform().copy() ); // could be a native type, so copy in necessary
			}

			return interpolated;
		}
	}

	static class MyRenderTarget implements RenderTarget
	{
		BufferedImage bi;

		final int width;
		final int height;

		public MyRenderTarget( final int width, final int height )
		{
			this.width = width;
			this.height = height;
		}

		@Override
		public BufferedImage setBufferedImage( final BufferedImage bufferedImage )
		{
			bi = bufferedImage;
			return null;
		}

		@Override
		public int getWidth()
		{
			return width;
		}

		@Override
		public int getHeight()
		{
			return height;
		}
	}


	/**
	 * n-dimensional extension of the monotone cubic spline implementation by Leszek Wach:
	 * https://gist.github.com/lecho/7627739#file-splineinterpolation-java
	 * 
	 * @author Stephan Preibisch
	 *
	 */
	public static class MonotoneCubicSpline
	{
		private final List<Double> mX;
		private final List< ? extends RealLocalizable > mY;
		private final double[][] mM;
		private final int nd;

		private MonotoneCubicSpline(final List<Double> x, final List< ? extends RealLocalizable > y, final double[][] m )
		{
			this.mX = x;
			this.mY = y;
			this.mM = m;
			this.nd = y.get( 0 ).numDimensions();
		}

		public static MonotoneCubicSpline createMonotoneCubicSpline( final List<? extends RealLocalizable> y )
		{
			if ( y == null || y.size() < 2 )
				throw new IllegalArgumentException("There must be at least two control points and the arrays must be of equal length.");

			final ArrayList< Double > x = new ArrayList<>();

			for ( int i = 0; i < y.size(); ++i )
				x.add( (double)i );

			return createMonotoneCubicSpline( x, y );
		}

		/*
		 * Creates a monotone cubic spline from a given set of control points.
		 * 
		 * The spline is guaranteed to pass through each control point exactly. Moreover, assuming the control points are
		 * monotonic (Y is non-decreasing or non-increasing) then the interpolated values will also be monotonic.
		 * 
		 * This function uses the Fritsch-Carlson method for computing the spline parameters.
		 * http://en.wikipedia.org/wiki/Monotone_cubic_interpolation
		 * 
		 * @param x
		 *            The X component of the control points, strictly increasing.
		 * @param y
		 *            The Y component of the control points
		 * @return
		 * 
		 * @throws IllegalArgumentException
		 *             if the X or Y arrays are null, have different lengths or have fewer than 2 values.
		 */
		public static MonotoneCubicSpline createMonotoneCubicSpline( final List<Double> x, final List<? extends RealLocalizable> y )
		{
			if (x == null || y == null || x.size() != y.size() || x.size() < 2) {
				throw new IllegalArgumentException("There must be at least two control "
						+ "points and the arrays must be of equal length.");
			}

			final int nd = y.get( 0 ).numDimensions();

			final int n = x.size();
			double[][] d = new double[n - 1][nd]; // could optimize this out
			double[][] m = new double[n][nd];

			// Compute slopes of secant lines between successive points.
			for (int i = 0; i < n - 1; i++)
			{
				final double h = x.get(i + 1) - x.get(i);
				if (h <= 0f)
					throw new IllegalArgumentException("The control points must all have strictly increasing X values.");

				for ( int dim = 0; dim < nd; ++dim )
					d[i][ dim ] = (y.get(i + 1).getDoublePosition( dim ) - y.get(i).getDoublePosition( dim )) / h;
			}

			// Initialize the tangents as the average of the secants.
			for ( int dim = 0; dim < nd; ++dim )
			{
				m[0][ dim ] = d[0][ dim ];
				for (int i = 1; i < n - 1; i++)
					m[i][ dim ] = (d[i - 1][ dim ] + d[i][ dim ]) * 0.5f;

				m[n - 1][ dim ] = d[n - 2][ dim ];

				// Update the tangents to preserve monotonicity.
				for (int i = 0; i < n - 1; i++)
				{
					if (d[i][ dim ] == 0f) { // successive Y values are equal
						m[i][ dim ] = 0f;
						m[i + 1][ dim ] = 0f;
					}
					else
					{
						final double a = m[i][ dim ] / d[i][ dim ];
						final double b = m[i + 1][ dim ] / d[i][ dim ];
						final double h = Math.hypot(a, b);
						if (h > 9f)
						{
							final double t = 3f / h;
							m[i][ dim ] = t * a * d[i][ dim ];
							m[i + 1][ dim ] = t * b * d[i][ dim ];
						}
					}
				}
			}

			return new MonotoneCubicSpline(x, y, m);
		}

		/*
		 * Interpolates the value of Y = f(X) for given X. Clamps X to the domain of the spline.
		 * 
		 * @param x
		 *            The X value.
		 * @return The interpolated Y = f(X) value.
		 */
		public < P extends RealPositionable > void interpolate( final double x, final P p )
		{
			// Handle the boundary cases.
			final int n = mX.size();
			if (Double.isNaN(x))
			{
				for ( int d = 0; d < nd; ++d )
					p.setPosition( 0, d );
				return;
			}

			if (x <= mX.get(0))
			{
				p.setPosition( mY.get( 0 ) );
				return;
			}

			if (x >= mX.get(n - 1))
			{
				p.setPosition( mY.get( n - 1 ) );
				return;
			}

			// Find the index 'i' of the last point with smaller X.
			// We know this will be within the spline due to the boundary tests.
			int i = 0;
			while (x >= mX.get(i + 1))
			{
				i += 1;
				if (x == mX.get(i))
				{
					p.setPosition( mY.get( i ) );
					return;
				}
			}

			// Perform cubic Hermite spline interpolation.
			for ( int dim = 0; dim < nd; ++dim )
			{
				final double h = mX.get(i + 1) - mX.get(i);
				final double t = (x - mX.get(i)) / h;

				p.setPosition( 
						(mY.get(i).getDoublePosition( dim ) * (1 + 2 * t) + h * mM[i][ dim ] * t) * (1 - t) * (1 - t)
						+ (mY.get(i + 1).getDoublePosition( dim ) * (3 - 2 * t) + h * mM[i + 1][ dim ] * (t - 1)) * t * t,
						dim );
			}
		}
	}
}
