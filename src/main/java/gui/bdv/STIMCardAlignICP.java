package gui.bdv;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

import align.AlignTools;
import align.ICPAlign;
import bdv.tools.transformation.TransformedSource;
import bdv.viewer.SynchronizedViewerState;
import cmd.InteractiveAlignment.AddedGene;
import data.STDataUtils;
import gui.DisplayScaleOverlay;
import gui.overlay.SIFTOverlay;
import mpicbg.models.Affine2D;
import mpicbg.models.CoordinateTransform;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.Model;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Pair;
import net.miginfocom.swing.MigLayout;
import util.BDVUtils;
import util.BoundedValue;
import util.BoundedValuePanel;

public class STIMCardAlignICP
{
	public class ICPParams
	{
		double maxErrorICP, maxErrorRANSAC;
		int maxIterations = 250;

		@Override
		public String toString()
		{
			String s = "";
			s += "maxErrorICP: " + this.maxErrorICP;
			s += ", maxErrorRANSAC: " + this.maxErrorRANSAC;
			s += ", maxIterations: " + this.maxIterations;

			return s;
		}
	}

	private final ICPParams param;
	private final JPanel panel;
	private final SIFTOverlay icpoverlay;
	private final STIMCard stimcard;
	private final STIMCardAlignSIFT stimcardSIFT;

