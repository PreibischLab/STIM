package anndata;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import org.junit.Test;


public class SparseImageTest {

	private Img<DoubleType> data;
	private Img<LongType> indices;
	private Img<LongType> indptr;

	@Test
	public void correctNumberOfNonzeros() {
		data = create1DImgFromList(new ArrayImgFactory<>(new DoubleType()),
				Stream.of(1.0, 1.0, 1.0, 1.0, 1.0).map(DoubleType::new).collect(Collectors.toList()));
		indices = create1DImgFromList(new ArrayImgFactory<>(new LongType()),
				Stream.of(2, 5, 0, 6, 9).map(LongType::new).collect(Collectors.toList()));
		indptr = create1DImgFromList(new ArrayImgFactory<>(new LongType()),
				Stream.of(0, 1, 2, 3, 3, 3, 3, 3, 3, 5).map(LongType::new).collect(Collectors.toList()));

		AbstractCompressedStorageRai<DoubleType, LongType> sparse = new CsrRandomAccessibleInterval<>(10, 9, data, indices, indptr);
		RandomAccess<DoubleType> ra = sparse.randomAccess();
		assertEquals(1.0, ra.setPositionAndGet(2,0).getRealDouble(), 1e-6);
		assertEquals(1.0, ra.setPositionAndGet(5,1).getRealDouble(), 1e-6);
		assertEquals(1.0, ra.setPositionAndGet(0,2).getRealDouble(), 1e-6);
		assertEquals(1.0, ra.setPositionAndGet(6,8).getRealDouble(), 1e-6);
		assertEquals(1.0, ra.setPositionAndGet(9,8).getRealDouble(), 1e-6);


		assertEquals(2, sparse.numDimensions());
		assertArrayEquals(new long[]{0, 0}, sparse.minAsLongArray());
		int count = 0;
		for (int i = 0; i <= sparse.max(0); ++i)
			for (int j = 0; j <= sparse.max(1); ++j)
				if (ra.setPositionAndGet(i, j).getRealDouble() != 0.0)
					++count;
		assertEquals(data.max(0)+1, count);

	}

	private <T extends Type<T>> Img<T> create1DImgFromList(ImgFactory<T> imgFactory, List<T> values) {
		final Img<T> img = imgFactory.create(values.size());
		final Iterator<T> valueIterator = values.iterator();
		for (final T pixel : img)
			pixel.set(valueIterator.next());
		return img;
	}
}