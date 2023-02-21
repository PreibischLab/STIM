package anndata;

public abstract class AnnDataField<BaseType, ArrayType>{

    protected final int numRows;
    protected final int numColumns;
    protected final ArrayType data;

    protected AnnDataField(final int numRows, final int numColumns, final ArrayType data) {
        this.numRows = numRows;
        this.numColumns = numColumns;
        this.data = data;
    }

    public int getNumRows() {
        return this.numRows;
    }

    public int getNumColumns() {
        return this.numColumns;
    }

    public AnnDataField<?,?> get(String name) {
        throw new UnsupportedOperationException("Retrieving values by name not allowed for non-groups.");
    }

    public void put(String name, AnnDataField<?,?> field) {
        throw new UnsupportedOperationException("Adding values not allowed for non-groups.");
    }

    public AnnDataField<?,?> remove(String name) {
        throw new UnsupportedOperationException("Removing values not allowed for non-groups.");
    }

    public abstract BaseType get(final int i, final int j);

    public abstract ArrayType getRow(final int i);

    public abstract ArrayType getColumn(final int j);
}
