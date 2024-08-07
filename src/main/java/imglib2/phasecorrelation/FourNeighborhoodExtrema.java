/*-
 * #%L
 * Multiview stitching of large datasets.
 * %%
 * Copyright (C) 2016 - 2017 Big Stitcher developers.
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
package imglib2.phasecorrelation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

public class FourNeighborhoodExtrema
{
	
	/**
	 * merge the pre-sorted lists in lists, keep at most the maxN biggest values from any list in the result
	 * @param lists lists to merge
	 * @param maxN maximum size of output list
	 * @param compare comparator
	 * @param <T> list content type
	 * @return merged list with size {@literal < maxN}
	 */	
	public static <T> ArrayList<T> merge(List<List<T>> lists, final int maxN, Comparator<T> compare){
		ArrayList<T> res = new ArrayList<>();
		int[] idxs = new int[lists.size()];
		boolean[] hasMore = new boolean[lists.size()];
		boolean allDone = true;
		
		for (int i = 0; i < lists.size(); i++){
			hasMore[i] = !lists.get(i).isEmpty();
			allDone &= !hasMore[i];
		}
		
		while(!allDone && res.size() < maxN ){
		
			int activeLists = 0;
			int maxList = 0;
			for (int i = 0; i<hasMore.length; i++){
				if (hasMore[i]){
					maxList=i;
					break;
				}
			}
			
			for (int i = 0; i< lists.size(); i++){
				if(!hasMore[i]){ continue;}
				activeLists++;
				if (compare.compare(lists.get(i).get(idxs[i]),(lists.get(maxList).get(idxs[maxList]))) >= 0){
					maxList = i;
				}
			}
			
			res.add(lists.get(maxList).get(idxs[maxList]));
			idxs[maxList]++;
			if (idxs[maxList] >= lists.get(maxList).size()){
				hasMore[maxList] = false;
				activeLists--;
			}
			
			allDone = activeLists == 0;			
			
		}	
		
		return res;
	}
	
	/**
	 * split the given Interval into nSplits intervals along the largest dimension
	 * @param interval input interval
	 * @param nSplits how may splits
	 * @return list of intervals input was split into
	 */
	public static List<Interval> splitAlongLargestDimension(Interval interval, long nSplits){
		
		List<Interval> res = new ArrayList<>();
		
		long[] min = new long[interval.numDimensions()];
		long[] max = new long[interval.numDimensions()];
		interval.min(min);
		interval.max(max);
		
		int splitDim = 0;
		for (int i = 0; i< interval.numDimensions(); i++){
			if (interval.dimension(i) > interval.dimension(splitDim)) splitDim = i;
		}

		// there could be more splits than actual dimension entries
		nSplits = Math.min( nSplits, interval.dimension(splitDim) );

		long chunkSize = interval.dimension(splitDim) / nSplits;
		long maxSplitDim = max[splitDim];
		
		for (int i = 0; i<nSplits; i++){
			if (i != 0){
				min[splitDim] += chunkSize;	
			}
			max[splitDim] = min[splitDim] + chunkSize - 1;
			if (i == nSplits -1){
				max[splitDim] = maxSplitDim;
			}
			res.add(new FinalInterval(min, max));
		}
			
		return res;
	}
	
	public static < T extends RealType< T > > ArrayList< Pair< Localizable, Double > > findMaxMT( final RandomAccessible< T > img, final Interval region, final int maxN , ExecutorService service){
		
		
		int nTasks = Runtime.getRuntime().availableProcessors() * 4;
		List<Interval> intervals = splitAlongLargestDimension(region, nTasks);
		List<Future<ArrayList< Pair< Localizable, Double > >>> futures = new ArrayList<>();
		
		for (final Interval i : intervals){
			futures.add(service.submit(() -> findMax(img, i, maxN)));
		}
		
		List<List< Pair< Localizable, Double > >> toMerge = new ArrayList<>();
		
		for (Future<ArrayList< Pair< Localizable, Double > >> f : futures){
			try {
				toMerge.add(f.get());
			} catch (InterruptedException | ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return merge(toMerge, maxN, (o1, o2) -> (int) Math.signum(o1.getB() - o2.getB()));
	}
	
	public static < T extends RealType< T > > ArrayList< Pair< Localizable, Double > > findMax( final RandomAccessible< T > img, final Interval region, final int maxN )
	{
		final Cursor< T > c = Views.iterable( Views.interval( img, region ) ).localizingCursor();
		final RandomAccess< T > r = img.randomAccess();
		final int n = img.numDimensions();

		final ArrayList< Pair< Localizable, Double > > list = new ArrayList<>();

		for ( int i = 0; i < maxN; ++i )
			list.add(new ValuePair<>(null, -Double.MAX_VALUE) );

A:		while ( c.hasNext() )
		{
			final double type = c.next().getRealDouble();
			r.setPosition( c );

			for ( int d = 0; d < n; ++d )
			{
				r.fwd( d );
				if ( type < r.get().getRealDouble() )
					continue A;
	
				r.bck( d );
				r.bck( d );
				
				if ( type < r.get().getRealDouble() )
					continue A;

				r.fwd( d );
			}

			
			for ( int i = maxN - 1; i >= 0; --i )
			{
				if ( type < list.get( i ).getB() )
				{
					if ( i == maxN - 1 )
					{
						continue A;
					}
					else
					{
						list.add( i + 1, new ValuePair<>(new Point(c), type) );
						list.remove( maxN );
						continue A;
					}
				}
			}

			list.add( 0, new ValuePair<>(new Point(c), type) );
			list.remove( maxN );
		}

		// remove all null elements
		for ( int i = maxN -1; i >= 0; --i )
			if ( list.get( i ).getA() == null )
				list.remove(  i );

		return list;
	}
}
