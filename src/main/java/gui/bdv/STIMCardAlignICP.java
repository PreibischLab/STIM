package gui.bdv;

import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import align.AlignTools;
import align.ICPAlign;
import bdv.viewer.SynchronizedViewerState;
import cmd.InteractiveAlignment;
import data.STDataUtils;
import gui.DisplayScaleOverlay;
import mpicbg.models.Affine2D;
import mpicbg.models.AffineModel2D;
import mpicbg.models.CoordinateTransform;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.Model;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import net.imglib2.Interval;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.util.Pair;
import net.miginfocom.swing.MigLayout;
import util.BoundedValue;
import util.BoundedValuePanel;

public class STIMCardAlignICP
{
	public class ICPParams
	{
		double maxErrorICP, maxErrorRANSAC;
		AtomicInteger maxIterations = new AtomicInteger( 100 ); // so it can be changed
		boolean useRANSAC = false;

		Double ffSingleSpot = null;
		Double ffMedian = null;
		Double ffGauss = null;
		Double ffMean = null;

		@Override
		public String toString()
		{
			String s = "";
			s += "maxErrorICP: " + this.maxErrorICP;
			s += ", useRANSAC: " + this.useRANSAC;
			s += ", maxErrorRANSAC: " + this.maxErrorRANSAC;
			s += ", maxIterations: " + this.maxIterations;
			s += ", ffSingleSpot: " + this.ffSingleSpot;
			s += ", ffMedian: " + this.ffMedian;
			s += ", ffGauss: " + this.ffGauss;
			s += ", ffMean: " + this.ffMean;

			return s;
		}
	}

	private final JLabel siftResults;
	protected final JButton cmdLine, run, reset, saveTransform;
	private final JProgressBar bar;
	private Thread icpThread = null;
	private Affine2D<?> previousModel = null;

	private final ICPParams param;
	private final JPanel panel;
	private final STIMCardFilter cardFilter;
	private STIMCardAlignSIFT stimcardSIFT;
	private STIMCardManualAlign manualCard = null; // may or may not be there

