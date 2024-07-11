package align;

import java.util.List;

import filter.FilterFactory;
import gui.bdv.AddedGene.Rendering;
import mpicbg.imagefeatures.FloatArray2DSIFT;
import net.imglib2.type.numeric.real.DoubleType;

public class SIFTParam
{
	public enum SIFTPreset { FAST, NORMAL, THOROUGH, VERY_THOROUGH}

	/* defaults from FloatArray2DSIFT.Param:
	 * 
	 * public int fdSize = 4;
	 * public int fdBins = 8;
	 * public int maxOctaveSize = 1024;
	 * public int minOctaveSize = 64;
	 * public int steps = 3;
	 * public float initialSigma = 1.6f;
	 * */
	final public FloatArray2DSIFT.Param sift = new FloatArray2DSIFT.Param();

	public double scale = Double.NaN;
	public double maxError = Double.NaN;
	public List< FilterFactory< DoubleType, DoubleType > > filterFactories = null;
	public Rendering rendering = null;
	public double renderingSmoothness = Double.NaN;
	public double brightnessMin = Double.NaN;
	public double brightnessMax = Double.NaN;

	public int iterations = 10000;
	public double minInlierRatio = 0.05;
	public int minInliersGene = 10;
	public int minInliersTotal = 25;

	/**
	 * Closest/next closest neighbour distance ratio
	 */
	public float rod = 0.92f;

	/**
	 * Try imgA vs imgB and imgB vs imgA
	 */
	public boolean biDirectional = false;

	public SIFTParam() {}

	/*
	 * these are dependent on the current dataset and can be extracted from the current BDV rendering and user input
	 */
	public void setDatasetParameters(
			final double maxError,
			final double scale,
			final int maxOctaveSize,
			final List< FilterFactory< DoubleType, DoubleType > > filterFactories,
			final Rendering rendering,
			final double renderingSmoothness,
			final double brightnessMin,
			final double brightnessMax )
	{
		this.scale = scale;
		this.maxError = maxError;
		this.sift.maxOctaveSize = maxOctaveSize;
		this.filterFactories = filterFactories;
		this.rendering = rendering;
		this.renderingSmoothness = renderingSmoothness;
		this.brightnessMin = brightnessMin;
		this.brightnessMax = brightnessMax;
	}

	public void setIntrinsicParameters(
			final int fdSize,
			final int fdBins,
			final int minOctaveSize,
			final int steps,
			final double initialSigma,
			final boolean biDirectional,
			final double rod,
			final double minInlierRatio,
			final int minInliersGene,
			final int minInliersTotal,
			final int iterations )
	{
		this.sift.fdSize = fdSize;
		this.sift.fdBins = fdBins;
		this.sift.minOctaveSize = minOctaveSize;
		this.sift.steps = steps;
		this.sift.initialSigma = (float)initialSigma;
		this.biDirectional = biDirectional;
		this.rod = (float)rod;
		this.minInlierRatio = minInlierRatio;
		this.minInliersGene = minInliersGene;
		this.minInliersTotal = minInliersTotal;
		this.iterations = iterations;
	}

	public void setIntrinsicParameters( final SIFTPreset preset )
	{
		switch ( preset )
		{
		case FAST:
			this.sift.fdSize = 4;
			this.sift.fdBins = 8;
			this.sift.minOctaveSize = 64;
			this.sift.steps = 3;
			this.sift.initialSigma = 0.5f;
			this.biDirectional = false;
			this.rod = 0.92f;
			this.minInlierRatio = 0.05;
			this.minInliersGene = 10;
			this.minInliersTotal = 25;
			this.iterations = 10000;

			break;
		case NORMAL:
			this.sift.fdSize = 8;
			this.sift.fdBins = 8;
			this.sift.minOctaveSize = 64;
			this.sift.steps = 3;
			this.sift.initialSigma = 0.5f;
			this.biDirectional = false;
			this.rod = 0.92f;
			this.minInlierRatio = 0.05;
			this.minInliersGene = 10;
			this.minInliersTotal = 25;
			this.iterations = 10000;

			break;
		case THOROUGH:
			this.sift.fdSize = 8;
			this.sift.fdBins = 8;
			this.sift.steps = 6;
			this.sift.minOctaveSize = 64;
			this.sift.initialSigma = 0.5f;
			this.rod = 0.90f;
			this.biDirectional = false;
			this.minInlierRatio = 0.03;
			this.minInliersGene = 8;
			this.minInliersTotal = 20;
			this.iterations = 10000;

			break;
		case VERY_THOROUGH:
			this.sift.fdSize = 8;
			this.sift.fdBins = 8;
			this.sift.steps = 10;
			this.sift.minOctaveSize = 128;
			this.sift.initialSigma = 0.5f;
			this.rod = 0.90f;
			this.biDirectional = true;
			this.minInlierRatio = 0.0;
			this.minInliersGene = 7;
			this.minInliersTotal = 15;
			this.iterations = 10000;

			break;
		}
	}

	@Override
	public String toString()
	{
		String s = "";
		s += "fdSize: " + this.sift.fdSize;
		s += ", fdBins: " + this.sift.fdBins;
		s += ", minOctaveSize: " + this.sift.minOctaveSize;
		s += ", steps: " + this.sift.steps;
		s += ", initialSigma: " + this.sift.initialSigma;
		s += ", biDirectional: " + this.biDirectional;
		s += ", rod: " + this.rod;
		s += ", minInlierRatio: " + this.minInlierRatio;
		s += ", minInliersGene: " + this.minInliersGene;
		s += ", minInliersTotal: " + this.minInliersTotal;
		s += ", iterations: " + this.iterations;

		s += "\n";

		s += "scale: " + this.scale;
		s += ", maxError: " + this.maxError;
		s += ", maxOctaveSize: " + this.sift.maxOctaveSize;
		s += ", rendering: " + this.rendering;
		s += ", renderingSmoothness: " + this.renderingSmoothness;
		s += ", brightnessMin: " + this.brightnessMin;
		s += ", brightnessMax: " + this.brightnessMax;

		return s;
	}
}
