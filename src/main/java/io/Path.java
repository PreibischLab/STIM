package io;

public class Path
{
	private Path() {}

	public static String getPath()
	{
		if ( !System.getProperty("os.name").toLowerCase().contains( "mac" ) )
			return "/home/innerbergerm@hhmi.org/Projects/janelia/stim/data/";//"/groups/scicompsoft/home/preibischs/";
		else
			return "/Users/preibischs/Documents/BIMSB/Publications/imglib2-st/";
	}
}
