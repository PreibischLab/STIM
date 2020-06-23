package imglib2.icp;

import mpicbg.models.Point;
import net.imglib2.RealLocalizable;
import net.imglib2.util.Util;

public class LinkedPoint< P > extends Point implements RealLocalizable
{
	// when identifying as RealLocalizable
	final static boolean useW = true;

	private static final long serialVersionUID = 1L;

	final P link;

	public LinkedPoint( final double[] l, final double[] w, final P link )
	{
		super( l.clone(), w.clone() );

		this.link = link;
	}

	public LinkedPoint( final double[] l, final P link )
	{
		this( l, l, link );
	}

	public P getLinkedObject() { return link; }

	@Override
	public void localize( final float[] position )
	{
		for ( int d = 0; d < l.length; ++d )
			position[ d ] = useW? (float)w[ d ] : (float)l[ d ];
	}

	@Override
	public void localize( final double[] position )
	{
		for ( int d = 0; d < l.length; ++d )
			position[ d ] = useW? w[ d ] : l[ d ];
	}

	@Override
	public float getFloatPosition( final int d ) { return useW? (float)w[ d ] : (float)l[ d ]; }

	@Override
	public double getDoublePosition( final int d ) { return useW? w[ d ] : l[ d ]; }

	@Override
	public int numDimensions() { return l.length; }

	@Override
	public String toString() { return "LinkedPoint l=" + Util.printCoordinates( l ) + ", w=" + Util.printCoordinates( w ); }

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		for ( int d = 0; d < w.length; ++d )
			result *= w[ d ] % prime;
		result = prime * result + ( ( link == null ) ? 0 : link.hashCode() );
		return result;
	}

	@Override
	public boolean equals( Object obj )
	{
		if ( this == obj )
			return true;
		if ( obj == null )
			return false;
		if ( getClass() != obj.getClass() )
			return false;
		LinkedPoint< ? > other = (LinkedPoint< ? >) obj;
		if ( link == null )
		{
			if ( other.link != null )
				return false;
		} else if ( !link.equals( other.link ) )
			return false;
		return true;
	}

}
