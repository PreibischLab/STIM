package anndata;

import net.imglib2.img.NativeImg;

public class CsrRandomAccessibleInterval extends AbstractCompressedStorageRai{
    public CsrRandomAccessibleInterval(long numRows, long numCols, NativeImg data, NativeImg indices, NativeImg indptr) {
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
