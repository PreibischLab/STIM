package anndata;

import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;

import java.io.IOException;
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

    protected static String[] readPrimitiveStringArray(N5HDF5Reader reader, String path) {
        final IHDF5Reader hdf5Reader = HDF5Factory.openForReading(reader.getFilename());
        return hdf5Reader.readStringArray(path);
    }

    protected static StringArray readStringArray(N5HDF5Reader reader, String path) {
        return readStringArray(reader, path, false);
    }

    protected static StringArray readStringArray(N5HDF5Reader reader, String path, boolean asRow) {
        String[] block = readPrimitiveStringArray(reader, path);
        return asRow ? new StringArray(1, block.length, block) : new StringArray(block.length, 1, block);
    }

    public static void main(String[] args) throws IOException {
        String path = "./data/test.h5ad";
        N5HDF5Reader reader = new N5HDF5Reader(path);

        DenseArray field = AnnData.readDenseArray(reader, "/obsm/locations");
        StringArray varNames = AnnData.readStringArray(reader, "/var/_index", true);
        StringArray obsNames = AnnData.readStringArray(reader, "/obs/_index");

        AnnData adata = new AnnData(field, varNames, obsNames);
    }
}
