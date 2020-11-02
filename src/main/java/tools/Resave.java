package tools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import data.STData;
import io.JsonIO;
import io.N5IO;
import io.Path;

public class Resave
{
	public static void main( String[] args ) throws IOException, InterruptedException, ExecutionException
	{
		final String path = Path.getPath();
		final String[] pucks = new String[] { "Puck_180602_20", "Puck_180602_18", "Puck_180602_17", "Puck_180602_16", "Puck_180602_15", "Puck_180531_23", "Puck_180531_22", "Puck_180531_19", "Puck_180531_18", "Puck_180531_17", "Puck_180531_13", "Puck_180528_22", "Puck_180528_20" };

		for ( final String puck : pucks )
		{
			System.out.println( new File( path + "slide-seq/" + puck + "-normalized.n5" ).getAbsolutePath() );

			final STData data = N5IO.readN5( new File( path + "slide-seq/" + puck + "-normalized.n5" ) );
			//final STData data = STDataUtils.createTestDataSet();
			//final STData data = JsonIO.readJSON( new File( Path.getPath() + "patterns_examples_2d/small.json.zip" ) );

			N5IO.writeN5(data, new File( path + "slide-seq/" + puck + "-normalized-512.n5" ) );

		}


	}
}
