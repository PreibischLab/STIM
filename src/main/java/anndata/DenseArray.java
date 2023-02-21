package anndata;

import java.util.Arrays;
import java.util.stream.IntStream;

public class DenseArray extends AbstractAnnDataField<Double, double[]> {

    public DenseArray(final int numRows, final int numColumns, final double[] data) {
        super(numRows, numColumns, data);

        if (data.length != numRows*numColumns)
            throw new IllegalArgumentException("Dense array data has to match the array's size.");
    }

    protected int linearIndex(final int i, final int j) {
        return i*numColumns + j;
    }

    @Override
    public Double get(final int i, final int j) {
        return data[linearIndex(i,j)];
    }

    @Override
    public double[] getRow(final int i) {
        return Arrays.copyOfRange(data, linearIndex(i,0), linearIndex(i,numColumns));
    }

    @Override
    public double[] getColumn(final int j) {
        return  IntStream.range(0, numRows).mapToDouble(i -> data[linearIndex(i,j)]).toArray();
    }

    public static void main(String[] args) {
        DenseArray A = new DenseArray(2, 3, new double[]{1.0, 2.0, 3.0, 4.0, 5.0, 6.0});
        System.out.println("A(0,1) = " + A.get(0,1));

        System.out.print("Row 1: ");
        Arrays.stream(A.getRow(1)).forEach(x -> System.out.print(x + " "));
        System.out.println();

        System.out.print("Column 1: ");
        Arrays.stream(A.getColumn(1)).forEach(x -> System.out.print(x + " "));
        System.out.println();
    }
}
