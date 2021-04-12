package data;

import java.io.File;
import java.util.HashMap;
import java.util.List;

import org.janelia.saalfeldlab.n5.N5FSReader;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.real.DoubleType;

public class STDataN5 extends STDataImgLib2
{
	N5FSReader n5Reader;
	File n5path;
	String datasetName;

	public STDataN5(
			final RandomAccessibleInterval< DoubleType > locations,
			final RandomAccessibleInterval< DoubleType > exprValues,
			final List< String > geneNames,
			final List< String > barcodes,
			final HashMap< String, Integer > geneLookup,
			final N5FSReader n5Reader,
			final File n5path,
			final String datasetName )
	{
		super( locations, exprValues, geneNames, barcodes, geneLookup );

		this.n5Reader = n5Reader;
		this.n5path = n5path;
		this.datasetName = datasetName;
	}

	public STDataN5( final STDataImgLib2Factory factory, final N5FSReader n5Reader, final File n5path, final String datasetName )
	{
		this( factory.locations, factory.exprValues, factory.geneNames, factory.barcodes, factory.geneLookup, n5Reader, n5path, datasetName );
	}

	public File n5Path() { return n5path; }
	public N5FSReader n5Reader() { return n5Reader; }
	public String datasetName() { return datasetName; }

	@Override
	public String toString()
	{
		if ( datasetName != null )
			return datasetName;
		else
			return super.toString();
	}
}
