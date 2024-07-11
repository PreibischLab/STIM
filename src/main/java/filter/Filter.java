package filter;

import net.imglib2.RealLocalizable;

public interface Filter<T> {
	void filter(final RealLocalizable position, final T output);
}
