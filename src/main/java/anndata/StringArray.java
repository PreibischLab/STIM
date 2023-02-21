package anndata;

import java.util.Arrays;

public class StringArray extends AbstractAnnDataField<String, String[]> {

    public StringArray(final int numRows, final int numColumns, final String[] data) {
        super(numRows, numColumns, data);

        if (numRows != 1 && numColumns != 1)
            throw new IllegalArgumentException("String arrays can only be one dimensional");
    }

    @Override
    public String get(final int i, final int j) {
        if (i != 0 && j != 0)
            throw new IllegalArgumentException("String arrays can only be one dimensional");

        return this.get(i+j);
    }

    public String get(final int i) {
        return data[i];
    }

    @Override
    public String[] getRow(final int i) {
        if (numRows == 1)
            return Arrays.copyOf(data, data.length);
        else
            return new String[]{get(i)};
    }

    @Override
    public String[] getColumn(final int j) {
        if (numColumns == 1)
            return Arrays.copyOf(data, data.length);
        else
            return new String[]{get(j)};
    }

    public static void main(String[] args) {
        StringArray A = new StringArray(1, 4, new String[]{"", "A", "B", ":-þ"});
        System.out.println("A(0,1) = " + A.get(0,1));

        System.out.print("Row 0: ");
        Arrays.stream(A.getRow(0)).forEach(x -> System.out.print(x + " "));
        System.out.println();

        System.out.print("Column 3: ");
        Arrays.stream(A.getColumn(3)).forEach(x -> System.out.print(x + " "));
        System.out.println();
    }
}
