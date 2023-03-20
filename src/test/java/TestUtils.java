import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.Type;

import java.util.Iterator;
import java.util.List;

public class TestUtils {
	public static  <T extends Type<T>> Img<T> create1DImgFromList(ImgFactory<T> imgFactory, List<T> values) {
		final Img<T> img = imgFactory.create(values.size());
		final Iterator<T> valueIterator = values.iterator();
		for (final T pixel : img)
			pixel.set(valueIterator.next());
		return img;
	}
}
