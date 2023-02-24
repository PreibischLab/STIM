package anndata;

import net.imglib2.*;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;

public class CsrRandomAccess<
        DataType extends NativeType<DataType> & NumericType<DataType>,
        IndexType extends NativeType<IndexType> & IntegerType<IndexType>>
        extends AbstractLocalizable
        implements RandomAccess<DataType> {

    protected final AbstractCompressedStorageRai<DataType, IndexType> rai;
    protected final RandomAccess<DataType> dataAccess;
    protected final RandomAccess<IndexType> indicesAccess;
    protected final RandomAccess<IndexType> indptrAccess;
    protected final DataType fillValue;
    protected final DataType valueToReturn;

    public CsrRandomAccess(AbstractCompressedStorageRai<DataType, IndexType> rai) {
        super(rai.numDimensions());

        this.rai = rai;
        dataAccess = rai.data.randomAccess();
        indicesAccess = rai.indices.randomAccess();
        indptrAccess = rai.indptr.randomAccess();

        valueToReturn = dataAccess.get().createVariable();
        fillValue = valueToReturn.createVariable();
        fillValue.setZero();
    }

    public CsrRandomAccess(CsrRandomAccess<DataType, IndexType> ra) {
        this(ra.rai);

        for (int d = 0; d < n; ++d) {
            setPosition(ra.getLongPosition(d), d);
        }
    }



    @Override
    public RandomAccess<DataType> copyRandomAccess() {
        return new CsrRandomAccess<>(this);
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
            final int distance = localizable.getIntPosition(d);
            position[d] += distance;
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
            position[d]  = localizable.getIntPosition(d);
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
    public DataType get() {
        return fillValue;
    }

    @Override
    public Sampler<DataType> copy() {
        return copyRandomAccess();
    }
}
