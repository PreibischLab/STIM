package io;

public class Path
{
	private Path() {}

	public static String getPath()
	{
		if ( !System.getProperty("os.name").toLowerCase().contains( "mac" ) )
			return "/home/stephan/Downloads/";
		else
			return "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/";
	}
}
