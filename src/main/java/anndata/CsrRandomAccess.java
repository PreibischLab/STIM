package anndata;

import net.imglib2.*;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.LongType;

public class CsrRandomAccess<T extends NativeType<T>>
        extends AbstractLocalizable
        implements RandomAccess<T> {

    protected final AbstractCompressedStorageRai<T> rai;
    protected final RandomAccess<T> dataAccess;
    protected final RandomAccess<LongType> indicesAccess;
    protected final RandomAccess<LongType> indptrAccess;

    public CsrRandomAccess(AbstractCompressedStorageRai<T> rai) {
        super(rai.numDimensions());

        this.rai = rai;
        this.dataAccess = rai.data.randomAccess();
        this.indicesAccess = rai.indices.randomAccess();
        this.indptrAccess = rai.indptr.randomAccess();
    }

    public CsrRandomAccess(CsrRandomAccess<T> ra) {
        super(ra.numDimensions());

        this.rai = ra.rai;
        this.dataAccess = rai.data.randomAccess();
        this.indicesAccess = rai.indices.randomAccess();
        this.indptrAccess = rai.indptr.randomAccess();

        for (int d = 0; d < n; ++d) {
            setPosition(ra.getLongPosition(d), d);
        }
    }

    @Override
    public RandomAccess<T> copyRandomAccess() {
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
    public T get() {
        dataAccess.setPosition(0, 0);
        return dataAccess.get();
    }

    @Override
    public Sampler<T> copy() {
        return copyRandomAccess();
    }
}
