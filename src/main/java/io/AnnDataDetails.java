package io;

import anndata.CompressedStorageRai;
import anndata.CscRandomAccessibleInterval;
import anndata.CsrRandomAccessibleInterval;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ch.systemsx.cisd.hdf5.IHDF5StringWriter;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import io.SpatialDataIO.N5Options;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;


class AnnDataDetails {

    public static <T extends NativeType<T> & RealType<T>, I extends NativeType<I> & IntegerType<I>> RandomAccessibleInterval<T> readArray(N5Reader reader, String path) throws IOException {
        final AnnDataFieldType type = getFieldType(reader, path);
        switch (type) {
            case MISSING:
                System.out.println("Array is missing metadata. Assuming dense array.");
                return N5Utils.open(reader, path);
            case DENSE_ARRAY:
                return N5Utils.open(reader, path);
            case CSR_MATRIX:
                return openSparseArray(reader, path, CsrRandomAccessibleInterval<T,I>::new); // row
            case CSC_MATRIX:
                return openSparseArray(reader, path, CscRandomAccessibleInterval<T,I>::new); // column
            default:
                throw new UnsupportedOperationException("Reading data for " + type + " not supported.");
        }
    }

    public static AnnDataFieldType getFieldType(N5Reader reader, String path) throws IOException {
        final String encoding = reader.getAttribute(path, "encoding-type", String.class);
        final String version = reader.getAttribute(path, "encoding-version", String.class);
        return AnnDataFieldType.fromString(encoding, version);
    }

    protected static <D extends NativeType<D> & RealType<D>, I extends NativeType<I> & IntegerType<I>> CompressedStorageRai<D, I> openSparseArray(
            N5Reader reader,
            String path,
            SparseArrayConstructor<D, I> constructor) throws IOException {

        final RandomAccessibleInterval<D> sparseData = N5Utils.open(reader, path + "/data");
        final RandomAccessibleInterval<I> indices = N5Utils.open(reader, path + "/indices");
        final RandomAccessibleInterval<I> indptr = N5Utils.open(reader, path + "/indptr");

        final long[] shape = reader.getAttribute(path, "shape", long[].class);
        return constructor.apply(shape[1], shape[0], sparseData, indices, indptr);
    }