	public STIMCardAlignICP(
			final String dataset1,
			final String dataset2,
			final DisplayScaleOverlay overlay,
			final STIMCard stimcard,
			final STIMCardFilter cardFilter,
			final STIMCardAlignSIFT stimcardSIFT,
			final ExecutorService service )
	{
		this.panel = new JPanel(new MigLayout("gap 0, ins 5 5 5 5, fill", "[right][grow]", "center"));
		this.param = new ICPParams();
		this.cardFilter = cardFilter;
		this.stimcardSIFT = stimcardSIFT;

		final Interval interval = STDataUtils.getCommonInterval( stimcard.data().get( 0 ).data(), stimcard.data().get( 1 ).data() );
		this.param.maxErrorICP = Math.max( stimcard.medianDistance(), ( Math.max( interval.dimension( 0 ), interval.dimension( 1 ) ) / 20 ) / 5.0 );
		this.param.maxErrorRANSAC = this.param.maxErrorICP / 2.0;

		// max ICP error
		final BoundedValuePanel maxErrorICPSlider = new BoundedValuePanel(new BoundedValue(0, Math.round( Math.ceil( param.maxErrorICP * 2 ) ), param.maxErrorICP ));
		maxErrorICPSlider.setBorder(null);
		final JLabel maxErrorICPLabel = new JLabel("<html>Maximum error<br>ICP (px)</html>");
		final Font f = maxErrorICPLabel.getFont().deriveFont( 10f );
		maxErrorICPLabel.setFont( f );
		panel.add(maxErrorICPLabel, "aligny baseline");
		panel.add(maxErrorICPSlider, "growx, wrap");

		// use RANSAC checkbox
		final JCheckBox useRANSAC = new JCheckBox( "Use RANSAC filtering of ICP inliers" );
		panel.add(useRANSAC, "span,growx,pushy");
		useRANSAC.setBorder( BorderFactory.createEmptyBorder(5,0,0,0) );
		useRANSAC.setSelected( false );

		// max RANSAC error
		final BoundedValuePanel maxErrorRANSACSlider = new BoundedValuePanel(new BoundedValue(0, Math.round( Math.ceil( param.maxErrorRANSAC * 2 ) ), param.maxErrorRANSAC ));
		maxErrorRANSACSlider.setBorder(null);
		final JLabel maxErrorRANSACLabel = new JLabel("<html>Maximum error<br>RANSAC (px)</html>");
		maxErrorRANSACLabel.setFont( f );
		maxErrorRANSACSlider.setEnabled( false );
		panel.add(maxErrorRANSACLabel, "aligny baseline");
		panel.add(maxErrorRANSACSlider, "growx, wrap");

		// iterations
		final BoundedValuePanel iterationsSlider = new BoundedValuePanel(new BoundedValue(50, Math.round( Math.ceil( param.maxIterations.get() * 2 ) ), param.maxIterations.get() ));
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

		siftResults = new JLabel( getSIFTResultLabelText( stimcardSIFT.genesWithInliers().size() ) );
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

		// progress bar
		bar = new JProgressBar(SwingConstants.HORIZONTAL, 0, 100);
		bar.setValue( 0 );
		bar.setStringPainted(false);
		panel.add(bar, "span,growx,pushy");

		// buttons for adding genes and running SIFT
		//cmdLine = new JButton("Command-line");
		//cmdLine.setFont( cmdLine.getFont().deriveFont( 10f ));
		//panel.add(cmdLine, "aligny baseline");
		//run = new JButton("Run ICP alignment");
		//panel.add(run, "growx, wrap");

		// buttons for saveTransform, cmd-line, reset transforms and running SIFT
		final JPanel panelbuttons = new JPanel( new MigLayout("gap 0, ins 0 0 0 0, fill", "[right][grow]", "center") );
		run = new JButton("Run ICP Alignment");
		run.setFont( run.getFont().deriveFont(Font.BOLD));

		cmdLine = new JButton();
		saveTransform = new JButton();
		reset = new JButton();

		try
		{
			cmdLine.setIcon(new ImageIcon(ImageIO.read( InteractiveAlignment.class.getResource("../cmdline.png") )));
			saveTransform.setIcon(new ImageIcon(ImageIO.read( InteractiveAlignment.class.getResource("../save.png") )));
			reset.setIcon(new ImageIcon(ImageIO.read( InteractiveAlignment.class.getResource("../reset.png") )));
		}
		catch (IOException e)
		{
			cmdLine.setText("Cmd-line");
			cmdLine.setFont( cmdLine.getFont().deriveFont( 10.5f ));
			saveTransform.setText("Save transform");
			saveTransform.setFont( saveTransform.getFont().deriveFont( 10.5f ));
			reset.setText("Reset");
			reset.setFont( reset.getFont().deriveFont( 10.5f ));
		}

		cmdLine.setToolTipText( "Create command-line arguments" );
		saveTransform.setToolTipText( "Save transformation to container" );
		reset.setToolTipText( "Reset transformation" );

		panelbuttons.add(cmdLine);
		panelbuttons.add(saveTransform);
		panelbuttons.add(reset);
		JPanel j = new JPanel();
		j.setBorder( BorderFactory.createEmptyBorder(0,1,0,0));
		panelbuttons.add( j );
		panelbuttons.add(run, "span, pushy, growx");
		panelbuttons.setBorder( BorderFactory.createEmptyBorder(0,0,1,0));
		panel.add(panelbuttons, "span,growx,pushy");

		// disable RANSAC slider if not used
		useRANSAC.addActionListener( e -> maxErrorRANSACSlider.setEnabled( useRANSAC.isSelected() ) );

		// disable lambdas if no regularization is selected
		boxModelFinal2.addActionListener( e -> {
			tfFinal.setEnabled( boxModelFinal2.getSelectedIndex() != 0 );
			labelFinalReg.setForeground( boxModelFinal2.getSelectedIndex() == 0 ? Color.gray : Color.black );
		} );

		// be able to change number of iterations while running
		iterationsSlider.changeListeners().add( () -> {
			param.maxIterations.set( (int)Math.round( iterationsSlider.getValue().getValue() ) );
		});

		//
		// Run ICP alignment
		//
		run.addActionListener( l ->
		{
			if ( icpThread != null )
			{
				// request to cancel
				icpThread.stop();

				// wait a bit
				try { Thread.sleep( 100 ); } catch (InterruptedException e1) {}

				reEnableControls();

				icpThread = null;

				// we're just stopping ...
				System.out.println( "Stopping ICP with the following models (hit reset to restore previous transformation): " );
				System.out.println( "2D model: " + stimcard.sourceData().values().iterator().next().get( 0 ).currentModel() );
				System.out.println( "2D transform: " + stimcard.sourceData().values().iterator().next().get( 0 ).currentModel2D() );
				System.out.println( "3D viewer transform: " + stimcard.sourceData().values().iterator().next().get( 0 ).currentModel3D() );

				if ( manualCard != null )
				{
					manualCard.setTransformGUI( AlignTools.modelToAffineTransform2D( stimcard.sourceData().values().iterator().next().get( 0 ).currentModel() ) );
					manualCard.reEnableControlsExternal();
				}

				return;
			}

			// we remember the state before first time calling ICP
			if ( previousModel == null )
				previousModel = (Affine2D)((Model)stimcard.sourceData().values().iterator().next().get( 0 ).currentModel()).copy();

			run.setForeground( Color.red );
			run.setFont( run.getFont().deriveFont( Font.BOLD ) );
			run.setText( "     STOP ICP run     ");
			cmdLine.setEnabled( false );
			reset.setEnabled( false );
			saveTransform.setEnabled( false );
			bar.setValue( 1 );
			stimcardSIFT.cmdLine.setEnabled( false );
			stimcardSIFT.run.setEnabled( false );
			stimcardSIFT.saveTransform.setEnabled( false );
			stimcardSIFT.overlayInliers.setSelected( false );

			if ( manualCard != null )
				manualCard.disableControlsExternal();

			// TODO: make sure current model is taken into account (seems to be somehow, weird)
			icpThread = new Thread( () ->
			{
				final SynchronizedViewerState state = stimcard.bdvhandle().getViewerPanel().state();
				AddedGene.updateRemainingSources( state, stimcard.geneToBDVSource(), stimcard.sourceData() );

				final double lambda = Double.parseDouble( tfFinal.getText().trim() );
				Model model = STIMCardAlignSIFT.getModelFor( boxModelFinal1.getSelectedIndex(), boxModelFinal2.getSelectedIndex(), lambda );

				System.out.println( model.getClass().getSimpleName() );
				System.out.println( "current  : " + stimcard.sourceData().values().iterator().next().get( 0 ).currentModel() );

				// set the model as much as possible to the current transform
				fit( model, stimcard.sourceData().values().iterator().next().get( 0 ).currentModel(), interval.dimension( 0 ) / 4.0, interval.dimension( 1 ) / 4.0, 4 );

				System.out.println( "ICP input: " + model );

				final HashSet< String > genes;

				if ( boxGenes.getSelectedIndex() == 0 ) // all displayed genes
					genes = new HashSet<>( stimcard.geneToBDVSource().keySet() );
				else
					genes = new HashSet<>( stimcardSIFT.genesWithInliers() ); // genes from SIFT

				if ( genes.size() == 0 )
				{
					System.out.println( "no genes for ICP, please run SIFT successfully first or select 'all displayed genes'.");

					reEnableControls();
					icpThread = null;

					return;
				}

				param.useRANSAC = useRANSAC.isSelected();
				param.maxErrorICP = maxErrorICPSlider.getValue().getValue();
				param.maxErrorRANSAC = param.useRANSAC ? maxErrorRANSACSlider.getValue().getValue() : Double.NaN;
				param.maxIterations.set( (int)Math.round( iterationsSlider.getValue().getValue() ) );

				if ( this.cardFilter.getTableModel().currentActiveValues[ 0 ] )
					param.ffSingleSpot = stimcard.medianDistance() * this.cardFilter.getTableModel().currentRadiusValues[ 0 ];
				else
					param.ffSingleSpot = null;

				if ( this.cardFilter.getTableModel().currentActiveValues[ 1 ] )
					param.ffMedian = stimcard.medianDistance() * this.cardFilter.getTableModel().currentRadiusValues[ 1 ];
				else
					param.ffMedian = null;

				if ( this.cardFilter.getTableModel().currentActiveValues[ 2 ] )
					param.ffGauss = stimcard.medianDistance() * this.cardFilter.getTableModel().currentRadiusValues[ 2 ];
				else
					param.ffGauss = null;

				if ( this.cardFilter.getTableModel().currentActiveValues[ 3 ] )
					param.ffMean = stimcard.medianDistance() * this.cardFilter.getTableModel().currentRadiusValues[ 3 ];
				else
					param.ffMean = null;

				System.out.println( "Running ICP align with the following parameters: \n" + param.toString() );
				System.out.println( "FINAL model: " + STIMCardAlignSIFT.optionsModel[ boxModelFinal1.getSelectedIndex() ] + ", regularizer: " + STIMCardAlignSIFT.optionsModelReg[ boxModelFinal2.getSelectedIndex() ] + ", lambda=" + lambda );

				final boolean visResult = false;
				final double[] progressBarValue = new double[] { 1.0 };


				final Pair<Model, List<PointMatch>> icpT = 
						ICPAlign.alignICP(
								stimcard.data().get( 1 ).data(),
								stimcard.data().get( 1 ).transform(),
								stimcard.data().get( 0 ).data(),
								stimcard.data().get( 0 ).transform(),
								genes, model,
								param.maxErrorICP, param.maxErrorRANSAC, param.maxIterations, param.ffSingleSpot, param.ffMedian, param.ffGauss, param.ffMean,
								v ->
								{
									progressBarValue[0] += v;
									bar.setValue((int) Math.round(progressBarValue[0]));
								},
								m ->
								{
									stimcardSIFT.setModel( (Affine2D)m );
									stimcard.applyTransformationToBDV( true );

									if ( manualCard != null )
										SwingUtilities.invokeLater( () -> manualCard.setTransformGUI( AlignTools.modelToAffineTransform2D( (Affine2D)m ) ) );
									SimpleMultiThreading.threadWait( 100 );
								},
								service);

				// 
				// apply transformations
				//
				if ( icpT != null && !icpT.getB().isEmpty() )
				{
					System.out.println( "ICP finished successfully.");
					try
					{
						model = icpT.getA();

						stimcardSIFT.setModel( (Affine2D)model );

						if ( manualCard != null )
							manualCard.setTransformGUI( AlignTools.modelToAffineTransform2D( (Affine2D)model ) );

						System.out.println( "2D model: " + model );
						System.out.println( "2D transform: " + stimcard.sourceData().values().iterator().next().get( 0 ).currentModel2D() );
						System.out.println( "3D viewer transform: " + stimcard.sourceData().values().iterator().next().get( 0 ).currentModel3D() );
					}
					catch (Exception e)
					{
						e.printStackTrace();
					}
				}
				else
				{
					System.out.println( "ICP did not converge.");

					stimcardSIFT.setModel( previousModel );

					if ( manualCard != null )
						manualCard.setTransformGUI( AlignTools.modelToAffineTransform2D( previousModel ) );
				}

				if ( manualCard != null )
					manualCard.reEnableControlsExternal();

				reEnableControls();
				stimcard.applyTransformationToBDV( true );

				icpThread = null;
			});

			icpThread.start();
		});

		//
		// Reset transform
		//
		reset.addActionListener( l ->
		{
			stimcardSIFT.setModel( previousModel );
			stimcard.applyTransformationToBDV( true );

			if ( manualCard != null )
				manualCard.setTransformGUI( AlignTools.modelToAffineTransform2D( previousModel ) );

			System.out.println( "reset ICP transformations.");
		});

		//
		// Save transform
		//
		saveTransform.addActionListener( l -> stimcardSIFT.saveTransforms() );

		//
		// Return command line paramters for the last SIFT align run ...
		//
		cmdLine.addActionListener( l -> 
		{
			// TODO ...
		});
	}

