package align;

import mpicbg.models.PointMatch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class SiftMatch {
	final protected String stDataAName;
	final protected String stDataBName;
	final protected int numCandidates;
	final protected ArrayList<PointMatch> inliers;
	final protected Set<String> genes;

	public SiftMatch() {
		this.stDataAName = null;
		this.stDataBName = null;
		this.numCandidates = 0;
		this.inliers = null;
		this.genes = null;
	}

	public SiftMatch(String stDataAName,
					 String stDataBName,
					 int numCandidates,
					 ArrayList<PointMatch> inliers) {
		this.stDataAName = stDataAName;
		this.stDataBName = stDataBName;
		this.numCandidates = numCandidates;
		this.inliers = inliers;
		this.genes = new HashSet<>();
		for (final PointMatch match : inliers)
			genes.add(((PointST) match.getP1()).getGene());
	}

	public String getStDataAName() {
		return stDataAName;
	}

	public String getStDataBName() {
		return stDataBName;
	}

	public int getNumCandidates() {
		return numCandidates;
	}

	public int getNumInliers() {
		return inliers.size();
	}

	public ArrayList<PointMatch> getInliers() {
		return inliers;
	}

	public Set<String> getGenes() {
		return genes;
	}

	public double quality() {
		if (getNumCandidates() == 0)
			return 0;
		else
			return ((double) getNumInliers() / (double) getNumCandidates()) * Math.sqrt(getNumInliers());
	}

}
