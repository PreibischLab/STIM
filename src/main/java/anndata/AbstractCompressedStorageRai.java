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

        if (data.numDimensions() != 1 || indices.numDimensions() != 1 || indptr.numDimensions() != 1)
            throw new IllegalArgumentException("Data, index, and indptr RandomAccessibleInterval must be one dimensional.");
        if (data.min(0) != 0 || indices.min(0) != 0 || indptr.min(0) != 0)
            throw new IllegalArgumentException("Data, index, and indptr arrays must start from 0.");
        if (data.max(0) != indices.max(0))
            throw new IllegalArgumentException("Data and index array must be of the same size.");
        if (indptr.max(0) != ptr(max)+1)
            throw new IllegalArgumentException("Indptr array does not fit number of slices.");
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

    /**
     * Returns the index of the continuous dimension.
     *
     * @param position
     * @return entry of position corresponds to continuous dimension
     */
    abstract protected long ind(long[] position);

    /**
     * Returns the index of the non-continuous dimension.
     *
     * @param position
     * @return entry of position corresponds to non-continuous dimension
     */
    abstract protected long ptr(long[] position);

}
