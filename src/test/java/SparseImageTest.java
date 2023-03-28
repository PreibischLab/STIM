import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import anndata.AbstractCompressedStorageRai;
import anndata.CscRandomAccessibleInterval;
import anndata.CsrRandomAccessibleInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;


@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class SparseImageTest {

	@Test
	public void CSR_setup_is_correct() {
		CsrRandomAccessibleInterval<DoubleType, LongType> csr = setupCsr();
		assertEquals(2, csr.numDimensions());
		assertArrayEquals(new long[]{0, 0}, csr.minAsLongArray());
		assertArrayEquals(new long[]{9, 8}, csr.maxAsLongArray());
	}

	@ParameterizedTest
	@CsvSource({"2,0", "5,1", "0,2", "6,8", "9,8"})
	public void CSR_nonzero_entries_are_correct(int x, int y) {
		RandomAccess<DoubleType> ra = setupCsr().randomAccess();
		assertEquals(1.0, ra.setPositionAndGet(x,y).getRealDouble(), 1e-6);
	}

	@ParameterizedTest
	@MethodSource("setupSparseImages")
	public void sparse_has_correct_number_of_nonzeros(AbstractCompressedStorageRai<DoubleType, LongType> sparse) {
		assertEquals(5, AbstractCompressedStorageRai.getNumberOfNonzeros(sparse));
	}

	@ParameterizedTest
	@MethodSource("setupSparseImages")
	public void conversion_to_sparse_is_correct(AbstractCompressedStorageRai<DoubleType, LongType> sparse) {
		AbstractCompressedStorageRai<DoubleType, LongType> newCsr = AbstractCompressedStorageRai.convertToSparse(sparse, 0);
		assertTrue(newCsr instanceof CsrRandomAccessibleInterval);
		assertRaiEquals(sparse, newCsr);
		AbstractCompressedStorageRai<DoubleType, LongType> newCsc = AbstractCompressedStorageRai.convertToSparse(sparse, 1);
		assertTrue(newCsc instanceof CscRandomAccessibleInterval);
		assertRaiEquals(sparse, newCsc);
	}

	@Test
	public void CSC_is_CSR_transposed() {
		CsrRandomAccessibleInterval<DoubleType, LongType> csr = setupCsr();
		CscRandomAccessibleInterval<DoubleType, LongType> csc = setupCsc();
		assertRaiEquals(csr, Views.permute(csc, 0, 1));
	}

	public CsrRandomAccessibleInterval<DoubleType, LongType> setupCsr() {
		return (CsrRandomAccessibleInterval<DoubleType, LongType>) setupSparseImages().get(0).getPayload();
	}

	public CscRandomAccessibleInterval<DoubleType, LongType> setupCsc() {
		return (CscRandomAccessibleInterval<DoubleType, LongType>) setupSparseImages().get(1).getPayload();
	}

	public static List<Named<AbstractCompressedStorageRai<DoubleType, LongType>>> setupSparseImages() {
		Img<DoubleType> data = create1DImgFromList(new ArrayImgFactory<>(new DoubleType()),
				Stream.of(1.0, 1.0, 1.0, 1.0, 1.0).map(DoubleType::new).collect(Collectors.toList()));
		Img<LongType> indices = create1DImgFromList(new ArrayImgFactory<>(new LongType()),
				Stream.of(2, 5, 0, 6, 9).map(LongType::new).collect(Collectors.toList()));
		Img<LongType> indptr = create1DImgFromList(new ArrayImgFactory<>(new LongType()),
				Stream.of(0, 1, 2, 3, 3, 3, 3, 3, 3, 5).map(LongType::new).collect(Collectors.toList()));

		return Arrays.asList(
				named("CSR", new CsrRandomAccessibleInterval<>(10, 9, data, indices, indptr)),
				named("CSC", new CscRandomAccessibleInterval<>(9, 10, data, indices, indptr)));
	}

	protected static  <T extends Type<T>> Img<T> create1DImgFromList(ImgFactory<T> imgFactory, List<T> values) {
		final Img<T> img = imgFactory.create(values.size());
		final Iterator<T> valueIterator = values.iterator();
		for (final T pixel : img)
			pixel.set(valueIterator.next());
		return img;
	}

	protected static <T extends Type<T>> void assertRaiEquals(RandomAccessibleInterval<T> expected, RandomAccessibleInterval<T> actual) {
		assertEquals(expected.dimension(0), actual.dimension(0), "Number of columns does not coincide.");
		assertEquals(expected.dimension(1), actual.dimension(1), "Number of rows does not coincide.");

		RandomAccess<T> raExpected = expected.randomAccess();
		RandomAccess<T> raActual = actual.randomAccess();
		for (int i = 0; i < expected.dimension(0); ++i)
			for (int j = 0; j < expected.dimension(1); ++j)
				assertEquals(raExpected.setPositionAndGet(i, j), raActual.setPositionAndGet(i, j),
						"Rai's differ on entry (" + i + "," + j +")");
	}
}