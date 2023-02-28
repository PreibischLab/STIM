package anndata;

import net.imglib2.img.NativeImg;

public class CscRandomAccessibleInterval extends AbstractCompressedStorageRai{
    public CscRandomAccessibleInterval(long numRows, long numCols, NativeImg data, NativeImg indices, NativeImg indptr) {
        super(numRows, numCols, data, indices, indptr);
    }

    @Override
    protected long targetCursor(long[] position) {
        return position[1];
    }

    @Override
    protected long targetPointer(long[] position) {
        return position[0];
    }
}
