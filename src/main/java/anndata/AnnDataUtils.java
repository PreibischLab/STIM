package anndata;

import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.img.Img;
import net.imglib2.img.NativeImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.view.Views;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class AnnDataUtils {

    public static RandomAccessibleInterval readData(N5Reader reader, String path) throws IOException {
        final AnnDataFieldType type = getFieldType(reader, path);

        switch (type) {
            case DENSE_ARRAY:
                return N5Utils.open(reader, path);
            case CSR_MATRIX:
                return openCsrArray(reader, path); // row
            case CSC_MATRIX:
                return openCscArray(reader, path); // column
            default:
                throw new IOException("Reading data for " + type.toString() + " not supported.");
        }
    }

    public static AnnDataFieldType getFieldType(N5Reader reader, String path) throws IOException {
        final String encoding = reader.getAttribute(path, "encoding-type", String.class);
        final String version = reader.getAttribute(path, "encoding-version", String.class);
        return AnnDataFieldType.fromString(encoding, version);
    }

    protected static <T extends NativeType<T> & NumericType<T>> CsrRandomAccessibleInterval openCsrArray(N5Reader reader, String path) throws IOException {
        final CachedCellImg<T, ?> sparseData = N5Utils.open(reader, path + "/data");
        final CachedCellImg<?, ?> indices = N5Utils.open(reader, path + "/indices");
        final CachedCellImg<?, ?> indptr = N5Utils.open(reader, path + "/indptr");

        final long[] shape = reader.getAttribute("/X", "shape", long[].class);
        return new CsrRandomAccessibleInterval(shape[1], shape[0], sparseData, indices, indptr);
    }

    protected static <T extends NativeType<T> & NumericType<T>> CscRandomAccessibleInterval openCscArray(N5Reader reader, String path) throws IOException {
        final CachedCellImg<T, ?> sparseData = N5Utils.open(reader, path + "/data");
        final CachedCellImg<?, ?> indices = N5Utils.open(reader, path + "/indices");
        final CachedCellImg<?, ?> indptr = N5Utils.open(reader, path + "/indptr");

        final long[] shape = reader.getAttribute("/X", "shape", long[].class);
        return new CscRandomAccessibleInterval(shape[1], shape[0], sparseData, indices, indptr);
    }

    public static List<String> readAnnotation(N5Reader reader, String path) throws IOException {
        final AnnDataFieldType type = getFieldType(reader, path);

        switch (type) {
            case STRING_ARRAY:
                return readStringList(reader, path);
            case CATEGORICAL_ARRAY:
                return readCategoricalList(reader, path);
            default:
                throw new IOException("Reading annotations for " + type + " not supported.");
        }
    }

    protected static List<String> readStringList(N5Reader reader, String path) {
        final String[] array = readPrimitiveStringArray((N5HDF5Reader) reader, path);
        return Arrays.asList(array);
    }

    protected static String[] readPrimitiveStringArray(N5HDF5Reader reader, String path) {
        final IHDF5Reader hdf5Reader = HDF5Factory.openForReading(reader.getFilename());
        return hdf5Reader.readStringArray(path);
    }

    protected static List<String> readCategoricalList(N5Reader reader, String path) throws IOException {
        final String[] categoryNames = readPrimitiveStringArray((N5HDF5Reader) reader, path + "/categories");
        final Img<?> category = N5Utils.open(reader, path + "/codes");
        final RandomAccess<? extends IntegerType> ra = (RandomAccess<? extends IntegerType>) category.randomAccess();

        // assume that minimal index = 0
        final int max = (int) category.max(0);
        final String[] names = new String[max];
        for (int i = 0; i < max; ++i) {
            names[i] = categoryNames[ra.setPositionAndGet(i).getInteger()];
        }

        return Arrays.asList(names);
    }


    public enum AnnDataFieldType {

        ANNDATA("anndata", "0.1.0"),
        DENSE_ARRAY("array", "0.2.0"),
        CSR_MATRIX("csr_matrix", "0.1.0"),
        CSC_MATRIX("csc_matrix", "0.1.0"),
        DATA_FRAME("dataframe", "0.2.0"),
        MAPPING("dict", "0.1.0"),
        NUMERIC_SCALAR("numeric-scalar", "0.2.0"),
        STRING_SCALAR("string", "0.2.0"),
        CATEGORICAL_ARRAY("categorical", "0.2.0"),
        STRING_ARRAY("string-array", "0.2.0"),
        NULLABLE_INTEGER("nullable-integer", "0.1.0"),
        NULLABLE_BOOL("nullable-bool", "0.1.0");

        private final String encoding;
        private final String version;

        AnnDataFieldType(String encoding, String version) {
            this.encoding = encoding;
            this.version = version;
        }

        public String toString() {
            return "encoding: " + encoding + ", version: " + version;
        }

        public static AnnDataFieldType fromString(String encoding, String version) {
            for (AnnDataFieldType type : values())
                if (type.encoding.equals(encoding) && type.version.equals(version))
                    return type;
            throw new IllegalArgumentException("No known anndata field with encoding \"" + encoding + "\" and version \"" + version + "\"");
        }
    }
}
