package align;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import analyze.ExtractGeneLists;
import data.STData;
import data.STDataStatistics;
import filter.GaussianFilterFactory;
import filter.GaussianFilterFactory.WeightType;
import ij.ImageJ;
import imglib2.ImgLib2Util;
import imglib2.phasecorrelation.PhaseCorrelation2;
import imglib2.phasecorrelation.PhaseCorrelation2Util;
import imglib2.phasecorrelation.PhaseCorrelationPeak2;
import io.N5IO;
import io.Path;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.complex.ComplexDoubleType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import render.Render;
import util.Threads;

public class Pairwise
{
	public static void align( final STData stdataA, final STData stdataB )
	{
		final STDataStatistics statA = new STDataStatistics( stdataA );
		final STDataStatistics statB = new STDataStatistics( stdataB );

		final int topG = 500;
		final int topN = 5;
/*
		final ArrayList< Pair< String, Double > > listA = ExtractGeneLists.sortByStDevIntensity( stdataA );
		final ArrayList< Pair< String, Double > > listB = ExtractGeneLists.sortByStDevIntensity( stdataB );

		final ArrayList< String > genesA = new ArrayList<>();
		final ArrayList< String > genesB = new ArrayList<>();

		for ( int i = 0; i < topG; ++i )
		{
			genesA.add( listA.get( i ).getA() );
			genesB.add( listB.get( i ).getA() );
		}

		final List< String > genes = commonGeneNames( genesA, genesB );
		System.out.println( "testing " + genes.size() + " genes." );

*/
	
		final List< String > genesToTest = new ArrayList<>();
		genesToTest.add( "Hpca" );
		genesToTest.add( "Fth1" );
		genesToTest.add( "Ubb" );
		genesToTest.add( "Pcp4" );
		// TODO: select top N by stdev, get common ones

		//final String gene = "Hpca";//"Fth1";//"Ubb";//"Pcp4";

		final ExecutorService service = Threads.createFixedExecutorService();

		final AffineTransform2D scalingTransform = new AffineTransform2D();
		scalingTransform.scale( 0.025 );

		final Interval interval = getCommonInterval( stdataA, stdataB );

		final double[] histogram = new double[ 360 ];

		for ( final String gene : genesToTest )
		{
			final List< Pair< PhaseCorrelationPeak2, Double > > alignParamList = alignPairwise( stdataA, statA, stdataB, statB, gene, topN, scalingTransform, interval, service );

			for ( final Pair< PhaseCorrelationPeak2, Double > alignParam : alignParamList )
				histogram[ (int)Math.round( alignParam.getB() ) ] += alignParam.getA().getCrossCorr();

			final Pair< PhaseCorrelationPeak2, Double > alignParams = alignParamList.get( 0 );
			System.out.println( "TOP ("+gene+"): " + alignParams.getB() + ", " + alignParams.getA().getCrossCorr() + ", " + Util.printCoordinates( alignParams.getA().getShift() ) );
	
			/*
			// visualize result
			final AffineTransform2D transformB = scalingTransform.copy();
			transformB.rotate( Math.toRadians( alignParams.getB() ) );
			final RandomAccessibleInterval< DoubleType > imgB = Views.zeroMin( display( stdataB, statB, gene, transformB, interval ) );
			new ImageJ();
	
			Pair< RandomAccessibleInterval<FloatType>, RandomAccessibleInterval<FloatType>> res = PhaseCorrelation2Util.dummyFuse( Views.zeroMin( display( stdataA, statA, gene, scalingTransform, interval ) ), imgB, alignParams.getA(), service);
			ImageJFunctions.show(res.getA());
			ImageJFunctions.show(res.getB());
			*/
		}

		System.out.println( );

		for ( int i = 0; i < histogram.length; ++i )
		{
			System.out.println( i + "\t" + histogram[ i ] );
		}

		service.shutdown();

	}

