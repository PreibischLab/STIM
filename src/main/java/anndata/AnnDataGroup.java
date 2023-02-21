package anndata;

import java.util.HashMap;

public class AnnDataGroup extends AnnDataField<AnnDataField, AnnDataField[]> {

    final protected HashMap<String, AnnDataField> dataMap;
    protected AnnDataGroup(int rowRestriction, int columnRestriction) {
        super(rowRestriction, columnRestriction, null);
        dataMap = new HashMap<String, AnnDataField>();
    }

    public AnnDataField get(final String name) {
        return dataMap.get(name);
    }

    public void put(final String name, final AnnDataField field) {
        if ((numRows != 0 && field.numRows != numRows)
                | (numColumns != 0 && field.numColumns != numColumns))
            throw new IllegalArgumentException("Field size not compatible with group.");
        dataMap.put(name, field);
    }

    public AnnDataField remove(final String name) {
        return dataMap.remove(name);
    }

    @Override
    public AnnDataField get(int i, int j) {
        throw new UnsupportedOperationException("Cannot get member of group by indices.");
    }

    @Override
    public AnnDataField[] getRow(int i) {
        throw new UnsupportedOperationException("Cannot get members of group by indices.");
    }

    @Override
    public AnnDataField[] getColumn(int j) {
        throw new UnsupportedOperationException("Cannot get members of group by indices.");
    }
}
