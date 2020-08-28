package align;

import mpicbg.models.Point;

public class PointST extends Point
{
	/**
	 * 
	 */
	private static final long serialVersionUID = -3939977186278130300L;
	final String gene;

	public PointST(final double[] l, final String gene )
	{
		super(l);
		this.gene = gene;
	}

	public PointST(double[] l, double[] w, final String gene )
	{
		super(l,w);
		this.gene = gene;
	}

	public String getGene() { return gene; }
}