	protected void reEnableControls()
	{
		cmdLine.setEnabled( true );
		reset.setEnabled( true );
		saveTransform.setEnabled( true );
		run.setText( "Run ICP alignment" );
		run.setFont( run.getFont().deriveFont( Font.BOLD ) );
		run.setForeground( Color.black );
		bar.setValue( 0 );

		stimcardSIFT.cmdLine.setEnabled( true );
		stimcardSIFT.run.setEnabled( true );
		stimcardSIFT.reset.setEnabled( true );
		stimcardSIFT.saveTransform.setEnabled( true );
	}

	public static String getSIFTResultLabelText(int s) { return "<html>Note: SIFT identified <FONT COLOR=\"#ff0000\">" + s + " genes</FONT> with correspondences.</html>"; }
	public void setManualAlignCard( final STIMCardManualAlign manualCard ) { this.manualCard = manualCard; }
	public JLabel siftResults() { return siftResults; }
	public JPanel getPanel() { return panel; }

	/**
	 * Fits sampled points to a model.
	 *
	 * Stolen from
	 *
	 * <a href=
	 * "https://github.com/axtimwalde/fiji-scripts/blob/master/TrakEM2/visualize-ct-difference.bsh#L90-L106">
	 * https://github.com/axtimwalde/fiji-scripts/blob/master/TrakEM2/visualize-ct-difference.bsh#L90-L106
	 * </a>.
	 *
	 * @param model               model to fit (note: model will be changed by this
	 *                            operation).
	 * @param coordinateTransform transform to apply to each sampled point.
	 * @param sampleWidth         width of each sample.
	 * @param sampleHeight        height of each sample.
	 * @param samplesPerDimension number of samples to take in each dimension.
	 */
	public static void fit(
			final Model<?> model,
			final CoordinateTransform coordinateTransform,
			final double sampleWidth,
			final double sampleHeight,
			final int samplesPerDimension)
	{
		final List<PointMatch> matches = new ArrayList<>();

		for (int y = 0; y < samplesPerDimension; ++y)
		{
			final double sampleY = y * sampleHeight;
			for (int x = 0; x < samplesPerDimension; ++x)
			{
				final double sampleX = x * sampleWidth;

				final Point p = new Point(new double[] { sampleX, sampleY });
				p.apply(coordinateTransform);

				//System.out.println( Util.printCoordinates( p.getL() ) + " >> " + Util.printCoordinates( p.getW() ));
				matches.add(new PointMatch(new Point(new double[] { sampleX, sampleY }), p));
			}
		}

		/*
		(0.0, 0.0) >> (-546.6035992226643, 4231.453000084942)
		(5601.0, 0.0) >> (1266.3276248460947, -913.3744190339776)
		(0.0, 5841.0) >> (5311.211627643417, 6282.676269118562)
		(5601.0, 5841.0) >> (7124.142851712177, 1137.8488499996429)

		(-394.67587180284, 4359.270213989155) ~~ (-394.67587180284, 4359.270213989155)
		(1461.1680473398637, -925.3342506591734) ~~ (1461.1680473398637, -925.3342506591734)
		(5116.371205149648, 6294.636100743758) ~~ (5116.371205149648, 6294.636100743758)
		(6972.215124292352, 1010.0316360954293) ~~ (6972.215124292352, 1010.0316360954293)
		*/

		try
		{
			model.fit(matches);

			for ( final PointMatch pm : matches )
			{
				pm.apply( model );
				//System.out.println( Util.printCoordinates( pm.getP1().getW() ) + " ~~ " + Util.printCoordinates( pm.getP2().getW() ));
			}
		}
		catch (NotEnoughDataPointsException | IllDefinedDataPointsException e) { e.printStackTrace(); }
	}

}
