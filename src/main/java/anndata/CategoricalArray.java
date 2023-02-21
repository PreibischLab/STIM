package anndata;

import java.util.Arrays;
import java.util.stream.IntStream;

public class CategoricalArray extends AbstractAnnDataField<String, String[]> {

    protected final int[] indices;

    protected CategoricalArray(int numRows, int numColumns, String[] data, int[] indices) {
        super(numRows, numColumns, data);
        this.indices = indices;

        if ((numRows != 1 && numColumns != 1) || (numRows != indices.length && numColumns != indices.length))
            throw new IllegalArgumentException("Categorical arrays can only be one dimensional");
    }

    @Override
    public String get(final int i, final int j) {
        if (i != 0 && j != 0)
            throw new IllegalArgumentException("Categorical arrays can only be one dimensional");

        return this.get(i+j);
    }

    public String get(final int i) {
        return data[indices[i]];
    }

    @Override
    public String[] getRow(final int i) {
        if (numRows == 1)
            return IntStream.of(indices).mapToObj(k -> data[k]).toArray(String[]::new);
        else
            return new String[]{get(i)};
    }

    @Override
    public String[] getColumn(final int j) {
        if (numColumns == 1)
            return IntStream.of(indices).mapToObj(k -> data[k]).toArray(String[]::new);
        else
            return new String[]{get(j)};
    }

    public static void main(String[] args) {
        CategoricalArray A = new CategoricalArray(1, 5, new String[]{"A", "B", "ð"}, new int[]{2, 1, 1, 0, 2});
        System.out.println("A(0,1) = " + A.get(0,1));

        System.out.print("Row 0: ");
        Arrays.stream(A.getRow(0)).forEach(x -> System.out.print(x + " "));
        System.out.println();

        System.out.print("Column 3: ");
        Arrays.stream(A.getColumn(3)).forEach(x -> System.out.print(x + " "));
        System.out.println();
    }
}
