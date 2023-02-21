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

    public abstract BaseType get(final int i, final int j);

    public abstract ArrayType getRow(final int i);

    public abstract ArrayType getColumn(final int j);
}
