package anndata;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;

abstract public class AbstractCompressedStorageRai<
        D extends NativeType<D> & NumericType<D>,
        I extends NativeType<I> & IntegerType<I>>
        implements RandomAccessibleInterval<D> {

    protected final long[] max;
    protected final RandomAccessibleInterval<D> data;
    protected final RandomAccessibleInterval<I> indices;
    protected final RandomAccessibleInterval<I> indptr;

    public AbstractCompressedStorageRai(
            long numCols,
            long numRows,
            RandomAccessibleInterval<D> data,
            RandomAccessibleInterval<I> indices,
            RandomAccessibleInterval<I> indptr) {
        this.data = data;
        this.indices = indices;
        this.indptr = indptr;
        this.max = new long[]{numCols-1, numRows-1};
    }

    @Override
    public long min(int d) {
        return 0L;
    }

    @Override
    public long max(int d) {
        return max[d];
    }

    @Override
    public int numDimensions() {
        return 2;
    }

    @Override
    public RandomAccess<D> randomAccess() {
        return new SparseRandomAccess<D, I>(this);
    }

    @Override
    public RandomAccess<D> randomAccess(Interval interval) {
        return randomAccess();
    }

    abstract protected long targetCursor(long[] position);

    abstract protected long targetPointer(long[] position);

}
