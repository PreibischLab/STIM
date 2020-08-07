package align;

import java.io.File;
import java.io.IOException;

import data.STData;
import data.STDataStatistics;
import data.STDataUtils;
import ij.ImageJ;
import imglib2.ImgLib2Util;
import io.N5IO;
import io.Path;
import net.imglib2.Interval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.util.Intervals;

public class GlobalOpt
{
	public static void visualizePair( final STData stDataA, final STData stDataB )
	{
		final AffineTransform2D pcmTransform = new AffineTransform2D();
		pcmTransform.set( 0.43837114678907746, -0.8987940462991671, 5283.362652306015, 0.8987940462991671, 0.43837114678907746, -770.4745037840293 );

		final Interval interval = STDataUtils.getCommonInterval( stDataA, stDataB );

		// visualize result using the global transform
		final AffineTransform2D tA = new AffineTransform2D();
		tA.scale( 0.1 );

		final AffineTransform2D tB_PCM = pcmTransform.copy();
		tB_PCM.preConcatenate( tA );

		final Interval finalInterval = Intervals.expand( ImgLib2Util.transformInterval( interval, tA ), 100 );

		new ImageJ();

		ImageJFunctions.show( Pairwise.display( stDataA, new STDataStatistics( stDataA ), "Calm1", finalInterval, tA ) );
		ImageJFunctions.show( Pairwise.display( stDataB, new STDataStatistics( stDataB ), "Calm1", finalInterval, tB_PCM ) ).setTitle( "Calm1-PCM" );
	}

	public static void main( String[] args ) throws IOException
	{
		final String path = Path.getPath();

		final String[] pucks = new String[] { "Puck_180602_20", "Puck_180602_18", "Puck_180602_17", "Puck_180602_16", "Puck_180602_15", "Puck_180531_23", "Puck_180531_22", "Puck_180531_19", "Puck_180531_18", "Puck_180531_17", "Puck_180531_13", "Puck_180528_22", "Puck_180528_20" };

		final STData stDataA = N5IO.readN5( new File( path + "slide-seq/" + pucks[ 0 ] + "-normalized.n5" ) );
		final STData stDataB = N5IO.readN5( new File( path + "slide-seq/" + pucks[ 4 ] + "-normalized.n5" ) );

		visualizePair( stDataA, stDataB );
	}
}
