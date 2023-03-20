import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

import anndata.AbstractCompressedStorageRai;
import anndata.CscRandomAccessibleInterval;
import anndata.CsrRandomAccessibleInterval;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import org.junit.Before;
import org.junit.Test;


public class SparseImageTest {

	private Img<DoubleType> data;
	private Img<LongType> indices;
	private Img<LongType> indptr;
	private AbstractCompressedStorageRai<DoubleType, LongType> csr;
	private AbstractCompressedStorageRai<DoubleType, LongType> csc;
	private List<AbstractCompressedStorageRai<DoubleType, LongType>> sparseImgs;

	@Before
	public void setupSparseImages() {
		data = TestUtils.create1DImgFromList(new ArrayImgFactory<>(new DoubleType()),
				Stream.of(1.0, 1.0, 1.0, 1.0, 1.0).map(DoubleType::new).collect(Collectors.toList()));
		indices = TestUtils.create1DImgFromList(new ArrayImgFactory<>(new LongType()),
				Stream.of(2, 5, 0, 6, 9).map(LongType::new).collect(Collectors.toList()));
		indptr = TestUtils.create1DImgFromList(new ArrayImgFactory<>(new LongType()),
				Stream.of(0, 1, 2, 3, 3, 3, 3, 3, 3, 5).map(LongType::new).collect(Collectors.toList()));

		csr = new CsrRandomAccessibleInterval<>(10, 9, data, indices, indptr);
		csc = new CscRandomAccessibleInterval<>(9, 10, data, indices, indptr);
		sparseImgs = Stream.of(csr, csc).collect(Collectors.toList());
	}

	@Test
	public void csrSetupIsCorrect() {
		assertEquals(2, csr.numDimensions());
		assertArrayEquals(new long[]{0, 0}, csr.minAsLongArray());
		assertArrayEquals(new long[]{9, 8}, csr.maxAsLongArray());

		RandomAccess<DoubleType> ra = csr.randomAccess();
		assertEquals(1.0, ra.setPositionAndGet(2,0).getRealDouble(), 1e-6);
		assertEquals(1.0, ra.setPositionAndGet(5,1).getRealDouble(), 1e-6);
		assertEquals(1.0, ra.setPositionAndGet(0,2).getRealDouble(), 1e-6);
		assertEquals(1.0, ra.setPositionAndGet(6,8).getRealDouble(), 1e-6);
		assertEquals(1.0, ra.setPositionAndGet(9,8).getRealDouble(), 1e-6);
	}

	@Test
	public void sparseHasCorrectNumberOfNonzeros() {
		for (AbstractCompressedStorageRai<DoubleType, LongType> sparse : sparseImgs) {
			int count = 0;
			RandomAccess<DoubleType> ra = sparse.randomAccess();
			for (int i = 0; i <= sparse.max(0); ++i)
				for (int j = 0; j <= sparse.max(1); ++j)
					if (ra.setPositionAndGet(i, j).getRealDouble() != 0.0)
						++count;

			assertEquals(data.max(0)+1, count);
		}
	}

	@Test
	public void cscIsCsrTransposed() {
		RandomAccess<DoubleType> raR = csr.randomAccess();
		RandomAccess<DoubleType> raC = csc.randomAccess();
		for (int i = 0; i <= csr.max(0); ++i)
			for (int j = 0; j <= csr.max(1); ++j)
				assertEquals(raR.setPositionAndGet(i, j), raC.setPositionAndGet(j, i));
	}
}