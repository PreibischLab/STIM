package anndata;

import java.util.Arrays;

public class CscSparseArray extends CsrSparseArray {
    public CscSparseArray(int numRows, int numColumns, double[] data, int[] colIndex, int[] rowPointer) {
        super(numColumns, numRows, data, colIndex, rowPointer);
    }

    public int getNumRows() {
        return this.numColumns;
    }

    public int getNumColumns() {
        return this.numRows;
    }

    public double get(int i, int j) {
        return super.get(j, i);
    }

    @Override
    public double[] getRow(int i) {
        return super.getColumn(i);
    }

    @Override
    public double[] getColumn(int j) {
        return super.getRow(j);
    }

    public static void main(String[] args) {
        CscSparseArray A = new CscSparseArray(4, 3,
                new double[]{1.0, 2.0, 4.0, 3.0, 5.0, 6.0},
                new int[]{0, 0, 1, 0, 1, 2},
                new int[]{0, 1, 3, 6});

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
