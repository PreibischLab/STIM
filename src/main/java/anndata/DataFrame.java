package anndata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.IntStream;

public class DataFrame extends DenseArray {

    protected ArrayList<String> headings;
    protected ArrayList<String> index;

    public DataFrame(int numRows, int numColumns, double[] data) {
        this(numRows, numColumns, data, (String[]) IntStream.range(0, numColumns)
                .mapToObj(i -> ("Column " + i))
                .toArray(String[]::new));
    }

    public DataFrame(int numRows, int numColumns, double[] data, String[] headings) {
        this(numRows, numColumns, data, headings, (String[]) IntStream.range(0, numRows)
                .mapToObj(i -> ("Row " + i))
                .toArray(String[]::new));
    }

    public DataFrame(int numRows, int numColumns, double[] data, String[] headings, String[] index) {
        super(numRows, numColumns, data);
        this.headings = new ArrayList<>(Arrays.asList(headings));
        this.index = new ArrayList<>(Arrays.asList(index));
    }

    public double[] getRow(final String key) {
        final int i = getRowIndexOf(key);
        return getRow(i);
    }

    public int getRowIndexOf(final String key) {
        return index.indexOf(key);
    }

    public double[] getColumn(final String key) {
        final int j = getColumnIndexOf(key);
        return getColumn(j);
    }

    public int getColumnIndexOf(final String key) {
        return headings.indexOf(key);
    }

    public static void main(String[] args) {
        DataFrame A = new DataFrame(2, 3, new double[]{1.0, 2.0, 3.0, 4.0, 5.0, 6.0}, new String[]{"A", "B", "C"});
        final int i = A.getRowIndexOf("Row 0");
        final int j = A.getColumnIndexOf("B");
        System.out.println("A(0,1) = " + A.get(i,j));

        System.out.print("Row 1: ");
        Arrays.stream(A.getRow(1)).forEach(x -> System.out.print(x + " "));
        System.out.println();

        System.out.print("Column 1: ");
        Arrays.stream(A.getColumn(1)).forEach(x -> System.out.print(x + " "));
        System.out.println();

        System.out.print("Column C: ");
        Arrays.stream(A.getColumn("C")).forEach(x -> System.out.print(x + " "));
        System.out.println();
    }
}
