package gui;

import data.STData;
import data.STDataStatistics;
import net.imglib2.realtransform.AffineTransform2D;

public class STDataAssembly
{
	final private STData data;
	final private STDataStatistics statistics;
	final private AffineTransform2D transform;

	public STDataAssembly(
			final STData data,
			final STDataStatistics statistics,
			final AffineTransform2D transform )
	{
		this.data = data;
		this.statistics = statistics;
		this.transform = transform;
	}

	public STDataAssembly( final STData data )
	{
		this( data, new STDataStatistics( data ), new AffineTransform2D() );
	}

	public STData data() { return data; }
	public STDataStatistics statistics() { return statistics; }
	public AffineTransform2D transform() { return transform; }
}
