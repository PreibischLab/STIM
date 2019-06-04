package importer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import com.google.gson.JsonIOException;

import data.STData;
import io.JsonIO;

public class ConvertToJson
{
	public static void convertDrosophila( final boolean zip ) throws JsonIOException, UnsupportedEncodingException, FileNotFoundException, IOException
	{
		final STData stdata = Parser.read(
				new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/fly_3d_data/geometry.txt" ),
				new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/fly_3d_data/sdge_1297_cells_3039_locations_84_markers.txt" ),
				new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/fly_3d_data/gene_names.txt" ) );

		final File out = new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/fly_3d_data/stdata_fly3d" + ( zip ? ".json.zip" : ".json" ) );

		System.out.println( "Saving " + out.getAbsolutePath() + " ... " );
		long time = System.currentTimeMillis();

		JsonIO.writeAsJSON( stdata, out, zip );

		System.out.println( "Saving took " + ( System.currentTimeMillis() - time ) + " ms." );
	}

	public static void convertSlideSeqSmallCut( final boolean zip ) throws JsonIOException, UnsupportedEncodingException, FileNotFoundException, IOException
	{
		final STData stdata = Parser.read(
				new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/patterns_examples_2d/locations.txt" ),
				new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/patterns_examples_2d/dge_normalized_cut.txt" ) );

		final File out = new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/patterns_examples_2d/small_cut" + ( zip ? ".json.zip" : ".json" ) );

		System.out.println( "Saving " + out.getAbsolutePath() + " ... " );
		long time = System.currentTimeMillis();

		JsonIO.writeAsJSON( stdata, out, zip );

		System.out.println( "Saving took " + ( System.currentTimeMillis() - time ) + " ms." );
	}

	public static void convertSlideSeqSmall( final boolean zip ) throws JsonIOException, UnsupportedEncodingException, FileNotFoundException, IOException
	{
		final STData stdata = Parser.read(
				new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/patterns_examples_2d/locations.txt" ),
				new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/patterns_examples_2d/dge_normalized.txt" ) );

		final File out = new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/patterns_examples_2d/small" + ( zip ? ".json.zip" : ".json" ) );

		System.out.println( "Saving " + out.getAbsolutePath() + " ... " );
		long time = System.currentTimeMillis();

		JsonIO.writeAsJSON( stdata, out, zip );

		System.out.println( "Saving took " + ( System.currentTimeMillis() - time ) + " ms." );
	}

	public static void convertSlideSeq( final boolean zip ) throws JsonIOException, UnsupportedEncodingException, FileNotFoundException, IOException
	{
		final STData stdata = Parser.read(
				new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/patterns_examples_2d/full_locations.txt" ),
				new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/patterns_examples_2d/full_dge_normalized.txt" ) );

		final File out = new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/patterns_examples_2d/full" + ( zip ? ".json.zip" : ".json" ) );

		System.out.println( "Saving " + out.getAbsolutePath() + " ... " );
		long time = System.currentTimeMillis();

		JsonIO.writeAsJSON( stdata, out, zip );

		System.out.println( "Saving took " + ( System.currentTimeMillis() - time ) + " ms." );
	}

	public static void main( String[] args ) throws JsonIOException, UnsupportedEncodingException, FileNotFoundException, IOException
	{
		//convertDrosophila( true );
		//convertDrosophila( false );

		//convertSlideSeqCut( true );
		//convertSlideSeqCut( false );

		//convertSlideSeqSmall( true );
		//convertSlideSeqSmall( false );

		//convertSlideSeq( true );
		//convertSlideSeq( false );
	}
}
