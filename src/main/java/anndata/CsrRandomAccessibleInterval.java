package anndata;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;

public class CsrRandomAccessibleInterval <
		D extends NativeType<D> & NumericType<D>,
		I extends NativeType<I> & IntegerType<I>> extends AbstractCompressedStorageRai<D,I>
{
    public CsrRandomAccessibleInterval(
    		final long numRows,
    		final long numCols,
    		final RandomAccessibleInterval<D> data,
    		final RandomAccessibleInterval<I> indices,
    		final RandomAccessibleInterval<I> indptr)
    {
        super(numRows, numCols, data, indices, indptr);
    }

    @Override
    protected long targetCursor(long[] position) {
        return position[0];
    }

    @Override
    protected long targetPointer(long[] position) {
        return position[1];
    }
}
