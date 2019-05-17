package importer;

import java.util.ArrayList;
import java.util.HashMap;

import net.imglib2.Interval;
import net.imglib2.RealInterval;
import util.ImgLib2Util.SimpleStats;

public class STData
{
	public ArrayList< double[] > coordinates;
	public HashMap< String, double[] > genes;
	public SimpleStats distanceStats;
	public RealInterval interval;
	public Interval renderInterval;
}
