package util;

import java.net.URI;
import java.util.regex.Pattern;

import org.janelia.saalfeldlab.googlecloud.GoogleCloudUtils;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.s3.AmazonS3Utils;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.N5Factory.StorageFormat;
import org.janelia.saalfeldlab.n5.zarr.N5ZarrReader;
import org.janelia.saalfeldlab.n5.zarr.N5ZarrWriter;

/**
 * Note: The entire class is a copy from multiview-reconstruction URITools.java
 */
public class Cloud
{
	private final static Pattern HTTPS_SCHEME = Pattern.compile( "http(s)?", Pattern.CASE_INSENSITIVE );
	private final static Pattern FILE_SCHEME = Pattern.compile( "file", Pattern.CASE_INSENSITIVE );

	public static int cloudThreads = 256;

	public static boolean useS3CredentialsWrite = true;
	public static boolean useS3CredentialsRead = true;

	public static N5Writer instantiateN5Writer( final StorageFormat format, final URI uri )
	{
		if ( isFile( uri ) )
		{
			if ( format.equals( StorageFormat.N5 ))
				return new N5FSWriter( removeFilePrefix( uri ) );
			else if ( format.equals( StorageFormat.ZARR ))
				return new N5ZarrWriter( removeFilePrefix( uri ) );
			else if ( format.equals( StorageFormat.HDF5 ))
				return new N5HDF5Writer( removeFilePrefix( uri ) );
			else
				throw new RuntimeException( "Format: " + format + " not supported." );
		}
		else
		{
			N5Writer n5w;

			try
			{
				System.out.println( "Trying writing with credentials ..." );
				N5Factory factory = new N5Factory();
				factory.s3UseCredentials();
				n5w = factory.openWriter( format, uri );
			}
			catch ( Exception e )
			{
				System.out.println( "With credentials failed; trying anonymous ..." );

				n5w = new N5Factory().openWriter( format, uri );
			}

			return n5w;
			//return new N5Factory().openWriter( format, uri ); // cloud support, avoid dependency hell if it is a local file
		}
	}

	public static N5Reader instantiateN5Reader( final StorageFormat format, final URI uri )
	{
		if ( isFile( uri ) )
		{
			if ( format.equals( StorageFormat.N5 ))
				return new N5FSReader( removeFilePrefix( uri ) );
			else if ( format.equals( StorageFormat.ZARR ))
				return new N5ZarrReader( removeFilePrefix( uri ) );
			else
				throw new RuntimeException( "Format: " + format + " not supported." );
		}
		else
		{
			N5Reader n5r;

			try
			{
				System.out.println( "Trying reading with credentials ..." );
				N5Factory factory = new N5Factory();
				factory.s3UseCredentials();
				n5r = factory.openReader( format, uri );
			}
			catch ( Exception e )
			{
				System.out.println( "With credentials failed; trying anonymous ..." );
				n5r = new N5Factory().openReader( format, uri );
			}

			return n5r;
			//return new N5Factory().openReader( format, uri ); // cloud support, avoid dependency hell if it is a local file
		}
	}

	public static boolean isGC( URI uri )
	{
		final String scheme = uri.getScheme();
		final boolean hasScheme = scheme != null;
		if ( !hasScheme )
			return false;
		if ( GoogleCloudUtils.GS_SCHEME.asPredicate().test( scheme ) )
			return true;
		return uri.getHost() != null && HTTPS_SCHEME.asPredicate().test( scheme ) && GoogleCloudUtils.GS_HOST.asPredicate().test( uri.getHost() );
	}

	public static boolean isS3( URI uri )
	{
		final String scheme = uri.getScheme();
		final boolean hasScheme = scheme != null;
		if ( !hasScheme )
			return false;
		if ( AmazonS3Utils.S3_SCHEME.asPredicate().test( scheme ) )
			return true;
		return uri.getHost() != null && HTTPS_SCHEME.asPredicate().test( scheme );
	}

	public static boolean isFile( URI uri )
	{
		final String scheme = uri.getScheme();
		final boolean hasScheme = scheme != null;
		return !hasScheme || FILE_SCHEME.asPredicate().test( scheme );
	}

	public static String removeFilePrefix( URI uri )
	{
		final String scheme = uri.getScheme();
		final boolean hasScheme = scheme != null;

		if ( hasScheme && FILE_SCHEME.asPredicate().test( scheme ) )
			return uri.toString().substring( 5, uri.toString().length() ); // cut off 'file:'
		else
			return uri.toString();
	}

}
