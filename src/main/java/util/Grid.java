/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2023 Multiview Reconstruction developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.imglib2.Interval;
import net.imglib2.util.Intervals;

/**
 *
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class Grid {

	private Grid() {}

	/*
	 * Crops the dimensions of a {@link DataBlock} at a given offset to fit
	 * into and {@link Interval} of given dimensions.  Fills long and int
	 * version of cropped block size.  Also calculates the grid raster position
	 * assuming that the offset divisible by block size without remainder.
	 *
	 * @param max
	 * @param offset
	 * @param blockSize
	 * @param croppedBlockSize
	 * @param intCroppedBlockDimensions
	 * @param gridPosition
	 */
	static void cropBlockDimensions(
			final long[] dimensions,
			final long[] offset,
			final int[] outBlockSize,
			final int[] blockSize,
			final long[] croppedBlockSize,
			final long[] gridPosition) {

		for (int d = 0; d < dimensions.length; ++d) {
			croppedBlockSize[d] = Math.min(blockSize[d], dimensions[d] - offset[d]);
			gridPosition[d] = offset[d] / outBlockSize[d];
		}
	}

	/*
	 * Create a {@link List} of grid blocks that, for each grid cell, contains
	 * the world coordinate offset, the size of the grid block, and the
	 * grid-coordinate offset.  The spacing for input grid and output grid
	 * are independent, i.e. world coordinate offsets and cropped block-sizes
	 * depend on the input grid, and the grid coordinates of the block are
	 * specified on an independent output grid.  It is assumed that
	 * gridBlockSize is an integer multiple of outBlockSize.
	 *
	 * @param dimensions
	 * @param gridBlockSize
	 * @param outBlockSize
	 * @return
	 */
	public static List<long[][]> create(
			final long[] dimensions,
			final int[] gridBlockSize,
			final int[] outBlockSize) {

		final int n = dimensions.length;
		final ArrayList<long[][]> gridBlocks = new ArrayList<>();

		final long[] offset = new long[n];
		final long[] gridPosition = new long[n];
		final long[] longCroppedGridBlockSize = new long[n];
		for (int d = 0; d < n;) {
			cropBlockDimensions(dimensions, offset, outBlockSize, gridBlockSize, longCroppedGridBlockSize, gridPosition);
				gridBlocks.add(
						new long[][]{
							offset.clone(),
							longCroppedGridBlockSize.clone(),
							gridPosition.clone()
						});

			for (d = 0; d < n; ++d) {
				offset[d] += gridBlockSize[d];
				if (offset[d] < dimensions[d])
					break;
				else
					offset[d] = 0;
			}
		}
		return gridBlocks;
	}

	/*
	 * Create a {@link List} of grid blocks that, for each grid cell, contains
	 * the world coordinate offset, the size of the grid block, and the
	 * grid-coordinate offset.
	 *
	 * @param dimensions
	 * @param blockSize
	 * @return
	 */
	public static List<long[][]> create(
			final long[] dimensions,
			final int[] blockSize) {

		return create(dimensions, blockSize, blockSize);
	}


	/*
	 * Create a {@link List} of grid block offsets in world coordinates
	 * covering an {@link Interval} at a given spacing.
	 *
	 * @param interval
	 * @param spacing
	 * @return
	 */
	public static List<long[]> createOffsets(
			final Interval interval,
			final int[] spacing) {

		final int n = interval.numDimensions();
		final ArrayList<long[]> offsets = new ArrayList<>();

		final long[] offset = Intervals.minAsLongArray(interval);
		for (int d = 0; d < n;) {
			offsets.add(offset.clone());

			for (d = 0; d < n; ++d) {
				offset[d] += spacing[d];
				if (offset[d] <= interval.max(d))
					break;
				else
					offset[d] = interval.min(d);
			}
		}
		return offsets;
	}

	/*
	 * Returns the grid coordinates of a given offset for a min coordinate and
	 * a grid spacing.
	 *
	 * @param offset
	 * @param min
	 * @param spacing
	 * @return
	 */
	public static long[] gridCell(
			final long[] offset,
			final long[] min,
			final int[] spacing) {

		final long[] gridCell = new long[offset.length];
		Arrays.setAll(gridCell, i -> (offset[i] - min[i]) / spacing[i]);
		return gridCell;
	}

	/*
	 * Returns the long coordinates smaller or equal scaled double coordinates.
	 *
	 * @param doubles
	 * @param scale
	 * @return
	 */
	public static long[] floorScaled(final double[] doubles, final double scale) {

		final long[] floorScaled = new long[doubles.length];
		Arrays.setAll(floorScaled, i -> (long)Math.floor(doubles[i] * scale));
		return floorScaled;
	}

	/*	
	 * Returns the long coordinate greater or equal scaled double coordinates.
	 *
	 * @param doubles
	 * @param scale
	 * @return
	 */
	public static long[] ceilScaled(final double[] doubles, final double scale) {

		final long[] ceilScaled = new long[doubles.length];
		Arrays.setAll(ceilScaled, i -> (long)Math.ceil(doubles[i] * scale));
		return ceilScaled;
	}
}
