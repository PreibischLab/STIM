package anndata;

import net.imglib2.*;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.util.Util;

public class SparseRandomAccess<
        D extends NativeType<D> & NumericType<D>,
        I extends NativeType<I> & IntegerType<I>>
        extends AbstractLocalizable
        implements RandomAccess<D> {

    protected final AbstractCompressedStorageRai<D, I> rai;
    protected final RandomAccess<D> dataAccess;
    protected final RandomAccess<I> indicesAccess;
    protected final RandomAccess<I> indptrAccess;
    protected final D fillValue;

    public SparseRandomAccess(AbstractCompressedStorageRai<D, I> rai) {
        super(rai.numDimensions());

        this.rai = rai;
        dataAccess = rai.data.randomAccess();
        indicesAccess = rai.indices.randomAccess();
        indptrAccess = rai.indptr.randomAccess();

        fillValue = dataAccess.get().createVariable();
        fillValue.setOne();
    }

    public SparseRandomAccess(SparseRandomAccess<D, I> ra) {
        this(ra.rai);

        for (int d = 0; d < n; ++d) {
            position[d] = ra.getLongPosition(d);
        }
    }

    @Override
    public RandomAccess<D> copyRandomAccess() {
        return new SparseRandomAccess<>(this);
    }

    @Override
    public void fwd(int d) {
        ++position[d];
    }

    @Override
    public void bck(int d) {
        --position[d];
    }

    @Override
    public void move(int distance, int d) {
        position[d] += distance;
    }

    @Override
    public void move(long distance, int d) {
        position[d] += distance;
    }

    @Override
    public void move(Localizable localizable) {
        for (int d = 0; d < n; ++d) {
            position[d] += localizable.getLongPosition(d);
        }
    }

    @Override
    public void move(int[] distance) {
        for (int d = 0; d < n; ++d) {
            position[d] += distance[d];
        }
    }

    @Override
    public void move(long[] distance) {
        for (int d = 0; d < n; ++d) {
            position[d] += distance[d];
        }
    }

    @Override
    public void setPosition(Localizable localizable) {
        for (int d = 0; d < n; ++d) {
            position[d]  = localizable.getLongPosition(d);
        }
    }

    @Override
    public void setPosition(int[] position) {
        for (int d = 0; d < n; ++d) {
            this.position[d] = position[d];
        }
    }

    @Override
    public void setPosition(long[] position) {
        for (int d = 0; d < n; ++d) {
            this.position[d] = position[d];
        }
    }

    @Override
    public void setPosition(int position, int d) {
        this.position[d] = position;
    }

    @Override
    public void setPosition(long position, int d) {
        this.position[d] = position;
    }

    @Override
    public D get() {
    	long ptr=-12122,start=-12122;
    	try
    	{
        // determine range of indices to search
        ptr = rai.targetPointer(position);
        indptrAccess.setPosition(ptr, 0);
        start = indptrAccess.get().getIntegerLong();
        indptrAccess.setPosition(ptr + 1L, 0);
    	}
    	catch (Exception e )
    	{
			System.out.println( "ptr: "  + ptr  );
			System.out.println( "indptr: " + Util.printInterval(rai.indptr) );
			e.printStackTrace();
			System.exit( 0 );
			throw new ArrayIndexOutOfBoundsException();
    	}
//        indptrAccess.fwd(0);
//        final long end = indptrAccess.get().getIntegerLong();

        // todo: make this more efficient, e.g., by bisection
//        indicesAccess.setPosition(start, 0);
//        while (indicesAccess.getLongPosition(0) < end) {
//            if (indicesAccess.get().getIntegerLong() < rai.targetCursor(position)) {
//                indicesAccess.fwd(0);
//            }
//            else if (indicesAccess.get().getIntegerLong() == rai.targetCursor(position)) {
//                dataAccess.setPosition(indicesAccess);
//                return dataAccess.get();
//            }
//            else {
//                break;
//            }
//        }

        return fillValue;
    }

    @Override
    public Sampler<D> copy() {
        return copyRandomAccess();
    }
}
