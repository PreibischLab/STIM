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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import ij.ImageJ;
import imglib2.ImgLib2Util;
import imglib2.phasecorrelation.PhaseCorrelation2.ComplexPowerGLogRealConverter;
import net.imglib2.Cursor;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccess;
import net.imglib2.algorithm.fft2.FFT;
import net.imglib2.algorithm.fft2.FFTMethods;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.PolarToCartesianTransform2D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ComplexType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.complex.ComplexFloatType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import util.CompensatedSum;
import util.Threads;
import util.Threads.ImagePortion;



public class PhaseCorrelation2Util {
	
	/*
	 * copy source to dest. they do not have to be of the same size, but source must fit in dest
	 * @param source
	 * @param dest
	 */
	public static <T extends RealType<T>, S extends RealType<S>> void copyRealImage(final IterableInterval<T> source, final RandomAccessibleInterval<S> dest, ExecutorService service) {
		
		final Vector<ImagePortion> portions = Threads.divideIntoPortions( source.size() );
		ArrayList<Future<?>> futures = new ArrayList<>();
		final AtomicInteger ai = new AtomicInteger(-1);

		for (int i = 0; i < portions.size(); i++){
			futures.add(service.submit(() -> {
				RandomAccess<S> destRA = dest.randomAccess();
				Cursor<T> srcC = source.localizingCursor();

				ImagePortion ip = portions.get(ai.incrementAndGet());

				long loopSize = ip.getLoopSize();

				srcC.jumpFwd(ip.getStartPosition());

				for (long l = 0; l < loopSize; l++){
					srcC.fwd();
					destRA.setPosition(srcC);
					destRA.get().setReal(srcC.get().getRealDouble());
				}

			}));
		}

		for (Future<?> f : futures){
			try {
				f.get();
			} catch (InterruptedException | ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}		
		
	}
	
	/**
	 * calculate the size difference of two Dimensions objects (dim2-dim1)
	 * @param dim1 first Dimensions
	 * @param dim2 second Dimensions
	 * @return int array of difference
	 */	
	public static int[] getSizeDifference(Dimensions dim1, Dimensions dim2) {
		int[] diff = new int[dim1.numDimensions()];
		for (int i = 0; i < dim1.numDimensions(); i++){
			diff[i] = (int) (dim2.dimension(i) - dim1.dimension(i));
		}		
		return diff;
	}
	
	/**
	 * calculate the size of an extended image big enough to hold dim1 and dim2
	 * with each dimension also enlarged by extension pixels on each side (but at most by the original image size)
	 * @param dim1 first Dimensions
	 * @param dim2 second Dimensions
	 * @param extension: number of pixels to add at each side in each dimension
	 * @return extended dimensions
	 */
	public static FinalDimensions getExtendedSize(Dimensions dim1, Dimensions dim2, int [] extension) {
		long[] extDims = new long[dim1.numDimensions()];
		for (int i = 0; i <dim1.numDimensions(); i++){
			extDims[i] = Math.max(dim1.dimension(i), dim2.dimension(i));
			long extBothSides = extDims[i] < extension[i] ? extDims[i] * 2 : extension[i] * 2L;
			extDims[i] += extBothSides;
		}
		return new FinalDimensions(extDims);		
	}
	
	/*
	 * return a BlendedExtendedMirroredRandomAccesible of img extended extension pixels on each side (but at most by the original image size)
	 * @param img
	 * @param extension: number of blending pixels to add at each side in each dimension
	 * @return
	 */
	public static <T extends RealType<T>> RandomAccessible<T> extendImageByFactor(RandomAccessibleInterval<T> img, int [] extension)
	{
		int[] extEachSide = new int[img.numDimensions()];
		for (int i = 0; i <img.numDimensions(); i++){
			extEachSide[i] = (int) (img.dimension(i) < extension[i] ? img.dimension(i) : extension[i]);	
		}
		return new BlendedExtendedMirroredRandomAccessible2<>(img, extEachSide);
	}
	
	/*
	 * returns the extension at each side if an image is enlarged by a factor of extensionFactor at each side
	 * @param dims
	 * @param extensionFactor
	 * @return
	 */
	public static int[] extensionByFactor(Dimensions dims, double extensionFactor){
		int[] res = new int[dims.numDimensions()];
		for (int i = 0; i< dims.numDimensions(); i++){
			res[i] = (int) (dims.dimension(i)*extensionFactor);
		}
		return res;
	}
	
	
	/*
	 * return a BlendedExtendedMirroredRandomAccesible of img extended to extDims
	 * @param img
	 * @param extDims
	 * @return
	 */
	public static <T extends RealType<T>> RandomAccessible<T> extendImageToSize(RandomAccessibleInterval<T> img, Dimensions extDims)
	{
		int[] extEachSide = getSizeDifference(img, extDims);
		for (int i = 0; i< img.numDimensions(); i++){
			extEachSide[i] /= 2;
		}
		return new BlendedExtendedMirroredRandomAccessible2<>(img, extEachSide);
	}


	public static <T extends RealType<T>, S extends RealType<S>> void calculateCrossCorrParallel(
			List<PhaseCorrelationPeak2> peaks, final RandomAccessibleInterval<T> img1, final RandomAccessibleInterval<S> img2,
			final long minOverlapPx, ExecutorService service)
	{
		calculateCrossCorrParallel( peaks, img1, img2, minOverlapPx, service, false );
	}

	/*
	 * calculate the crosscorrelation of img1 and img2 for all shifts represented by a PhasecorrelationPeak List in parallel using a specified
	 * ExecutorService. service remains functional after the call
	 * @param peaks
	 * @param img1
	 * @param img2
	 * @param minOverlapPx minimal number of overlapping pixels in each Dimension, may be null to indicate no minimum
	 * @param service
	 */
	public static <T extends RealType<T>, S extends RealType<S>> void calculateCrossCorrParallel(
			List<PhaseCorrelationPeak2> peaks, final RandomAccessibleInterval<T> img1, final RandomAccessibleInterval<S> img2,
			final long minOverlapPx, ExecutorService service, boolean interpolateSubpixel)
	{
		List<Future<?>> futures = new ArrayList<>();

		for (final PhaseCorrelationPeak2 p : peaks){
			futures.add(service.submit(() -> p.calculateCrossCorr(img1, img2, minOverlapPx, interpolateSubpixel)));
		}

		for (Future<?> f: futures){
			try {
				f.get();
			} catch (InterruptedException | ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	/*
	 * find local maxima in PCM
	 * @param pcm
	 * @param service
	 * @param maxN 
	 * @return
	 */
	public static <T extends RealType<T>> List<PhaseCorrelationPeak2> getPCMMaxima(RandomAccessibleInterval<T> pcm, ExecutorService service, int maxN, boolean subpixelAccuracy){
		
		List<PhaseCorrelationPeak2> res = new ArrayList<>();
		
		ArrayList<Pair<Localizable, Double>> maxima = FourNeighborhoodExtrema.findMaxMT(Views.extendPeriodic(pcm), pcm, maxN, service);
		//ArrayList<Pair<Localizable, Double>> maxima = FourNeighborhoodExtrema.findMax(Views.extendPeriodic(pcm), pcm, maxN);
		
		
				
		for (Pair<Localizable, Double> p: maxima){
			PhaseCorrelationPeak2 pcp = new PhaseCorrelationPeak2(p.getA(), p.getB());
			if (subpixelAccuracy)
				pcp.calculateSubpixelLocalization(pcm);

			res.add(pcp);
		}
		return res;		
	}
	
	/*
	 * find maxima in PCM, use a temporary thread pool for calculation
	 * @param pcm
	 * @param nMax 
	 * @return
	 */
	public static <T extends RealType<T>> List<PhaseCorrelationPeak2> getPCMMaxima(RandomAccessibleInterval<T> pcm, int nMax, boolean subpixelAccuracy){
		ExecutorService tExecService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		List<PhaseCorrelationPeak2> res = getPCMMaxima(pcm, tExecService, nMax, subpixelAccuracy);
		tExecService.shutdown();
		return res;
	}
	
	/*
	 * sort PCM Peaks by phaseCorrelation and return a new list containing just the nToKeep highest peaks
	 * @param rawPeaks
	 * @param nToKeep
	 * @return
	 */
	@Deprecated
	public static List<PhaseCorrelationPeak2> getHighestPCMMaxima(List<PhaseCorrelationPeak2> rawPeaks, long nToKeep){
		rawPeaks.sort(Collections.reverseOrder(new PhaseCorrelationPeak2.ComparatorByPhaseCorrelation()));
		List<PhaseCorrelationPeak2> res = new ArrayList<>();
		for (int i = 0; i < nToKeep; i++){
			res.add(new PhaseCorrelationPeak2(rawPeaks.get(i)));
		}
		return res;
	}
	/*
	 * expand a list of PCM maxima to to a list containing all possible shifts corresponding to these maxima
	 * @param peaks
	 * @param pcmDims
	 * @param img1Dims
	 * @param img2Dims
	 */
	public static void expandPeakListToPossibleShifts(List<PhaseCorrelationPeak2> peaks,
			Dimensions pcmDims, Dimensions img1Dims, Dimensions img2Dims)
	{
		List<PhaseCorrelationPeak2> res = new ArrayList<>();
		for (PhaseCorrelationPeak2 p : peaks){
			res.addAll(expandPeakToPossibleShifts(p, pcmDims, img1Dims, img2Dims));
		}
		peaks.clear();
		peaks.addAll(res);
	}
	
	/*
	 * expand a single maximum in the PCM to a list of possible shifts corresponding to that peak
	 * an offset due to different images sizes is accounted for
	 * @param peak
	 * @param pcmDims
	 * @param img1Dims
	 * @param img2Dims
	 * @return
	 */
	public static List<PhaseCorrelationPeak2> expandPeakToPossibleShifts(
			PhaseCorrelationPeak2 peak, Dimensions pcmDims, Dimensions img1Dims, Dimensions img2Dims)
	{
		int n = pcmDims.numDimensions();
		double[] subpixelDiff = new double[n];
		
		if (peak.getSubpixelPcmLocation() != null)
			for (int i = 0; i < n; i++)
				subpixelDiff[i] = peak.getSubpixelPcmLocation().getDoublePosition( i ) - peak.getPcmLocation().getIntPosition( i );

		int[] originalPCMPeakWithOffset = new int[n];
		peak.getPcmLocation().localize(originalPCMPeakWithOffset);

		int[] extensionImg1 = getSizeDifference(img1Dims, pcmDims);
		int[] extensionImg2 = getSizeDifference(img2Dims, pcmDims);
		int[] offset = new int[pcmDims.numDimensions()];
		for(int i = 0; i < offset.length; i++){
			offset[i] = (extensionImg2[i] - extensionImg1[i] ) / 2;
			originalPCMPeakWithOffset[i] += offset[i];
			originalPCMPeakWithOffset[i] %= (int) pcmDims.dimension(i);
		}		
		
		List<PhaseCorrelationPeak2> shiftedPeaks = new ArrayList<>();
		for (int i = 0; i < Math.pow(2, pcmDims.numDimensions()); i++){
			int[] possibleShift = originalPCMPeakWithOffset.clone();
			PhaseCorrelationPeak2 peakWithShift = new PhaseCorrelationPeak2(peak);
			for (int d = 0; d < pcmDims.numDimensions(); d++){
				/*
				 * mirror the shift around the origin in dimension d if (i / 2^d) is even
				 * --> all possible shifts
				 */
				if ((i / (int) Math.pow(2, d) % 2) == 0){
					possibleShift[d] = possibleShift[d] < 0 ? possibleShift[d] + (int) pcmDims.dimension(d) : possibleShift[d] - (int) pcmDims.dimension(d);
				}
			}
			peakWithShift.setShift(new Point(possibleShift));

			if (peakWithShift.getSubpixelPcmLocation() != null)
			{
				double[] subpixelShift = new double[n];
				for (int j =0; j<n;j++){
					subpixelShift[j] = possibleShift[j] + subpixelDiff[j];
				}

				peakWithShift.setSubpixelShift( new RealPoint( subpixelShift ) );
			}
			shiftedPeaks.add(peakWithShift);
		}		
		return shiftedPeaks;
	}
	
	/*
	 * get intervals corresponding to overlapping area in two images (relative to image origins)
	 * will return null if there is no overlap
	 * @param img1
	 * @param img2
	 * @param shift
	 * @return
	 */
	public static Pair<Interval, Interval> getOverlapIntervals(Dimensions img1, Dimensions img2, Localizable shift){
		
		final int numDimensions = img1.numDimensions();
		final long[] offsetImage1 = new long[ numDimensions ];
		final long[] offsetImage2 = new long[ numDimensions ];
		final long[] maxImage1 = new long[ numDimensions ];
		final long[] maxImage2 = new long[ numDimensions ];
		
		long overlapSize;
		
		for ( int d = 0; d < numDimensions; ++d )
		{
			if ( shift.getLongPosition(d) >= 0 )
			{
				// two possiblities
				//
				//               shift=start              end
				//                 |					   |
				// A: Image 1 ------------------------------
				//    Image 2      ----------------------------------
				//
				//               shift=start	    end
				//                 |			     |
				// B: Image 1 ------------------------------
				//    Image 2      -------------------
				
				// they are not overlapping ( this might happen due to fft zeropadding and extension )
				if ( shift.getLongPosition(d) >= img1.dimension( d ) )
				{
					return null;
				}
				
				offsetImage1[ d ] = shift.getLongPosition(d);
				offsetImage2[ d ] = 0;
				overlapSize = Math.min( img1.dimension( d ) - shift.getLongPosition(d),  img2.dimension( d ) );
				maxImage1[ d ] = offsetImage1[d] + overlapSize -1;
				maxImage2[ d ] = offsetImage2[d] + overlapSize -1;
			}
			else
			{
				// two possiblities
				//
				//          shift start                	  end
				//            |	   |			`		   |
				// A: Image 1      ------------------------------
				//    Image 2 ------------------------------
				//
				//          shift start	     end
				//            |	   |          |
				// B: Image 1      ------------
				//    Image 2 -------------------
				
				// they are not overlapping ( this might happen due to fft zeropadding and extension
				if ( shift.getLongPosition(d) <= -img2.dimension( d ) )
				{
					return null;
				}

				offsetImage1[ d ] = 0;
				offsetImage2[ d ] = -shift.getLongPosition(d);
				overlapSize =  Math.min( img2.dimension( d ) + shift.getLongPosition(d),  img1.dimension( d ) );
				maxImage1[ d ] = offsetImage1[d] + overlapSize -1;
				maxImage2[ d ] = offsetImage2[d] + overlapSize -1;
			}
			
		}		
		
		FinalInterval img1Interval = new FinalInterval(offsetImage1, maxImage1);
		FinalInterval img2Interval = new FinalInterval(offsetImage2, maxImage2);

		return new ValuePair<>(img1Interval, img2Interval);
	}
	
	/*
	 * multiply complex numbers c1 and c2, set res to the result of multiplication
	 * @param c1
	 * @param c2
	 * @param res
	 */
	public static <R extends ComplexType<R>, S extends ComplexType<S>, T extends ComplexType<T>> void multiplyComplex(
			R c1, S c2, T res)
	{
		double a = c1.getRealDouble();
		double b = c1.getImaginaryDouble();
		double c = c2.getRealDouble();
		double d = c2.getImaginaryDouble();
		res.setReal(a*c - b*d);
		res.setImaginary(a*d + b*c);
	}
	
	/*
	 * pixel-wise multiplication of img1 and img2
	 * res is overwritten by the result
	 * @param img1
	 * @param img2
	 * @param res
	 */
	public static <R extends ComplexType<R>, S extends ComplexType<S>, T extends ComplexType<T>> void multiplyComplexIntervals(
			final RandomAccessibleInterval<R> img1, final RandomAccessibleInterval<S> img2, final RandomAccessibleInterval<T> res, ExecutorService service) 
	{
		
		
		final Vector<ImagePortion> portions = Threads.divideIntoPortions (Views.iterable(img1).size() );		
		List<Future<?>> futures = new ArrayList<>();
		final AtomicInteger ai = new AtomicInteger(-1);
		
		for (int i = 0; i  < portions.size(); i++){
			futures.add(service.submit(() -> {

				ImagePortion ip = portions.get(ai.incrementAndGet());
				long loopSize = ip.getLoopSize();

				if (Views.iterable(img1).iterationOrder().equals(Views.iterable(img2).iterationOrder()) &&
						Views.iterable(img1).iterationOrder().equals(Views.iterable(res).iterationOrder())){
					Cursor<T> cRes = Views.iterable(res).cursor();
					Cursor<R> cSrc1 = Views.iterable(img1).cursor();
					Cursor<S> cSrc2 = Views.iterable(img2).cursor();

					cSrc1.jumpFwd(ip.getStartPosition());
					cSrc2.jumpFwd(ip.getStartPosition());
					cRes.jumpFwd(ip.getStartPosition());

					for (long l = 0; l < loopSize; l++){
						cRes.fwd();
						cSrc1.fwd();
						cSrc2.fwd();
						multiplyComplex(cSrc1.get(), cSrc2.get(), cRes.get());
					}
				}

				else {
					final RandomAccess<R> ra1 = img1.randomAccess();
					final RandomAccess<S> ra2 = img2.randomAccess();
					final Cursor<T> cRes = Views.iterable(res).localizingCursor();

					cRes.jumpFwd(ip.getStartPosition());

					for (long l = 0; l < loopSize; l++){
						cRes.fwd();
						ra1.setPosition(cRes);
						ra2.setPosition(cRes);
						multiplyComplex(ra1.get(), ra2.get(), cRes.get());
					}
				}

			}));
			
			for (Future<?> f : futures){
				try {
					f.get();
				} catch (InterruptedException | ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
	}
	
	/*
	 * calculate complex conjugate of c, save result to res
	 * @param c
	 * @param res
	 */
	public static <R extends ComplexType<R>, S extends ComplexType<S>> void complexConj(R c, S res) {
		res.setComplexNumber(c.getRealDouble(), - c.getImaginaryDouble());
	}
	
	/*
	 * calculate element-wise complex conjugate of img, save result to res
	 * @param img
	 * @param res
	 */
	public static <R extends ComplexType<R>, S extends ComplexType<S>> void complexConjInterval(
			final RandomAccessibleInterval<R>	img, final RandomAccessibleInterval<S> res, ExecutorService service)
	{
		
		final Vector<ImagePortion> portions = Threads.divideIntoPortions( Views.iterable(img).size() );		
		List<Future<?>> futures = new ArrayList<>();
		final AtomicInteger ai = new AtomicInteger(-1);
		
		for (int i = 0; i  < portions.size(); i++){
			futures.add(service.submit(() -> {

				ImagePortion ip = portions.get(ai.incrementAndGet());
				long loopSize = ip.getLoopSize();

				if (Views.iterable(img).iterationOrder().equals(Views.iterable(res).iterationOrder())){
					Cursor<S> cRes = Views.iterable(res).cursor();
					Cursor<R> cSrc = Views.iterable(img).cursor();

					cSrc.jumpFwd(ip.getStartPosition());
					cRes.jumpFwd(ip.getStartPosition());

					for (long l = 0; l < loopSize; l++){
						cRes.fwd();
						cSrc.fwd();
						complexConj(cSrc.get(), cRes.get());
					}
				}

				else {
					Cursor<S> cRes = Views.iterable(res).localizingCursor();
					RandomAccess<R> raImg = img.randomAccess();

					cRes.jumpFwd(ip.getStartPosition());

					for (long l = 0; l < loopSize; l++){
						cRes.fwd();
						raImg.setPosition(cRes);
						complexConj(raImg.get(), cRes.get());
					}
				}

			}));
			
			for (Future<?> f : futures){
				try {
					f.get();
				} catch (InterruptedException | ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
	}
	
	/*
	 * normalize complex number c1 to length 1, save result to res
	 * if the length of c1 is less than normalizationThreshold, set res to 0
	 * @param c1
	 * @param res
	 * @param normalizationThreshold
	 */
	public static <R extends ComplexType<R>, S extends ComplexType<S>> void normalize( R c1, S res, double normalizationThreshold)
	{
		double len = c1.getPowerDouble();
		if (len > normalizationThreshold){
			res.setReal(c1.getRealDouble()/len);
			res.setImaginary(c1.getImaginaryDouble()/len);
		} else {
			res.setComplexNumber(0, 0);
		}
		
	}
	
	/*
	 * normalization with default threshold
	 * @param c1
	 * @param res
	 */
	public static <R extends ComplexType<R>, S extends ComplexType<S>> void normalize( R c1, S res){
		normalize(c1, res, 1e-5);
	}	


	/*
	 * normalize complex valued img to length 1, pixel-wise, saving result to res
	 * if the length of a pixel is less than normalizationThreshold, set res to 0
	 * @param img
	 * @param res
	 * @param normalizationThreshold
	 */
	public static <T extends ComplexType<T>, S extends ComplexType<S>>void normalizeInterval(
			final RandomAccessibleInterval<T> img, final RandomAccessibleInterval<S> res, final double normalizationThreshold, ExecutorService service) 
	{
		
		final Vector<ImagePortion> portions = Threads.divideIntoPortions( Views.iterable(img).size() );		
		List<Future<?>> futures = new ArrayList<>();
		final AtomicInteger ai = new AtomicInteger(-1);
		
		for (int i = 0; i  < portions.size(); i++){
			futures.add(service.submit(() -> {

				ImagePortion ip = portions.get(ai.incrementAndGet());
				long loopSize = ip.getLoopSize();

				if (Views.iterable(img).iterationOrder().equals(Views.iterable(res).iterationOrder())){
					Cursor<S> cRes = Views.iterable(res).cursor();
					Cursor<T> cSrc = Views.iterable(img).cursor();

					cSrc.jumpFwd(ip.getStartPosition());
					cRes.jumpFwd(ip.getStartPosition());

					for (long l = 0; l < loopSize; l++){
						cRes.fwd();
						cSrc.fwd();
						normalize(cSrc.get(), cRes.get(), normalizationThreshold);
					}
				}

				else {
					Cursor<S> cRes = Views.iterable(res).localizingCursor();
					RandomAccess<T> raImg = img.randomAccess();

					cRes.jumpFwd(ip.getStartPosition());

					for (long l = 0; l < loopSize; l++){
						cRes.fwd();
						raImg.setPosition(cRes);
						normalize(raImg.get(), cRes.get(), normalizationThreshold);
					}
				}

			}));
			
			for (Future<?> f : futures){
				try {
					f.get();
				} catch (InterruptedException | ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		
		
		
	}
	/*
	 * normalization with default threshold
	 * @param img
	 * @param res
	 */
	public static <T extends ComplexType<T>, S extends ComplexType<S>>void normalizeInterval(
			RandomAccessibleInterval<T> img, RandomAccessibleInterval<S> res, ExecutorService service){
		normalizeInterval(img, res, 1E-5, service);
	}
	
	/*
	 * get the mean pixel intensity of an img
	 * @param img
	 * @return
	 */
	public static <T extends RealType<T>> double getMean(RandomAccessibleInterval<T> img)
	{
		// TODO: if #pixels > ???? else RealSum
		// TODO: integral image?
		double sum = 0.0;
		long n = 0;
		for (T pix: Views.iterable(img))
		{
			final double v = pix.getRealDouble();
			if ( v > 0 )
			{
				sum += pix.getRealDouble();
				n++;
			}
		}

		System.out.println( "n: " + n );
		if ( n > 0 )
			return sum/n;
		else
			return 0;
	}
	
	/*
	 * get pixel-value correlation of two RandomAccessibleIntervals
	 * @param img1
	 * @param img2
	 * @return
	 */
	public static <T extends RealType<T>, S extends RealType<S>> double getCorrelation (
			final RandomAccessibleInterval<T> img1, final RandomAccessibleInterval<S> img2, final long minOverlapPx)
	{
		// square sums
		double sum11 = 0.0, sum22 = 0.0, sum12 = 0.0; 

		Cursor<T> c1 = Views.iterable(img1).localizingCursor();
		RandomAccess<S> r2 = img2.randomAccess();

		long n = 0;

		CompensatedSum sum1 = new CompensatedSum();
		CompensatedSum sum2 = new CompensatedSum();

		while ( c1.hasNext() )
		{
			final double c = c1.next().getRealDouble();
			r2.setPosition(c1);
			final double r = r2.get().getRealDouble();

			if ( c > 0 && r > 0 )
			{
				sum1.add( c );
				sum2.add( r );
				++n;
			}
		}

		if ( n < Math.max( 1, minOverlapPx ) )
			return 0;

		final double m1 = sum1.getSum() / n;
		final double m2 = sum1.getSum() / n;

		c1 = Views.iterable(img1).localizingCursor();

		while (c1.hasNext())
		{
			final double c = c1.next().getRealDouble();
			r2.setPosition(c1);
			final double r = r2.get().getRealDouble();

			if ( c > 0 && r > 0 )
			{
				sum11 += (c - m1) * (c - m1);
				sum22 += (r - m2) * (r - m2);
				sum12 += (c - m1) * (r - m2);
			}
		}

		// all pixels had the same color....
		if (sum11 == 0 || sum22 == 0)
		{
			// having the same means and same sums means the overlapping area was simply identically the same color
			// this is most likely an artifact and we return 0
			/* if ( sum11 == sum22 && m1 == m2 )
				return 1;
			else */
			return 0;
		}

		return sum12 / Math.sqrt(sum11 * sum22);
	}


	public static <T extends RealType<T>, S extends RealType<S>> Pair< RandomAccessibleInterval<FloatType>, RandomAccessibleInterval<FloatType>> dummyFuse(
			RandomAccessibleInterval<T> img1, RandomAccessibleInterval<S> img2, PhaseCorrelationPeak2 shiftPeak, ExecutorService service)
	{
		return dummyFuse( img1, img2, shiftPeak.getShift(), service );
	}

	public static <T extends RealType<T>, S extends RealType<S>> Pair< RandomAccessibleInterval<FloatType>, RandomAccessibleInterval<FloatType>> dummyFuse(
			RandomAccessibleInterval<T> img1, RandomAccessibleInterval<S> img2, Localizable shiftPeak, ExecutorService service)
	{	
		long[] shift = new long[img1.numDimensions()];
		shiftPeak.localize(shift);
		long[] minImg1 = new long[img1.numDimensions()];
		long[] minImg2 = new long[img1.numDimensions()];
		long[] maxImg1 = new long[img1.numDimensions()];
		long[] maxImg2 = new long[img1.numDimensions()];
		long[] min = new long[img1.numDimensions()];
		long[] max = new long[img1.numDimensions()];

		for (int i = 0; i < img1.numDimensions(); i++){
			minImg1[i] = 0;
			maxImg1[i] = img1.dimension(i) -1;
			minImg2[i] = shiftPeak.getLongPosition(i);
			maxImg2[i] = img2.dimension(i) + minImg2[i] - 1;

			min[i] =  Math.min(minImg1[i], minImg2[i]);
			max[i] = Math.max(maxImg1[i], maxImg2[i]);
		}

		RandomAccessibleInterval<FloatType> res1 = new ArrayImgFactory<>(new FloatType()).create(new FinalInterval(min, max));
		RandomAccessibleInterval<FloatType> res2 = new ArrayImgFactory<>(new FloatType()).create(new FinalInterval(min, max));

		copyRealImage(Views.iterable(img1), Views.translate(res1, min), service);
		copyRealImage(Views.iterable(Views.translate(img2, shift)), Views.translate(res2, min), service);
		return new ValuePair<>( res1, res2 );
	}

	public static < C extends ComplexType< C >, T extends RealType< T > & NativeType< T > > Img< T > computePolarPowerSpectrum( final RandomAccessibleInterval< C > fft, final T type )
	{
		if ( fft.numDimensions() != 2 )
			throw new RuntimeException( "Only dim=2 allowed." );

		final RandomAccessibleInterval< T > power = Converters.convertRAI( fft, new ComplexPowerGLogRealConverter<>(), type );

		final Img< T > polarImg = new ArrayImgFactory<>(type).create(360, Math.min(power.dimension(0 ), power.dimension(1 ) ) ); //ArrayImgs.floats( 360, Math.min( power.dimension( 0 ), power.dimension( 1 ) ) );

		final PolarToCartesianTransform2D polarTransform = new PolarToCartesianTransform2D();

		// we need to mirror in x, then extend periodic
		final long[] min = new long[ 2 ];
		final long[] max = new long[ 2 ];

		power.max( max );
		min[ 0 ] = -max[ 0 ];

		final RealRandomAccess< T > in = Views.interpolate( Views.extendPeriodic( Views.interval( Views.extendMirrorSingle( power ), min, max ) ), new NLinearInterpolatorFactory<>() ).realRandomAccess();
		final Cursor< T > cursor = polarImg.localizingCursor();

		final double[] source = new double[ 2 ];

		while ( cursor.hasNext() )
		{
			final T t = cursor.next();

			source[ 0 ] = cursor.getIntPosition( 1 );
			source[ 1 ] = Math.toRadians( cursor.getIntPosition( 0 ) );

			polarTransform.apply( source, source );

			in.setPosition( source );
			t.set( in.get() );
		}

		return polarImg;
	}

	public static void main(String[] args)
	{
		new ImageJ();

		Img<FloatType> img1 = ImgLib2Util.openAs32Bit(new File("src/main/resources/img1.tif"));
		Img<FloatType> img2 = ImgLib2Util.openAs32Bit(new File("src/main/resources/img2.tif"));

		ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

		int [] extension = new int[img1.numDimensions()];
		Arrays.fill(extension, 10);
		Dimensions extSize = PhaseCorrelation2Util.getExtendedSize(img1, img2, extension);
		long[] paddedDimensions = new long[extSize.numDimensions()];
		long[] fftSize = new long[extSize.numDimensions()];
		FFTMethods.dimensionsRealToComplexFast(extSize, paddedDimensions, fftSize);

		final ImgFactory<ComplexFloatType> fftFactory = new ArrayImgFactory<>(new ComplexFloatType());
		RandomAccessibleInterval<ComplexFloatType> fft1 = fftFactory.create(fftSize);
		RandomAccessibleInterval<ComplexFloatType> fft2 = fftFactory.create(fftSize);

		FFT.realToComplex(Views.interval(PhaseCorrelation2Util.extendImageByFactor(img1, extension), 
				FFTMethods.paddingIntervalCentered(img1, new FinalInterval(paddedDimensions))), fft1, service);
		FFT.realToComplex(Views.interval(PhaseCorrelation2Util.extendImageByFactor(img2, extension), 
				FFTMethods.paddingIntervalCentered(img2, new FinalInterval(paddedDimensions))), fft2, service);

		final RandomAccessibleInterval< FloatType > polar1 =  computePolarPowerSpectrum( fft1, new FloatType() );
		final RandomAccessibleInterval< FloatType > polar2 =  computePolarPowerSpectrum( fft2, new FloatType() );

		ImageJFunctions.show( polar1 );
		ImageJFunctions.show( polar2 );
	}
}
