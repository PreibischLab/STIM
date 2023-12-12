package gui.bdv;

import java.awt.Font;
import java.util.concurrent.ExecutorService;

import javax.swing.JLabel;
import javax.swing.JPanel;

import bdv.util.BdvHandle;
import data.STDataUtils;
import gui.DisplayScaleOverlay;
import gui.STDataAssembly;
import net.imglib2.Interval;
import net.miginfocom.swing.MigLayout;
import util.BoundedValue;
import util.BoundedValuePanel;

public class STIMCardAlignICP
{
	public class ICPParams
	{
		double maxErrorICP, maxErrorRANSAC;
		int maxIterations = 250;
	}

	private final ICPParams param;
	private final JPanel panel;
	private final STDataAssembly data1, data2;

	public STIMCardAlignICP(
			final STDataAssembly data1,
			final STDataAssembly data2,
			final String dataset1,
			final String dataset2,
			final DisplayScaleOverlay overlay,
			final STIMCard stimcard,
			final STIMCardAlignSIFT stimcardSIFT,
			final double medianDistance,
			final BdvHandle bdvhandle,
			final ExecutorService service )
	{
		this.data1 = data1;
		this.data2 = data2;

		this.panel = new JPanel(new MigLayout("gap 0, ins 5 5 5 5, fill", "[right][grow]", "center"));
		this.param = new ICPParams();
		final Interval interval = STDataUtils.getCommonInterval( data1.data(), data2.data() );
		this.param.maxErrorICP = Math.max( interval.dimension( 0 ), interval.dimension( 1 ) ) / 20;
		this.param.maxErrorRANSAC = this.param.maxErrorICP / 2.0;

		// max ICP error
		final BoundedValuePanel maxErrorICPSlider = new BoundedValuePanel(new BoundedValue(0, Math.round( Math.ceil( param.maxErrorICP * 2 ) ), param.maxErrorICP ));
		maxErrorICPSlider.setBorder(null);
		final JLabel maxErrorICPLabel = new JLabel("max. error ICP (px)");
		final Font f = maxErrorICPLabel.getFont().deriveFont( 10f );
		maxErrorICPLabel.setFont( f );
		panel.add(maxErrorICPLabel, "aligny baseline");
		panel.add(maxErrorICPSlider, "growx, wrap");

		// max RANSAC error
		final BoundedValuePanel maxErrorRANSACSlider = new BoundedValuePanel(new BoundedValue(0, Math.round( Math.ceil( param.maxErrorRANSAC * 2 ) ), param.maxErrorRANSAC ));
		maxErrorRANSACSlider.setBorder(null);
		final JLabel maxErrorRANSACLabel = new JLabel("max. error RANSAC (px)");
		maxErrorRANSACLabel.setFont( f );
		panel.add(maxErrorRANSACLabel, "aligny baseline");
		panel.add(maxErrorRANSACSlider, "growx, wrap");

		// iterations
		final BoundedValuePanel iterationsSlider = new BoundedValuePanel(new BoundedValue(50, Math.round( Math.ceil( param.maxIterations * 2 ) ), param.maxIterations ));
		iterationsSlider.setBorder(null);
		final JLabel iterationsLabel = new JLabel("max. iterations");
		iterationsLabel.setFont( f );
		panel.add(iterationsLabel, "aligny baseline");
		panel.add(iterationsSlider, "growx, wrap");
}

	public JPanel getPanel() { return panel; }
}
