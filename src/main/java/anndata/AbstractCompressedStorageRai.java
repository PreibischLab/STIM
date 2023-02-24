package anndata;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.NativeImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.LongType;

import java.util.Iterator;

public class AbstractCompressedStorageRai<T extends NativeType<T>> implements RandomAccessibleInterval<T> {

    protected final long[] max;
    protected final NativeImg<T, ?> data;
    protected final NativeImg<LongType, ?> indices;
    protected final NativeImg<LongType, ?> indptr;

    public AbstractCompressedStorageRai(
            long numRows,
            long numCols, NativeImg<T, ?> data,
            NativeImg<LongType, ?> indices,
            NativeImg<LongType, ?> indptr) {
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
    public RandomAccess<T> randomAccess() {
        return new CsrRandomAccess<T>(this);
    }

    @Override
    public RandomAccess<T> randomAccess(Interval interval) {
        return randomAccess();
    }
}
