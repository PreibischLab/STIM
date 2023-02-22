package anndata;

import net.imglib2.util.Pair;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class AnnData {

    protected final Map<String, AnnDataField> fields;
    protected final int numVars;
    protected final int numObs;

    public AnnData(final AnnDataField X, final StringArray varNames) {
        this(X, varNames, null);
    }

    public AnnData(final AnnDataField X, final AnnDataField varNames, final AnnDataField obsNames) {
        this.numVars = X.numColumns;
        this.numObs = X.numRows;
        fields = new HashMap<>();
        fields.put("X", X);
        fields.put("varNames", varNames);
        fields.put("obsNames", obsNames);
        fields.put("layers", new AnnDataGroup(numObs, numVars));
        fields.put("var", new AnnDataGroup(1, numVars));
        fields.put("varm", new AnnDataGroup(0, numVars));
        fields.put("varp", new AnnDataGroup(numVars, numVars));
        fields.put("obs", new AnnDataGroup(numObs, 1));
        fields.put("obsm", new AnnDataGroup(numObs, 0));
        fields.put("obsp", new AnnDataGroup(numObs, numObs));
    }

    public AnnDataField get(String name) {
        return fields.get(name);
    }

    protected static AnnDataEncoding readElementEncoding(N5HDF5Reader reader, String path) throws IOException {
        final String type = reader.getAttribute(path, "encoding-type", String.class);
        final String version = reader.getAttribute(path, "encoding-version", String.class);
        return new AnnDataEncoding(type, version);
    }

    protected static DenseArray readDenseArray(N5HDF5Reader reader, String path) throws IOException {
        final DatasetAttributes attributes = reader.getDatasetAttributes(path);
        final long[] shape = attributes.getDimensions();
        double[] block = (double[]) reader.readBlock(path, attributes, 0, 0).getData();
        return new DenseArray((int) shape[0], (int) shape[1], block);
    }

    public static void main(String[] args) throws IOException {
        String path = "./data/test.h5ad";
        N5HDF5Reader reader = new N5HDF5Reader(path);
        DenseArray field = AnnData.readDenseArray(reader, "/obsm/locations");
        AnnData adata = new AnnData(
                new DenseArray(2, 3, new double[]{1,2,3,4,5,6}),
                new StringArray(1, 3, new String[]{"101", "011", "111"}),
                new CategoricalArray(2, 1, new String[]{"a cell"}, new int[]{0, 0}));

        adata.get("layers").put("test", new DenseArray(2, 3, new double[]{1,1,1,1,1,1}));
        AnnDataField test = adata.get("layers").get("test");


    }
}