	public STIMCardAlignICP(
			final String dataset1,
			final String dataset2,
			final DisplayScaleOverlay overlay,
			final STIMCard stimcard,
			final STIMCardAlignSIFT stimcardSIFT,
			final ExecutorService service )
	{
		this.stimcard = stimcard;
		this.stimcardSIFT = stimcardSIFT;

		this.panel = new JPanel(new MigLayout("gap 0, ins 5 5 5 5, fill", "[right][grow]", "center"));
		this.icpoverlay = new SIFTOverlay( new ArrayList<>(), stimcard.bdvhandle() );
		this.param = new ICPParams();

		final Interval interval = STDataUtils.getCommonInterval( stimcard.data1().data(), stimcard.data2().data() );
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

		// overlay listener
		overlayInliers.addChangeListener( e ->
		{
			if ( !overlayInliers.isSelected() )
			{
				stimcard.bdvhandle().getViewerPanel().renderTransformListeners().remove( icpoverlay );
				stimcard.bdvhandle().getViewerPanel().getDisplay().overlays().remove( icpoverlay );
			}
			else
			{
				stimcard.bdvhandle().getViewerPanel().renderTransformListeners().add( icpoverlay );
				stimcard.bdvhandle().getViewerPanel().getDisplay().overlays().add( icpoverlay );
			}
			stimcard.bdvhandle().getViewerPanel().requestRepaint();
		});

		//
		// Run ICP alignment
		//
		run.addActionListener( l ->
		{
			icpoverlay.setInliers( new ArrayList<>() );
			stimcard.bdvhandle().getViewerPanel().renderTransformListeners().remove( icpoverlay );
			stimcard.bdvhandle().getViewerPanel().getDisplay().overlays().remove( icpoverlay );
			run.setEnabled( false );
			cmdLine.setEnabled( false );
			overlayInliers.setEnabled( false );
			bar.setValue( 1 );

			// TODO: update transforms as we go
			// TODO: be able to stop alignment
			new Thread( () ->
			{
				final SynchronizedViewerState state = stimcard.bdvhandle().getViewerPanel().state();
				AddedGene.updateRemainingSources( state, stimcard.geneToBDVSource(), stimcard.sourceData() );

				final double lambda = Double.parseDouble( tfFinal.getText().trim() );
				Model model = STIMCardAlignSIFT.getModelFor( boxModelFinal1.getSelectedIndex(), boxModelFinal2.getSelectedIndex(), lambda );
				final HashSet< String > genes;

				if ( boxGenes.getSelectedIndex() == 0 ) // all displayed genes
					genes = new HashSet<>( stimcard.geneToBDVSource().keySet() );
				else
					genes = new HashSet<>( stimcardSIFT.genesWithInliers() ); // genes from SIFT

				param.maxErrorICP = maxErrorICPSlider.getValue().getValue();
				param.maxErrorRANSAC = maxErrorRANSACSlider.getValue().getValue();
				param.maxIterations = (int)Math.round( iterationsSlider.getValue().getValue() );

				System.out.println( "Running ICP align with the following parameters: \n" + param.toString() );
				System.out.println( "FINAL model: " + STIMCardAlignSIFT.optionsModel[ boxModelFinal1.getSelectedIndex() ] + ", regularizer: " + STIMCardAlignSIFT.optionsModelReg[ boxModelFinal2.getSelectedIndex() ] + ", lambda=" + lambda );

				System.out.println( model.getClass().getSimpleName() );

				final boolean visResult = false;
				final double[] progressBarValue = new double[] { 1.0 };

				// set the model as much as possible to the current transform
				final Affine2D<?> currentModel = stimcardSIFT.currentModel().createInverse();
				fit( model, currentModel, interval.dimension( 0 ), interval.dimension( 1 ), 8 );

				final Pair< Model, List< PointMatch > > icpT =
						ICPAlign.alignICP( stimcard.data1().data(), stimcard.data2().data(), genes, model, param.maxErrorICP, param.maxErrorRANSAC, param.maxIterations );

				// 
				// apply transformations
				//
				if ( !icpT.getB().isEmpty() )
				{
					try
					{
						model = icpT.getA();
						final AffineTransform2D m2d = AlignTools.modelToAffineTransform2D( (Affine2D)model ).inverse();
						final AffineTransform3D m3d = new AffineTransform3D();
						m3d.set(m2d.get(0, 0), 0, 0 ); // row, column
						m3d.set(m2d.get(0, 1), 0, 1 ); // row, column
						m3d.set(m2d.get(1, 0), 1, 0 ); // row, column
						m3d.set(m2d.get(1, 1), 1, 1 ); // row, column
						m3d.set(m2d.get(0, 2), 0, 3 ); // row, column
						m3d.set(m2d.get(1, 2), 1, 3 ); // row, column

						System.out.println( "2D model: " + m2d );
						System.out.println( "3D viewer transform: " + m3d );

						final List<TransformedSource<?>> tsources = BDVUtils.getTransformedSources(state);

						// every second source will be transformed
						for ( int i = 1; i < tsources.size(); i = i + 2 )
							tsources.get( i ).setFixedTransform( m3d );

					} catch (Exception e)
					{
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

					//
					// Overlay detections
					//
					icpoverlay.setInliers( icpT.getB() );


					overlayInliers.setSelected( true );
					overlayInliers.setEnabled( true );
				}
				else
				{
					icpoverlay.setInliers( new ArrayList<>() );
					overlayInliers.setEnabled( false );
				}

				bar.setValue( 100 );
				cmdLine.setEnabled( true );
				run.setEnabled( true );
				stimcard.bdvhandle().getViewerPanel().requestRepaint();
			}).start();

		});

		//
		// Return command line paramters for the last SIFT align run ...
		//
		cmdLine.addActionListener( l -> 
		{
			// TODO ...
		});

	}

	public JPanel getPanel() {
		return panel;
	}

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
	public static void fit(final Model<?> model, final CoordinateTransform coordinateTransform,
			final double sampleWidth, final double sampleHeight, final int samplesPerDimension) 
	{

		final List<PointMatch> matches = new ArrayList<>();

		for (int y = 0; y < samplesPerDimension; ++y) {
			final double sampleY = y * sampleHeight;
			for (int x = 0; x < samplesPerDimension; ++x) {
				final double sampleX = x * sampleWidth;
				final Point p = new Point(new double[] { sampleX, sampleY });
				p.apply(coordinateTransform);
				matches.add(new PointMatch(p, p));
			}
		}

		try {
			model.fit(matches);
		} catch (NotEnoughDataPointsException | IllDefinedDataPointsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