	public static List< Pair< PhaseCorrelationPeak2, Double > > alignPairwise( final STData stdataA, final STDataStatistics statA, final STData stdataB, final STDataStatistics statB, final String gene, final int topN, final AffineTransform2D scalingTransform,  final Interval interval, final ExecutorService service )
	{
		final ArrayList< Pair< PhaseCorrelationPeak2, Double > > topPeaks = new ArrayList<>();

		final AffineTransform2D transformA = scalingTransform.copy();
		final RandomAccessibleInterval< DoubleType > imgA = ImgLib2Util.copyImg( Views.zeroMin( display( stdataA, statA, gene, transformA, interval ) ), new ArrayImgFactory<>( new DoubleType() ), service );

		// initial scouting
		System.out.println( "Scouting: " + gene );

		for ( int deg = 0; deg < 360; deg += 3 )
		{
			final PhaseCorrelationPeak2 shiftPeak = test( imgA, stdataB, statB, gene, interval, deg, scalingTransform, service );
			insertIntoList( topPeaks, topN, shiftPeak, deg );
			//System.out.println( deg + ": " + shiftPeak.getCrossCorr() + ", " + Util.printCoordinates( shiftPeak.getShift() ) );
		}

		// gradient descent for every inital Peak
		System.out.println( "Gradient descent: " + gene );

		final ArrayList< Pair< PhaseCorrelationPeak2, Double > > revisedTopPeaks = new ArrayList<>();

		for ( final Pair< PhaseCorrelationPeak2, Double > peak : topPeaks )
		{
			Pair< PhaseCorrelationPeak2, Double > bestPeak = peak;

			for ( double step = 2; step > 0.001; step /= 1.5 )
			{
				boolean updated;

				do
				{
					updated = false;
					double deg = bestPeak.getB();
					
	
					PhaseCorrelationPeak2 shiftPeakA = test( imgA, stdataB, statB, gene, interval, deg + step, scalingTransform, service );
	
					if ( shiftPeakA.getCrossCorr() > bestPeak.getA().getCrossCorr() )
					{
						updated = true;
						bestPeak = new ValuePair< PhaseCorrelationPeak2, Double >( shiftPeakA, deg + step );
					}

					PhaseCorrelationPeak2 shiftPeakB = test( imgA, stdataB, statB, gene, interval, deg - step, scalingTransform, service );

					if ( shiftPeakB.getCrossCorr() > bestPeak.getA().getCrossCorr() )
					{
						updated = true;
						bestPeak = new ValuePair< PhaseCorrelationPeak2, Double >( shiftPeakB, deg - step );
					}
				} while ( updated );
			}

			insertIntoList( revisedTopPeaks, topN, bestPeak.getA(), bestPeak.getB() );
		}

		//for ( int i = 0; i < revisedTopPeaks.size(); ++i )
		//	System.out.println( "TOP " + i + " ("+gene+"): " + revisedTopPeaks.get( i ).getB() + ", " + revisedTopPeaks.get( i ).getA().getCrossCorr() + ", " + Util.printCoordinates( revisedTopPeaks.get( i ).getA().getShift() ) );

		return revisedTopPeaks;
	}

	public static void insertIntoList( final List< Pair< PhaseCorrelationPeak2, Double > > topPeaks, final int topN, final PhaseCorrelationPeak2 newPeak, final double deg )
	{
		if ( topPeaks.size() == 0 )
			topPeaks.add( new ValuePair< PhaseCorrelationPeak2, Double >( newPeak, deg ) );
		else
		{
			boolean inserted = false;
			for ( int i = 0; i < Math.min( topN, topPeaks.size() ); ++i )
			{
				if ( newPeak.getCrossCorr() > topPeaks.get( i ).getA().getCrossCorr() )
				{
					topPeaks.add( i, new ValuePair< PhaseCorrelationPeak2, Double >( newPeak, deg ) );

					if ( topPeaks.size() >= topN )
						topPeaks.remove( topPeaks.size() - 1 );

					inserted = true;
					break;
				}
			}

			if ( !inserted && topPeaks.size() < topN )
				topPeaks.add( new ValuePair< PhaseCorrelationPeak2, Double >( newPeak, (double)deg ) );
		}
	}

