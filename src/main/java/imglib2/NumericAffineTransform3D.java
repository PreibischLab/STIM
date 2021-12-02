package imglib2;

import net.imglib2.img.NativeImg;
import net.imglib2.img.basictypeaccess.DoubleAccess;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Index;
import net.imglib2.type.NativeType;
import net.imglib2.type.NativeTypeFactory;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.util.Fraction;

public class NumericAffineTransform3D implements NumericType< NumericAffineTransform3D >, NativeType< NumericAffineTransform3D >
{
	private int i = 0;
	private int baseIndex = 0; // i * 12;

	final protected NativeImg< ?, ? extends DoubleAccess > img;

	// the DataAccess that holds the information
	protected DoubleAccess dataAccess;

	// only set when reading
	final AffineTransform3D transformTmp = new AffineTransform3D();

	//final double[] data1 = new double[ 12 ];
	//final double[] data2 = new double[ 12 ];

	// this is the constructor if you want it to read from an array
	public NumericAffineTransform3D( final NativeImg< ?, ? extends DoubleAccess > modelDoubleStorage )
	{
		img = modelDoubleStorage;
	}

	public NumericAffineTransform3D( final NumericAffineTransform3D model )
	{
		img = null;
		dataAccess = new DoubleArray( 12 );
		set( model );
	}

	public NumericAffineTransform3D( final AffineTransform3D model )
	{
		img = null;
		dataAccess = new DoubleArray( 12 );
		setAffineModel( model );
	}

	public NumericAffineTransform3D()
	{
		this( new AffineTransform3D() );
	}

	protected void updateTransformTmp()
	{
		// biggest different to mpi-cbg models, different ordering
		transformTmp.set(
				getAtBase( 0 ), getAtBase( 1 ), getAtBase( 2 ), getAtBase( 3 ),
				getAtBase( 4 ), getAtBase( 5 ), getAtBase( 6 ), getAtBase( 7 ),
				getAtBase( 8 ), getAtBase( 9 ), getAtBase( 10 ), getAtBase( 11 ) );
	}

	final protected double getAtBase( final int j )
	{
		return dataAccess.getValue( baseIndex + j );
	}

	final protected void setAtBase( final int j, final double value )
	{
		dataAccess.setValue( baseIndex + j, value );
	}

	public void setAffineModel( final AffineTransform3D model )
	{
		final double[] tmp = new double[ 12 ];
		model.toArray( tmp );

		for ( int j = 0; j < 12; ++j )
			setAtBase( j, tmp[ j ] );
	}

	public AffineTransform3D getTransform()
	{
		updateTransformTmp();
		return transformTmp;
	}

	@Override
	public NumericAffineTransform3D createVariable()
	{
		return new NumericAffineTransform3D( new AffineTransform3D() );
	}

	@Override
	public NumericAffineTransform3D copy()
	{
		return new NumericAffineTransform3D( this );
	}

	@Override
	public void set( final NumericAffineTransform3D c )
	{
		for ( int j = 0; j < 12; ++j )
			setAtBase( j, c.getAtBase( j ) );
	}

	@Override
	public boolean valueEquals( final NumericAffineTransform3D c )
	{
		for ( int j = 0; j < 12; ++j )
			if ( getAtBase( j ) != c.getAtBase( j ) )
				return false;

		return true;
	}

	@Override
	public void add( final NumericAffineTransform3D c )
	{
		for ( int j = 0; j < 12; ++j )
			setAtBase( j, getAtBase( j ) + c.getAtBase( j ) );
	}

	@Override
	public void mul( final NumericAffineTransform3D c )
	{
		for ( int j = 0; j < 12; ++j )
			setAtBase( j, getAtBase( j ) * c.getAtBase( j ) );
	}

	@Override
	public void sub( final NumericAffineTransform3D c )
	{
		for ( int j = 0; j < 12; ++j )
			setAtBase( j, getAtBase( j ) - c.getAtBase( j ) );
	}

	@Override
	public void div( final NumericAffineTransform3D c )
	{
		for ( int j = 0; j < 12; ++j )
			setAtBase( j, getAtBase( j ) / c.getAtBase( j ) );
	}

	@Override
	public void setOne()
	{
		setAffineModel( new AffineTransform3D() );
	}

	@Override
	public void setZero()
	{
		for ( int j = 0; j < 12; ++j )
			setAtBase( j, 0.0 );
	}

	@Override
	public void mul( final float c )
	{
		for ( int j = 0; j < 12; ++j )
			setAtBase( j, getAtBase( j ) * c );
	}

	@Override
	public void mul( final double c )
	{
		for ( int j = 0; j < 12; ++j )
			setAtBase( j, getAtBase( j ) * c );
	}

	@Override
	public Fraction getEntitiesPerPixel()
	{
		return new Fraction( 12, 1 );
	}

	@Override
	public NumericAffineTransform3D duplicateTypeOnSameNativeImg()
	{
		return new NumericAffineTransform3D( img );
	}

	private static final NativeTypeFactory< NumericAffineTransform3D, DoubleAccess > typeFactory = NativeTypeFactory.DOUBLE( img -> new NumericAffineTransform3D( img ) );

	@Override
	public NativeTypeFactory< NumericAffineTransform3D, DoubleAccess > getNativeTypeFactory()
	{
		return typeFactory;
	}

	@Override
	public void updateContainer( final Object c )
	{
		dataAccess = img.update( c );
	}

	@Override
	public void updateIndex( final int i )
	{
		this.i = i;
		this.baseIndex = i * 12;
	}

	@Override
	public int getIndex()
	{
		return i;
	}

	@Override
	public void incIndex()
	{
		++i;
		baseIndex += 12;
	}

	@Override
	public void incIndex( final int increment )
	{
		i += increment;
		baseIndex += 12*increment;
	}

	@Override
	public void decIndex()
	{
		--i;
		baseIndex -= 12;
	}

	@Override
	public void decIndex( int decrement )
	{
		i -= decrement;
		baseIndex -= 12*decrement;
	}

	@Override
	public void pow( final NumericAffineTransform3D c )
	{
		for ( int j = 0; j < 12; ++j )
			setAtBase( j, Math.pow( getAtBase( j ), c.getAtBase( j ) ) );
	}

	@Override
	public void pow( final double c)
	{
		for ( int j = 0; j < 12; ++j )
			setAtBase( j, Math.pow( getAtBase( j ), c ) );
	}

	@Override
	public Index index() {
		// TODO Auto-generated method stub
		return null;
	}
}
