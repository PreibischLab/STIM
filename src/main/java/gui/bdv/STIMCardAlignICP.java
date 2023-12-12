package gui.bdv;

import java.awt.Color;
import java.awt.Font;
import java.util.concurrent.ExecutorService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

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
		final JLabel maxErrorICPLabel = new JLabel("<html>Maximum error<br>ICP (px)</html>");
		final Font f = maxErrorICPLabel.getFont().deriveFont( 10f );
		maxErrorICPLabel.setFont( f );
		panel.add(maxErrorICPLabel, "aligny baseline");
		panel.add(maxErrorICPSlider, "growx, wrap");

		// max RANSAC error
		final BoundedValuePanel maxErrorRANSACSlider = new BoundedValuePanel(new BoundedValue(0, Math.round( Math.ceil( param.maxErrorRANSAC * 2 ) ), param.maxErrorRANSAC ));
		maxErrorRANSACSlider.setBorder(null);
		final JLabel maxErrorRANSACLabel = new JLabel("<html>Maximum error<br>RANSAC (px)</html>");
		maxErrorRANSACLabel.setFont( f );
		panel.add(maxErrorRANSACLabel, "aligny baseline");
		panel.add(maxErrorRANSACSlider, "growx, wrap");

		// iterations
		final BoundedValuePanel iterationsSlider = new BoundedValuePanel(new BoundedValue(50, Math.round( Math.ceil( param.maxIterations * 2 ) ), param.maxIterations ));
		iterationsSlider.setBorder(null);
		final JLabel iterationsLabel = new JLabel("Max. iterations");
		//iterationsLabel.setFont( f );
		panel.add(iterationsLabel, "aligny baseline");
		panel.add(iterationsSlider, "growx, wrap");

		// which genes
		final JComboBox< String > boxGenes = new JComboBox< String > ( new String[] { "All displayed genes", "SIFT genes only" } );
		boxGenes.setBorder( null );
		boxGenes.setSelectedIndex( 1 );
		final JLabel boxLabel = new JLabel("Genes to use ");
		//boxLabel.setFont( f );
		boxLabel.setBorder( BorderFactory.createEmptyBorder(5, 0, 5, 0) );
		panel.add( boxLabel, "aligny baseline" );
		panel.add( boxGenes, "growx, wrap" );

		final JLabel siftResults = new JLabel( "<html>Note: SIFT identified <FONT COLOR=\"#ff0000\">" + stimcardSIFT.genesWithInliers().size() + " genes</FONT> with correspondences.</html>");
		siftResults.setFont( f.deriveFont( Font.ITALIC ));
		siftResults.setBorder( BorderFactory.createEmptyBorder(5, 5, 5, 5) );
		panel.add(siftResults, "span,growx,pushy");

		// Panel for FINAL MODEL
		final JComboBox< String > boxModelFinal1 = new JComboBox< String > (STIMCardAlignSIFT.optionsModel);
		boxModelFinal1.setSelectedIndex( 1 );
		final JLabel boxModeFinalLabel1 = new JLabel("Final model ");
		panel.add( boxModeFinalLabel1, "aligny baseline, sy 2" );
		panel.add( boxModelFinal1, "growx, wrap" );
		final JPanel panFinal = new JPanel( new MigLayout("gap 0, ins 0 0 0 0, fill", "[right][grow]", "center") );
		final JComboBox< String > boxModelFinal2 = new JComboBox< String > (STIMCardAlignSIFT.optionsModelReg);
		boxModelFinal2.setSelectedIndex( 0 );
		panFinal.add( boxModelFinal2 );
		final JLabel labelFinalReg = new JLabel( "λ=" );
		panFinal.add( labelFinalReg, "alignx right" );
		final JTextField tfFinal = new JTextField( "0.1" );
		tfFinal.setEnabled( false );
		labelFinalReg.setForeground( Color.gray );
		panFinal.add( tfFinal, "growx" );
		panFinal.setBorder( BorderFactory.createEmptyBorder(0,0,5,0));
		panel.add( panFinal, "growx, wrap" );

		// sift overlay
		final JCheckBox overlayInliers = new JCheckBox( "Overlay ICP features" );
		panel.add(overlayInliers, "span,growx,pushy");
		overlayInliers.setSelected( true );
		overlayInliers.setEnabled( false );

		// progress bar
		final JProgressBar bar = new JProgressBar(SwingConstants.HORIZONTAL, 0, 100);
		bar.setValue( 0 );
		bar.setStringPainted(false);
		panel.add(bar, "span,growx,pushy");

		// buttons for adding genes and running SIFT
		final JButton cmdLine = new JButton("Command-line");
		cmdLine.setFont( cmdLine.getFont().deriveFont( 10f ));
		panel.add(cmdLine, "aligny baseline");
		final JButton run = new JButton("Run ICP alignment");
		panel.add(run, "growx, wrap");

		// disable lambdas if no regularization is selected
		boxModelFinal2.addActionListener( e -> {
			tfFinal.setEnabled( boxModelFinal2.getSelectedIndex() != 0 );
			labelFinalReg.setForeground( boxModelFinal2.getSelectedIndex() == 0 ? Color.gray : Color.black );
		} );

}

	public JPanel getPanel() { return panel; }
}