	public static PhaseCorrelationPeak2 test( final RandomAccessibleInterval< DoubleType > imgA, final STData stdata, final STDataStatistics stat, final String gene, final Interval interval, final double degrees, final AffineTransform2D initialTransform, final ExecutorService service  )
	{
		final AffineTransform2D transformB = initialTransform.copy();
		transformB.rotate( Math.toRadians( degrees ) );

		final RandomAccessibleInterval< DoubleType > imgB = Views.zeroMin( display( stdata, stat, gene, transformB, interval ) );

		//ImageJFunctions.show( imgA, service ).setTitle( stdataA.toString() );
		//ImageJFunctions.show( imgB, service ).setTitle( stdataB.toString() + " @ " + i + " deg"  );

		RandomAccessibleInterval<DoubleType> pcm = PhaseCorrelation2.calculatePCM(
				imgA, imgB,
				new ArrayImgFactory<DoubleType>( new DoubleType() ),
				new ArrayImgFactory<ComplexDoubleType>( new ComplexDoubleType() ),
				service );

		//List<PhaseCorrelationPeak2> peaks = PhaseCorrelation2Util.getPCMMaxima(pcm, service, 5, false );
		//for ( final PhaseCorrelationPeak2 p : peaks )
		//	System.out.println( "PCM: " + p.getPhaseCorr() + ", " + Util.printCoordinates( p.getPcmLocation() ) );

		return PhaseCorrelation2.getShift(pcm, imgA, imgB, 5, 10000, false, false, service);// Threads.createFixedExecutorService( 1 ));
	}

