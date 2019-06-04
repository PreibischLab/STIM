package io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;

import data.STData;
import data.STDataMinimal;
import importer.Parser;
import net.imglib2.util.Util;

public class JsonIO
{
	public static void writeAsJSON(
			final STData stdata,
			final File file,
			final boolean zip ) throws UnsupportedEncodingException, FileNotFoundException, IOException, JsonIOException
	{
		final Writer writer;

		if ( zip )
		{
			writer =
				new OutputStreamWriter(
						new BufferedOutputStream(
								new GZIPOutputStream(
										new FileOutputStream( file.getAbsolutePath() ))), "UTF-8");
		}
		else
		{
			writer =
					new OutputStreamWriter(
							new BufferedOutputStream(
									new FileOutputStream( file.getAbsolutePath() )), "UTF-8");
		}

		final GsonBuilder gsonBuilder = new GsonBuilder();
		final Gson gson = gsonBuilder.create();

		gson.toJson( new STDataMinimal( stdata.coordinates, stdata.genes ), writer );

		writer.close();
	}

	public static STData readJSON( final File file ) throws IOException
	{
		final STDataMinimal data = readJSONMinimal( file );

		if ( data == null )
			return null;
		else
			return new STData( data );
	}

	public static STDataMinimal readJSONMinimal( final File file ) throws IOException
	{
		final Reader reader;
		final BufferedInputStream in =
				new BufferedInputStream(
						new FileInputStream( file.getAbsolutePath() ) );

		if ( isGZip( in ) )
		{
			reader =
					new InputStreamReader(
							new GZIPInputStream( in ), "UTF-8");
		}
		else
		{
			reader =
					new InputStreamReader( in, "UTF-8");
		}

		final Gson gson = new GsonBuilder().create();
		final STDataMinimal data = gson.fromJson( reader, STDataMinimal.class );

		return data;
	}

	public static boolean isGZip( final BufferedInputStream in )
	{
		try
		{
			in.mark( 2 );

			final int magic = in.read() & 0xff | ( ( in.read() << 8 ) & 0xff00 );

			in.reset();

			return magic == GZIPInputStream.GZIP_MAGIC;
		}
		catch ( IOException e )
		{
			e.printStackTrace( System.err );
			return false;
		}
	}

	public static void main( String[] args ) throws IOException
	{
		STData data = STData.createTestDataSet();

		final File jsonFile = new File( "data.json" );
		writeAsJSON( data, jsonFile, false );

		data = new STData( readJSON( jsonFile ) );

		data.printInfo();

		for ( final double[] coord : data.coordinates )
			System.out.println( Util.printCoordinates( coord ) );

		for ( final String gene : data.genes.keySet() )
			System.out.println( gene + ": " + Util.printCoordinates( data.genes.get( gene ) ) );
	}
}
