package data;

import java.util.HashMap;
import java.util.List;

/**
 * A minimal version of STData for import/export
 * 
 * @author spreibi
 *
 */
public class STDataMinimal
{
	public List< double[] > coordinates;
	public HashMap< String, double[] > genes;

	public STDataMinimal( final List< double[] > coordinates, final HashMap< String, double[] > genes )
	{
		this.coordinates = coordinates;
		this.genes = genes;
	}
}
