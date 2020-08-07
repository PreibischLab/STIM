package test;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import weka.attributeSelection.PrincipalComponents;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

public class PCATest
{
	public static HashMap< Integer, ArrayList< Double > > readCSV( final String path ) throws IOException
	{
		final Reader in = new FileReader( path );
		final Iterable< CSVRecord > records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse( in );

		final HashMap< Integer, ArrayList< Double > > data = new HashMap<>();

		for ( final CSVRecord record : records )
		{
			final int neuron = Integer.parseInt( record.get( "Neuron_number" ) );
			final ArrayList< Double > values = new ArrayList<>();

			for ( int i = 0; i <= 350; ++i )
				values.add( Double.parseDouble( record.get( Integer.toString( i ) ) ) );

			data.put( neuron, values );
		}

		in.close();

		return data;
	}

	public static void main( String args[] ) throws Exception
	{
		//weka.core.WekaPackageManager.loadPackages(false,true,false); 
		 
		final HashMap< Integer, ArrayList< Double > > data = readCSV( new File( "FP75_Worm_1_Fnorm_detrend_median_PCA_input.csv" ).getAbsolutePath() );

		//for ( final double v : data.get( 30 ) )
		//	System.out.println( v );

		final int capacity = data.get( data.keySet().iterator().next() ).size();

		final ArrayList< Integer > neurons = new ArrayList<>( data.keySet() );
		Collections.sort( neurons );

		final ArrayList< Attribute > attInfo = new ArrayList<>();
		for ( final int neuron : neurons )
			attInfo.add( new Attribute( "Neuron" + neuron, neuron ) );

		System.out.println( "capacity: " + capacity );
		for ( final Attribute att : attInfo )
			System.out.println( att  + " " + att.index() );

		Instances dataset = new Instances( "Dauer", attInfo, capacity );

		for ( int i = 0; i < capacity; ++i )
		{
			final Instance inst = new DenseInstance( attInfo.size() );

			for ( final Attribute att : attInfo )
				inst.setValue( att, data.get( att.index() ).get( i ) );

			dataset.add( inst );
		}

		PrincipalComponents pca = new PrincipalComponents();
		pca.buildEvaluator( dataset );

		//With

		//pca.getEigenValues();
		//pca.getUnsortedEigenVectors();
		//you revieve the information about the prinicpal components. You also can print an summary using

		System.out.println( pca );
		//which prints a nice summary, including the components (to a given degree, setMaximumAttributeNames specifies how many attributes are printed in this summary
	}
}
