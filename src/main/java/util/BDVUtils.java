package util;

import java.util.ArrayList;
import java.util.List;

import bdv.tools.transformation.TransformedSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerState;

public class BDVUtils
{
	public static ArrayList< TransformedSource< ? > > getTransformedSources( final ViewerState state )
	{
		final List< ? extends SourceAndConverter< ? > > sourceList;
		synchronized ( state )
		{
			sourceList = new ArrayList<>( state.getSources() );
		}

		final ArrayList< TransformedSource< ? > > list = new ArrayList<>();
		for ( final SourceAndConverter< ? > soc : sourceList )
		{
			final Source< ? > source = soc.getSpimSource();
			if ( source instanceof TransformedSource )
				list.add( (TransformedSource< ? > ) source );
		}
		return list;
	}

}
