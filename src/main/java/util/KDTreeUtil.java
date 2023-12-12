package util;

import java.util.ArrayList;
import java.util.List;

import net.imglib2.IterableRealInterval;
import net.imglib2.KDTree;
import net.imglib2.RealCursor;
import net.imglib2.type.Type;

public class KDTreeUtil
{
	public static <T extends Type<T>> KDTree<T> createParallelizableKDTreeFrom(IterableRealInterval<T> data) {
		final List<RealCursor<T>> positions = new ArrayList<>();
		final List<T> values = new ArrayList<>();

		RealCursor<T> cursor = data.localizingCursor();
		while (cursor.hasNext()) {
			cursor.next();
			positions.add(cursor.copyCursor());
			values.add(cursor.get().copy());
		}

		return new KDTree<T>(values, positions);
	}

}
