package anndata;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.NativeImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;

public class AbstractCompressedStorageRai<
        DataType extends NativeType<DataType> & NumericType<DataType>,
        IndexType extends NativeType<IndexType> & IntegerType<IndexType>>
        implements RandomAccessibleInterval<DataType> {

    protected final long[] max;
    protected final NativeImg<DataType, ?> data;
    protected final NativeImg<IndexType, ?> indices;
    protected final NativeImg<IndexType, ?> indptr;

    public AbstractCompressedStorageRai(
            long numRows,
            long numCols, NativeImg<DataType, ?> data,
            NativeImg<IndexType, ?> indices,
            NativeImg<IndexType, ?> indptr) {
        this.data = data;
        this.indices = indices;
        this.indptr = indptr;
        this.max = new long[]{numRows-1, numCols-1};
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
    public RandomAccess<DataType> randomAccess() {
        return new CsrRandomAccess<DataType, IndexType>(this);
    }

    @Override
    public RandomAccess<DataType> randomAccess(Interval interval) {
        return randomAccess();
    }
}
