package gui.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.List;

import align.PointST;
import bdv.util.BdvHandle;
import bdv.viewer.OverlayRenderer;
import bdv.viewer.SourceGroup;
import bdv.viewer.SynchronizedViewerState;
import bdv.viewer.TransformListener;
import mpicbg.models.PointMatch;
import net.imglib2.realtransform.AffineTransform3D;

public class SIFTOverlay implements OverlayRenderer, TransformListener< AffineTransform3D >
{
	final List< PointMatch> inliers;
	private final AffineTransform3D viewerTransform;
	final BdvHandle bdvhandle;

	final Color colorDefault = Color.gray;
	final Color colorSelected = Color.yellow;

	final Stroke strokeDefault = new BasicStroke(0.5f);
	final Stroke strokeSelected = new BasicStroke(1.5f);

	public SIFTOverlay( final List< PointMatch> inliers, final BdvHandle bdvhandle )
	{
		this.inliers = inliers;
		this.bdvhandle = bdvhandle;
		this.viewerTransform = new AffineTransform3D();
	}

	private double getPointSize( final double[] gPos )
	{
		return 13.0;
	}

	public synchronized void setInliers( final List< PointMatch> inliers )
	{
		this.inliers.clear();
		this.inliers.addAll( inliers );
	}

	@Override
	public void transformChanged( final AffineTransform3D transform )
	{
		viewerTransform.set( transform );
	}

	@Override
	public void drawOverlays( final Graphics g )
	{
		// find out which gene is currently selected
		final SynchronizedViewerState state = bdvhandle.getViewerPanel().state();
		final SourceGroup gr = state.getCurrentGroup();
		final String gene = state.getGroupName( gr );
		//System.out.println( "Currently active: " + name );
		
		final Graphics2D graphics = ( Graphics2D ) g;
		final double[] lPos = new double[ 3 ];
		final double[] gPos = new double[ 3 ];
		//final AffineTransform3D transform = new AffineTransform3D();

		for ( final PointMatch pm : inliers )
		{
			final Color color;
			final Stroke stroke;
			if ( ((PointST) pm.getP2()).getGene().equals( gene ) )
			{
				color = colorSelected;
				stroke = strokeSelected;
			}
			else
			{
				color = colorDefault;
				stroke = strokeDefault;
			}

			lPos[ 0 ] = pm.getP2().getL()[ 0 ];
			lPos[ 1 ] = pm.getP2().getL()[ 1 ];
			lPos[ 2 ] = 0;

			viewerTransform.apply( lPos, gPos );
			final double size = getPointSize( gPos );
			final int x = ( int ) ( gPos[ 0 ] - 0.5 * size );
			final int y = ( int ) ( gPos[ 1 ] - 0.5 * size );
			final int w = ( int ) size;

			graphics.setColor( color );
			graphics.setStroke( stroke);
			graphics.drawOval( x, y, w, w);
			graphics.drawLine(x, y, x+w, y+w);
			graphics.drawLine(x+w, y, x, y+w);
		}
	}

	@Override
	public void setCanvasSize( final int width, final int height )
	{}
}