	public static RandomAccessibleInterval< DoubleType > display(
			final STData stdata,
			final STDataStatistics stStats,
			final String gene,
			final AffineTransform2D transform,
			final Interval commonInterval )
	{
		final double medianDistance = stStats.getMedianDistance() * 1.0;

		final Interval interval = ImgLib2Util.transformInterval( commonInterval == null ? stdata.getRenderInterval() : commonInterval, transform );

		//System.out.println( "Mean distance: " + stStats.getMeanDistance());
		//System.out.println( "Median distance: " + stStats.getMedianDistance() );
		//System.out.println( "Max distance: " + stStats.getMaxDistance() );

		// gauss crisp
		double gaussRenderSigma = stStats.getMedianDistance();

		final DoubleType outofbounds = new DoubleType( 0 );

		IterableRealInterval< DoubleType > data = stdata.getExprData( gene );

		data = Converters.convert(
				data,
				new Converter< DoubleType, DoubleType >()
				{
					@Override
					public void convert( final DoubleType input, final DoubleType output )
					{
						output.set( input.get() + 0.1 );
						
					}
				},
				new DoubleType() );

		// TODO: this might all make more sense after normalization now, yay!

		//data = TransformCoordinates.sample( data, stStats.getMedianDistance() );

		//data = Filters.filter( data, new DensityFilterFactory<>( new DoubleType(), medianDistance ) );
		//data = Filters.filter( data, new MedianFilterFactory<>( outofbounds, medianDistance * 2 ) );
		//data = Filters.filter( data, new MeanFilterFactory<>( outofbounds, medianDistance * 10 ) );
		//data = Filters.filter( data, new GaussianFilterFactory<>( outofbounds, medianDistance * 2, stStats.getMedianDistance(), true ) );

		final Pair< DoubleType, DoubleType > minmax = ImgLib2Util.minmax( data );

		//System.out.println( "Min intensity: " + minmax.getA() );
		//System.out.println( "Max intensity: " + minmax.getB() );

		// for rendering the input pointcloud
		final RealRandomAccessible< DoubleType > renderRRA = Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderSigma, WeightType.NONE ) );

		// for rendering a 16x (median distance), regular sampled pointcloud
		//final RealRandomAccessible< DoubleType > renderRRA = Render.render( data, new GaussianFilterFactory<>( outofbounds, stStats.getMedianDistance() / 4.0, WeightType.NONE ) );

		//BdvOptions options = BdvOptions.options().is2D().numRenderingThreads( Runtime.getRuntime().availableProcessors() );
		//BdvStackSource< ? > bdv = BdvFunctions.show( renderRRA, stdata.getRenderInterval(), gene, options );
		//bdv.setDisplayRange( 0.1, minmax.getB().get() * 2 );
		//bdv.setDisplayRangeBounds( 0, minmax.getB().get() * 8 );

		//System.out.println( new Date(System.currentTimeMillis()) + ": Rendering interval " + Util.printInterval( interval ) + " with " + Threads.numThreads() + " threads ... " );
		
		final RandomAccessibleInterval< DoubleType > rendered = Views.interval( RealViews.affine( renderRRA, transform ), interval );
		//ImageJFunctions.show( rendered, Threads.createFixedExecutorService() ).setTitle( stdata.toString() );
		//System.out.println( new Date(System.currentTimeMillis()) + ": Done..." );

		return rendered;
	}

	public static List< String > commonGeneNames( final List< String > names0, final List< String > names1 )
	{
		final ArrayList< String > commonGeneNames = new ArrayList<>();
		final HashSet< String > names0Hash = new HashSet<>( names0 );

		for ( final String name1 : names1 )
			if ( names0Hash.contains( name1 ) )
				commonGeneNames.add( name1 );

		return commonGeneNames;
	}

	public static Interval getCommonInterval( final STData stDataA, final STData stDataB )
	{
		final ArrayList< STData > list = new ArrayList<>();
		list.add( stDataA );
		list.add( stDataB );

		return getCommonInterval( list );
	}

	public static Interval getCommonInterval( final Collection< STData > slides )
	{
		long[] min = null, max = null;

		for ( final STData slide : slides )
		{
			if ( min == null )
			{
				min = new long[ slide.getRenderInterval().numDimensions() ];
				max = new long[ slide.getRenderInterval().numDimensions() ];
				
				slide.getRenderInterval().min( min );
				slide.getRenderInterval().max( max );
			}
			else
			{
				for ( int d = 0; d < min.length; ++d )
				{
					min[ d ] = Math.min( min[ d ], slide.getRenderInterval().min( d ) );
					max[ d ] = Math.max( max[ d ], slide.getRenderInterval().max( d ) );
				}
			}
		}

		if ( min != null )
			return new FinalInterval( min, max );
		else
			return null;
	}

	public static void main( String[] args ) throws IOException
	{
		final String path = Path.getPath();

		//final String[] pucks = new String[] { "Puck_180602_20", "Puck_180602_18", "Puck_180602_17", "Puck_180602_16", "Puck_180602_15", "Puck_180531_23", "Puck_180531_22", "Puck_180531_19", "Puck_180531_18", "Puck_180531_17", "Puck_180531_13", "Puck_180528_22", "Puck_180528_20" };
		//final String[] pucks = new String[] { "Puck_180531_23" };
		final String[] pucks = new String[] { "Puck_180531_23", "Puck_180531_22" };
		//final String[] pucks = new String[] { "Puck_180531_18", "Puck_180531_17" };


		align(
				N5IO.readN5( new File( path + "slide-seq/" + pucks[ 0 ] + "-normalized.n5" ) ),
				N5IO.readN5( new File( path + "slide-seq/" + pucks[ 1 ] + "-normalized.n5" ) ) );

		SimpleMultiThreading.threadHaltUnClean();

		final ArrayList< Pair< STData, STDataStatistics > > slides = new ArrayList<>();

		for ( final String puck : pucks )
		{
			final STData slide = /*new NormalizingSTData*/( N5IO.readN5( new File( path + "slide-seq/" + puck + "-normalized.n5" ) ) );//.copy();
			final STDataStatistics stat = new STDataStatistics( slide );

			slides.add(  new ValuePair<>( slide, stat ) );
		}

		final Interval interval = getCommonInterval( slides.stream().map( pair -> pair.getA() ).collect( Collectors.toList() ) );

		new ImageJ();

		final AffineTransform2D transform = new AffineTransform2D();
		transform.scale( 0.25 );

		for ( final Pair< STData, STDataStatistics > slide : slides )
		{
			display( slide.getA(), slide.getB(), "Pcp4", transform, interval );
			SimpleMultiThreading.threadHaltUnClean();
		}

		/*
		final STData slide0 = N5IO.readN5( new File( path + "slide-seq/Puck_180531_22.n5" ) );
		final STData slide1 = N5IO.readN5( new File( path + "slide-seq/Puck_180531_23.n5" ) );

		System.out.println( "Genes 0: " + slide0.getGeneNames().size() );
		System.out.println( "Genes 1: " + slide1.getGeneNames().size() );

		List< String > commonGeneNames = commonGeneNames( slide0.getGeneNames(), slide1.getGeneNames() );

		System.out.println( "common genes: " + commonGeneNames.size() );

		final STDataStatistics stat0 = new STDataStatistics( slide0 );
		final STDataStatistics stat1 = new STDataStatistics( slide1 );

		new ImageJ();
		display( slide0, stat0, "Pcp4" );
		display( slide1, stat1, "Pcp4" );
		*/
	}
}
