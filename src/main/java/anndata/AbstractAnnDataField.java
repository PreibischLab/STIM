package anndata;

public abstract class AbstractAnnDataField {

    protected final int numRows;
    protected final int numColumns;

    public AbstractAnnDataField(final int numRows, final int numColumns) {
        this.numRows = numRows;
        this.numColumns = numColumns;
    }

    public int getNumRows() {
        return this.numRows;
    }

    public int getNumColumns() {
        return this.numColumns;
    }

    public abstract double get(final int i, final int j);

    public abstract double[] getRow(final int i);

    public abstract double[] getColumn(final int j);
}
