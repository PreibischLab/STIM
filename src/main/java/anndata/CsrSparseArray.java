package anndata;

import java.util.Arrays;

public class CsrSparseArray extends AbstractAnnDataField {

    protected final double[] data;
    protected final int[] colIndex;
    protected final int[] rowPointer;

    public CsrSparseArray(int numRows, int numColumns, double[] data, int[] colIndex, int[] rowPointer) {
        super(numRows, numColumns);
        this.data = data;
        this.colIndex = colIndex;
        this.rowPointer = rowPointer;

        if (rowPointer.length != numRows+1 || data.length != colIndex.length)
            throw new IllegalArgumentException("Data must be valid for compressed sparse format.");
    }

    @Override
    public double get(int i, int j) {
        double element = 0.0;
        for (int k=rowPointer[i]; k<rowPointer[i+1]; ++k) {
            if (colIndex[k] == j) {
                element = data[k];
                break;
            }
        }
        return element;
    }

    @Override
    public double[] getRow(int i) {
        double[] row = new double[numColumns];
        for (int k=rowPointer[i]; k<rowPointer[i+1]; ++k) {
            row[colIndex[k]] = data[k];
        }
        return row;
    }

    @Override
    public double[] getColumn(int j) {
        double[] column = new double[numRows];
        for (int i=0; i<numRows; ++i) {
            for (int k = rowPointer[i]; k<rowPointer[i+1]; ++k) {
                if (colIndex[k] == j) {
                    column[i] = data[k];
                }
            }
        }
        return column;
    }

    public static void main(String[] args) {
        CsrSparseArray A = new CsrSparseArray(4, 3,
                new double[]{1.0, 2.0, 3.0, 4.0, 5.0, 6.0},
                new int[]{0, 1, 2, 1, 2, 2},
                new int[]{0, 3, 5, 6, 6});

        System.out.println("A(0,1) = " + A.get(0,1));

        for (int i=0; i<4; ++i) {
            System.out.print("Row " + i + ": ");
            Arrays.stream(A.getRow(i)).forEach(x -> System.out.print(x + " "));
            System.out.println();
        }

        System.out.print("Column 1: ");
        Arrays.stream(A.getColumn(1)).forEach(x -> System.out.print(x + " "));
        System.out.println();
    }
}
