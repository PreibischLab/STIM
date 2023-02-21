package anndata;

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

    public static void main(String[] args) {
        AnnData adata = new AnnData(
                new DenseArray(2, 3, new double[]{1,2,3,4,5,6}),
                new StringArray(1, 3, new String[]{"101", "011", "111"}),
                new CategoricalArray(2, 1, new String[]{"a cell"}, new int[]{0, 0}));

        adata.get("layers").put("test", new DenseArray(2, 3, new double[]{1,1,1,1,1,1}));
        AnnDataField field = adata.get("layers").get("test");
    }
}