    public static List<String> readStringAnnotation(N5Reader reader, String path) throws IOException {
        final AnnDataFieldType type = getFieldType(reader, path);
        switch (type) {
            case STRING_ARRAY:
                return readStringList(reader, path);
            case CATEGORICAL_ARRAY:
                return readCategoricalList(reader, path);
            default:
                throw new UnsupportedOperationException("Reading string annotations for " + type + " not supported.");
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

    public static <T extends NativeType<T> & RealType<T>> RandomAccessibleInterval<T> readFromDataFrame(N5Reader reader, String dataFrame, String label) throws IOException {
        List<String> existingData = getExistingDataFrameDatasets(reader, dataFrame);
        if (!existingData.contains(label))
            throw new IOException("Dataframe '" + dataFrame + "' does not contain '" + label + "'.");

        // TODO: this returns null for non-readble datatypes (e.g. string); is there a better way to treat string annotations?
        DatasetAttributes attributes = reader.getDatasetAttributes(dataFrame + "/" + label);
        if (attributes == null || attributes.getDataType() == null)
            return null;

        return N5Utils.open(reader, dataFrame + "/" + label);
    }

    public static List<String> getExistingDataFrameDatasets(N5Reader reader, String dataFrame) throws IOException {
        if (!reader.exists(dataFrame))
            return new ArrayList<>();

        String[] rawArray = reader.getAttribute(dataFrame, "column-order", String[].class);
        rawArray = (rawArray == null) ? reader.list(dataFrame) : rawArray;

        if (rawArray == null || rawArray.length == 0)
            return new ArrayList<>();

        List<String> datasets = new ArrayList<>(Arrays.asList(rawArray));
        datasets.remove("_index");
        return datasets;
    }

    protected static List<String> readCategoricalList(N5Reader reader, String path) throws IOException {
        final String[] categoryNames = readPrimitiveStringArray((N5HDF5Reader) reader, path + "/categories");
        final Img<? extends IntegerType<?>> category = N5Utils.open(reader, path + "/codes");
        final RandomAccess<? extends IntegerType<?>> ra = category.randomAccess();

        // assume that minimal index = 0
        final int max = (int) category.max(0);
        final String[] names = new String[max];
        for (int i = 0; i < max; ++i) {
            names[i] = categoryNames[ra.setPositionAndGet(i).getInteger()];
        }

        return Arrays.asList(names);
    }

    public static boolean isValidAnnData(N5Reader n5) {
        try {
            return getFieldType(n5, "/").toString().equals(AnnDataFieldType.ANNDATA.toString());
        }
        catch (Exception e) {
            return false;
        }
    }

    public static void writeEncoding(N5Writer writer, String path, AnnDataFieldType type) throws IOException {
        writer.setAttribute(path, "encoding-type", type.encoding);
        writer.setAttribute(path, "encoding-version", type.version);
    }

    public static <T extends NativeType<T> & RealType<T>> void writeArray(
            N5Writer writer,
            String path,
            RandomAccessibleInterval<T> data,
            N5Options options) throws IOException {

        AnnDataFieldType type = AnnDataFieldType.DENSE_ARRAY;
        if (data instanceof CsrRandomAccessibleInterval)
            type = AnnDataFieldType.CSR_MATRIX;
        if (data instanceof CscRandomAccessibleInterval)
            type = AnnDataFieldType.CSC_MATRIX;
        writeArray(writer, path, data, options, type);
    }

    public static <T extends NativeType<T> & RealType<T>> void writeArray(
            N5Writer writer,
            String path,
            RandomAccessibleInterval<T> data,
            N5Options options,
            AnnDataFieldType type) throws IOException {

        try {
            if (type == AnnDataFieldType.DENSE_ARRAY)
                N5Utils.save(data, writer, path, options.blockSize, options.compression, options.exec);
            else if (type == AnnDataFieldType.CSR_MATRIX || type == AnnDataFieldType.CSC_MATRIX)
                writeSparseArray(writer, path, data, options, type);
            else
                throw new UnsupportedOperationException("Writing array data for " + type.toString() + " not supported.");
            writer.setAttribute(path, "shape", new long[]{data.dimension(1), data.dimension(0)});
            writeEncoding(writer, path, type);
        }
        catch (ExecutionException | InterruptedException e) {
            throw new IOException("Could not load dataset at '" + path + "'.", e);
        }
    }

    public static <T extends NativeType<T> & RealType<T>> void writeSparseArray(
            N5Writer writer,
            String path,
            RandomAccessibleInterval<T> data,
            N5Options options,
            AnnDataFieldType type) throws IOException, ExecutionException, InterruptedException {

        if (type != AnnDataFieldType.CSR_MATRIX && type != AnnDataFieldType.CSC_MATRIX)
            throw new IllegalArgumentException("Sparse array type must be CSR or CSC.");

        CompressedStorageRai<T, ?> sparse;
        final boolean typeFitsData = (type == AnnDataFieldType.CSR_MATRIX && data instanceof CsrRandomAccessibleInterval)
                || (type == AnnDataFieldType.CSC_MATRIX && data instanceof CscRandomAccessibleInterval);
        if (typeFitsData) {
           sparse = (CompressedStorageRai<T, ?>) data;
        }
        else {
            final int leadingDim = (type == AnnDataFieldType.CSR_MATRIX) ? 0 : 1;
            sparse = CompressedStorageRai.convertToSparse(data, leadingDim);
        }

        writer.createGroup(path);
        int[] blockSize = (options.blockSize.length == 1) ? options.blockSize : new int[]{options.blockSize[0]*options.blockSize[1]};
        N5Utils.save(sparse.getDataArray(), writer, path + "/data", blockSize, options.compression, options.exec);
        N5Utils.save(sparse.getIndicesArray(), writer, path + "/indices", blockSize, options.compression, options.exec);
        N5Utils.save(sparse.getIndexPointerArray(), writer, path + "/indptr", blockSize, options.compression, options.exec);
    }

    public static void createDataFrame(N5Writer writer, String path, List<String> index) throws IOException {
        writer.createGroup(path);
        writeEncoding(writer, path, AnnDataFieldType.DATA_FRAME);
        writer.setAttribute(path, "_index", "_index");
        // this should be an empty attribute, which N5 doesn't support -> use "" as surrogate
        writer.setAttribute(path, "column-order", "");

        writePrimitiveStringArray((N5HDF5Writer) writer, path + "/_index", index.toArray(new String[0]));
        writeEncoding(writer, path + "/_index", AnnDataFieldType.STRING_ARRAY);
    }

    public static <T extends NativeType<T> & RealType<T>> void addToDataFrame(
            N5Writer writer,
            String dataFrame,
            String label,
            RandomAccessibleInterval<T> data,
            N5Options options) throws IOException {

        List<String> existingData = getExistingDataFrameDatasets(writer, dataFrame);
        if (existingData.contains(label))
            throw new IOException("Dataframe '" + dataFrame + "' already contains '" + label + "'.");

        try {
            N5Utils.save(data, writer, dataFrame + "/" + label, options.blockSize, options.compression, options.exec);
            existingData.add(label);
            writer.setAttribute(dataFrame, "column-order", existingData.toArray());
        } catch (InterruptedException | ExecutionException e) {
            throw new IOException("Could not write dataset '" + dataFrame + "/" + label + "'.", e);
        }
    }

    protected static void writePrimitiveStringArray(N5HDF5Writer writer, String path, String[] array) {
        final IHDF5StringWriter stringWriter = HDF5Factory.open(writer.getFilename()).string();
        stringWriter.writeArrayVL(path, array);
    }

    protected static void createMapping(N5Writer writer, String path) throws IOException {
        writer.createGroup(path);
        writeEncoding(writer, path, AnnDataFieldType.MAPPING);
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
        NULLABLE_BOOL("nullable-bool", "0.1.0"),
        MISSING("missing", "missing");

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
            if (encoding == null || version == null)
                return MISSING;

            for (AnnDataFieldType type : values())
                if (type.encoding.equals(encoding) && type.version.equals(version))
                    return type;

            throw new IllegalArgumentException("No known anndata field with encoding \"" + encoding + "\" and version \"" + version + "\"");
        }
    }

    // functional interface with a lot of parameters
    @FunctionalInterface
    private interface SparseArrayConstructor<
            D extends NativeType<D> & RealType<D>,
            I extends NativeType<I> & IntegerType<I>> {
        CompressedStorageRai<D, I> apply(
                long numCols,
                long numRows,
                RandomAccessibleInterval<D> data,
                RandomAccessibleInterval<I> indices,
                RandomAccessibleInterval<I> indptr);
    }
}
