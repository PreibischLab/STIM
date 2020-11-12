package gui;

import data.STData;
import data.STDataStatistics;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform2D;

public class STDataAssembly
{
	final private STData data;
	final private STDataStatistics statistics;
	final private AffineTransform2D transform;
	final private AffineTransform intensityTransform;

	public STDataAssembly(
			final STData data,
			final STDataStatistics statistics,
			final AffineTransform2D transform,
			final AffineTransform intensityTransform )
	{
		this.data = data;
		this.statistics = statistics;
		this.transform = transform;
		this.intensityTransform = intensityTransform;
	}

	public STDataAssembly( final STData data )
	{
		this( data, new STDataStatistics( data ), new AffineTransform2D(), new AffineTransform( 1 ) );
	}

	public STData data() { return data; }
	public STDataStatistics statistics() { return statistics; }
	public AffineTransform2D transform() { return transform; }
	public AffineTransform intensityTransform() { return intensityTransform; }
}
