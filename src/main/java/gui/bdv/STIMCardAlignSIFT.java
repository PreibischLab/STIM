package gui.bdv;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.text.NumberFormatter;

import org.janelia.saalfeldlab.n5.N5Writer;

import align.AlignTools;
import align.PairwiseSIFT;
import align.PointST;
import align.SIFTParam;
import align.SIFTParam.SIFTPreset;
import cmd.InteractiveAlignment;
import align.SiftMatch;
import data.STData;
import data.STDataUtils;
import gui.overlay.SIFTOverlay;
import imglib2.TransformedIterableRealInterval;
import io.SpatialDataIO;
import mpicbg.models.Affine2D;
import mpicbg.models.AffineModel2D;
import mpicbg.models.InterpolatedAffineModel2D;
import mpicbg.models.Model;
import mpicbg.models.PointMatch;
import mpicbg.models.RigidModel2D;
import mpicbg.models.SimilarityModel2D;
import mpicbg.models.TranslationModel2D;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.miginfocom.swing.MigLayout;
import util.BoundedValue;
import util.BoundedValuePanel;

public class STIMCardAlignSIFT
{
	private final JPanel panel;
	private final SIFTOverlay siftoverlay;
	private final STIMCard stimcard;
	private final STIMCardFilter stimcardFilter;
	private STIMCardAlignICP icpCard = null; // may or may not be there
	private STIMCardManualAlign manualCard = null; // may or may not be there

	private Thread siftThread = null;
	private List< Thread > threads = new ArrayList<>();

	protected final JButton cmdLine, saveTransform, run, reset;
	protected final JCheckBox overlayInliers;
	private final JCheckBox biDirectional;
	private final JFormattedTextField ilr, initialSigma, minOS, maxOS, it, fdSize, fdBins, steps, rod;
	private final JProgressBar bar;
	private final BoundedValuePanel inliersPerGeneSlider, inliersSlider, maxErrorSlider;
	private final JComboBox< String > boxModelFinal1, boxModelFinal2, boxModelRANSAC1, boxModelRANSAC2;
	private final JTextField tfRANSAC, tfFinal;

	private Affine2D<?> previousModel = null;
	private final SIFTParam param;
	final static String optionsSIFT[] = { "Fast", "Normal", "Thorough", "Very thorough", "Custom ..." };
	private boolean customModeSIFT = false; // we always start with "normal" for 

	private final ExecutorService service;
	private HashSet< String > genesWithInliers = new HashSet<>();
	private double lastMaxError = Double.NaN;

	final static String optionsModel[] = { "Translation", "Rigid", "Similarity", "Affine" };
	final static String optionsModelReg[] = { "No Reg.", "Transl.", "Rigid", "Simil.", "Affine" };

	public STIMCardAlignSIFT(
			final String dataset1,
			final String dataset2,
			final STIMCard stimcard,
			final STIMCardFilter stimcardFilter,
			final ExecutorService service )
	{
		// setup initial parameters
		final int initSIFTPreset = 1; // NORMAL
		this.param = new SIFTParam();
		this.param.setIntrinsicParameters( SIFTPreset.values()[ initSIFTPreset ] );
		this.service = service;

		final Interval interval = STDataUtils.getCommonInterval( stimcard.data().get( 0 ).data(), stimcard.data().get( 1 ).data() );
		this.param.maxError = Math.max( interval.dimension( 0 ), interval.dimension( 1 ) ) / 20;
		this.param.sift.maxOctaveSize = 1024; // TODO find out

		this.siftoverlay = new SIFTOverlay( new ArrayList<>(), stimcard.bdvhandle() );
		this.stimcard = stimcard;
		this.stimcardFilter = stimcardFilter;
		this.panel = new JPanel(new MigLayout("gap 0, ins 5 5 5 5, fill", "[right][grow]", "center"));

		// lists for components
		final List< Component > customComponents = new ArrayList<>();
		final List< Component > advancedSIFTComponents = new ArrayList<>();

		// setup formatters
		final NumberFormatter formatter = formatterInt();
		final NumberFormatter formatterDouble = formatterDouble();
		final NumberFormatter formatterDouble01 = formatterDouble01();

		// SIFT presets
		final JComboBox< String > box = new JComboBox< String > ( optionsSIFT );
		box.setBorder( null );
		box.setSelectedIndex( initSIFTPreset );
		final JLabel boxLabel = new JLabel("SIFT Matching Preset ");
		final Font font = boxLabel.getFont().deriveFont( 10f );
		boxLabel.setFont( font );
		boxLabel.setBorder( null );
		panel.add( boxLabel, "aligny baseline" );
		panel.add( box, "growx, wrap" );

		// sift advanced options
		final JCheckBox advancedOptions = new JCheckBox( "Show advanced SIFT options" );
		panel.add(advancedOptions, "span,growx,pushy");
		advancedOptions.setBorder( BorderFactory.createEmptyBorder(5,0,0,0) );
		advancedOptions.setSelected( false );

		// max error
		maxErrorSlider = new BoundedValuePanel(new BoundedValue(0, Math.round( Math.ceil( param.maxError * 2 ) ), param.maxError ));
		maxErrorSlider.setBorder(null);
		final JLabel maxErrorLabel = new JLabel("max. error (px)");
		panel.add(maxErrorLabel, "aligny baseline");
		panel.add(maxErrorSlider, "growx, wrap");

		// inliers
		inliersSlider = new BoundedValuePanel(new BoundedValue(0, param.minInliersTotal * 2, param.minInliersTotal ));
		inliersSlider.setBorder(null);
		customComponents.add( inliersSlider );
		final JLabel inliersLabel = new JLabel("min inliers (total)");
		inliersLabel.setFont( font );
		inliersLabel.setBorder( null );
		panel.add(inliersLabel, "aligny baseline");
		panel.add(inliersSlider, "growx, wrap");

		// inliersPerGene
		inliersPerGeneSlider = new BoundedValuePanel(new BoundedValue(0, param.minInliersGene * 2, param.minInliersGene ));
		inliersPerGeneSlider.setBorder(null);
		customComponents.add( inliersPerGeneSlider );
		final JLabel inliersPerGeneLabel = new JLabel("min inliers (gene)");
		inliersPerGeneLabel.setFont( font );
		inliersPerGeneLabel.setBorder( null );
		panel.add(inliersPerGeneLabel, "aligny baseline");
		panel.add(inliersPerGeneSlider, "growx, wrap");

		// to add advanced parameters if necessary
		final int componentCount = panel.getComponentCount();

		// Panel for RANSAC MODEL
		boxModelRANSAC1 = new JComboBox< String > (optionsModel);
		boxModelRANSAC1.setSelectedIndex( 1 );
		final JLabel boxModeRANSACLabel1 = new JLabel("RANSAC model ");
		panel.add( boxModeRANSACLabel1, "aligny baseline, sy 2" );
		panel.add( boxModelRANSAC1, "growx, wrap" );
		final JPanel panRANSAC = new JPanel( new MigLayout("gap 0, ins 0 0 0 0, fill", "[right][grow]", "center") );
		boxModelRANSAC2 = new JComboBox< String > (optionsModelReg);
		boxModelRANSAC2.setSelectedIndex( 0 );
		panRANSAC.add( boxModelRANSAC2 );
		final JLabel labelRANSACReg = new JLabel( "λ=" );
		panRANSAC.add( labelRANSACReg, "alignx right" );
		tfRANSAC = new JTextField( "0.1" );
		tfRANSAC.setEnabled( false );
		labelRANSACReg.setForeground( Color.gray );
		panRANSAC.add( tfRANSAC, "growx" );
		panRANSAC.setBorder( BorderFactory.createEmptyBorder(0,0,5,0));
		panel.add( panRANSAC, "growx, wrap" );

		// Panel for FINAL MODEL
		boxModelFinal1 = new JComboBox< String > (optionsModel);
		boxModelFinal1.setSelectedIndex( 1 );
		final JLabel boxModeFinalLabel1 = new JLabel("Final model ");
		panel.add( boxModeFinalLabel1, "aligny baseline, sy 2" );
		panel.add( boxModelFinal1, "growx, wrap" );
		final JPanel panFinal = new JPanel( new MigLayout("gap 0, ins 0 0 0 0, fill", "[right][grow]", "center") );
		boxModelFinal2 = new JComboBox< String > (optionsModelReg);
		boxModelFinal2.setSelectedIndex( 0 );
		panFinal.add( boxModelFinal2 );
		final JLabel labelFinalReg = new JLabel( "λ=" );
		panFinal.add( labelFinalReg, "alignx right" );
		tfFinal = new JTextField( "0.1" );
		tfFinal.setEnabled( false );
		labelFinalReg.setForeground( Color.gray );
		panFinal.add( tfFinal, "growx" );
		panFinal.setBorder( BorderFactory.createEmptyBorder(0,0,5,0));
		panel.add( panFinal, "growx, wrap" );

		// sift overlay
		overlayInliers = new JCheckBox( "Overlay SIFT features" );
		panel.add(overlayInliers, "span,growx,pushy");
		overlayInliers.setSelected( true );
		overlayInliers.setEnabled( false );

		// progress bar
		bar = new JProgressBar(SwingConstants.HORIZONTAL, 0, 100);
		bar.setValue( 0 );
		bar.setStringPainted(false);
		panel.add(bar, "span,growx,pushy");

		// buttons for saveTransform, cmd-line, reset transforms and running SIFT
		final JPanel panelbuttons = new JPanel( new MigLayout("gap 0, ins 0 0 0 0, fill", "[right][grow]", "center") );
		run = new JButton("Run SIFT Alignment");
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

		//
		// for the advanced mode (move to separate object?)
		final JSeparator j1 = new JSeparator();// j1.setBackground( Color.gray );
		advancedSIFTComponents.add( j1 );

		final Font fontTF = inliersLabel.getFont().deriveFont( 9f );

		// fdsize
		fdSize = new JFormattedTextField( formatter );
		fdSize.setBorder( new TitledBorder(null, "Feature descriptor size (-fdSize)", TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION, fontTF, null ) );
		fdSize.setValue( param.sift.fdSize );
		customComponents.add( fdSize );
		advancedSIFTComponents.add( fdSize );

		// fdBins
		fdBins = new JFormattedTextField( formatter );
		fdBins.setBorder( new TitledBorder(null, "Feature descriptor orientation bins (-fdBins)", TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION, fontTF, null ) );
		fdBins.setValue( param.sift.fdBins );
		customComponents.add( fdBins );
		advancedSIFTComponents.add( fdBins );

		// ratio of distance
		rod = new JFormattedTextField( formatterDouble01 );
		rod.setBorder( new TitledBorder(null, "Feature descriptor closest/next closest ratio (-rod)", TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION, fontTF, null ) );
		rod.setValue( param.rod );
		customComponents.add(rod);
		advancedSIFTComponents.add(rod);

		// octave steps
		steps = new JFormattedTextField( formatter );
		steps.setBorder( new TitledBorder(null, "Steps per Scale Octave (-so)", TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION, fontTF, null ) );
		steps.setValue( param.sift.steps );
		customComponents.add(steps);
		advancedSIFTComponents.add(steps);

		// initialSigma
		initialSigma = new JFormattedTextField( formatterDouble );
		initialSigma.setBorder( new TitledBorder(null, "Initial sigma of each Scale Octave (-is)", TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION, fontTF, null ) );
		initialSigma.setValue( param.sift.initialSigma );
		customComponents.add(initialSigma);
		advancedSIFTComponents.add(initialSigma);

		// min octave size
		minOS = new JFormattedTextField( formatter );
		minOS.setBorder( new TitledBorder(null, "Min Octave size (-minOS)", TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION, fontTF, null ) );
		minOS.setValue( param.sift.minOctaveSize );
		customComponents.add(minOS);
		advancedSIFTComponents.add(minOS);

		// max octave size
		maxOS = new JFormattedTextField( formatter );
		maxOS.setBorder( new TitledBorder(null, "Max Octave size (-maxOS)", TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION, fontTF, null ) );
		maxOS.setValue( param.sift.maxOctaveSize );
		advancedSIFTComponents.add(maxOS);

		// min inlier ratio
		ilr = new JFormattedTextField( formatterDouble01 );
		ilr.setBorder( new TitledBorder(null, "RANSAC minimal inlier ratio (-ilr)", TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION, fontTF, null ) );
		ilr.setValue( param.minInlierRatio );
		customComponents.add(ilr);
		advancedSIFTComponents.add(ilr);

		// iterations
		it = new JFormattedTextField( formatter );
		it.setBorder( new TitledBorder(null, "RANSAC iterations (-it)", TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION, fontTF, null ) );
		it.setValue( param.iterations );
		customComponents.add(it);
		advancedSIFTComponents.add(it);

		// bi-directional matching
		biDirectional = new JCheckBox( "Bi-directional alignment (-bidir)" );
		biDirectional.setSelected( param.biDirectional );
		biDirectional.setEnabled( true );
		biDirectional.setBorder( BorderFactory.createEmptyBorder( 5, 0, 0, 0 ) );
		customComponents.add(biDirectional);
		advancedSIFTComponents.add(biDirectional);

		final JSeparator j2 = new JSeparator();// j2.setBackground( Color.gray );
		advancedSIFTComponents.add( j2 );

		//
		// set sift preset box to custom once one of the values is manually changed
		//
		final AtomicBoolean triggedChange = new AtomicBoolean( false );

		customComponents.forEach( c -> {
			if ( JFormattedTextField.class.isInstance( c ))
				((JFormattedTextField)c).addPropertyChangeListener( e -> {
					if ( !triggedChange.get() && e.getOldValue() != null && e.getNewValue() != null && e.getOldValue() != e.getNewValue() && box.getSelectedIndex() != 4 )
					{
						box.setSelectedIndex( 4 );
					}
				} );
			else if ( JCheckBox.class.isInstance( c ) )
				((JCheckBox)c).addChangeListener( e -> { if ( !triggedChange.get() && box.getSelectedIndex() != 4 ) box.setSelectedIndex( 4 ); } );
			else if ( BoundedValuePanel.class.isInstance( c ) )
				((BoundedValuePanel)c).changeListeners().add( () -> { if ( !triggedChange.get() && box.getSelectedIndex() != 4 ) box.setSelectedIndex( 4 ); } );
		});

		// transform listener for max octave size
		stimcard.bdvhandle().getViewerPanel().transformListeners().add( l -> SwingUtilities.invokeLater( () -> updateMaxOctaveSize() ) );

		// advanced options listener (changes menu)
		advancedOptions.addChangeListener( e ->
		{
			SwingUtilities.invokeLater( () ->
			{
				if ( !customModeSIFT )
				{
					// change to advanced mode
					AtomicInteger cc = new AtomicInteger(componentCount);
					triggedChange.set(true);
					advancedSIFTComponents.forEach( c -> panel.add(c, "span,growx,pushy", cc.getAndIncrement() ));
					panel.updateUI();
	
					triggedChange.set( false );
					customModeSIFT = true;
				}
				else
				{
					// change to simple mode
					advancedSIFTComponents.forEach( c -> panel.remove( c ) );
					customModeSIFT = false;
				}
			});
		});

		// advanced menu listener (changes menu)
		box.addActionListener( e -> {

			if ( box.getSelectedIndex() < 4 )
			{
				SwingUtilities.invokeLater( () ->
				{
					// update all values to the specific preset
					param.setIntrinsicParameters( SIFTPreset.values()[ box.getSelectedIndex() ] );
	
					// make sure we do not set the box back to index=4 because of the listeners triggered by changing values below
					triggedChange.set( true );
	
					// update all GUI elements (except error, maxoctavesize)
					fdSize.setValue( param.sift.fdSize );
					fdBins.setValue( param.sift.fdBins );
					rod.setValue( param.rod );
					minOS.setValue( param.sift.minOctaveSize );
					steps.setValue( param.sift.steps );
					initialSigma.setValue( param.sift.initialSigma );
					ilr.setValue( param.minInlierRatio );
					it.setValue( param.iterations );
					biDirectional.setSelected( param.biDirectional );
	
					BoundedValue b = new BoundedValue(
							Math.min( inliersPerGeneSlider.getValue().getMinBound(), param.minInliersGene ),
							Math.max( inliersPerGeneSlider.getValue().getMaxBound(), param.minInliersGene ),
							param.minInliersGene );
					inliersPerGeneSlider.setValue( b );
	
					b = new BoundedValue(
							Math.min( inliersSlider.getValue().getMinBound(), param.minInliersTotal ),
							Math.max( inliersSlider.getValue().getMaxBound(), param.minInliersTotal ),
							param.minInliersTotal );
					inliersSlider.setValue( b );
	
					panel.updateUI();
	
					triggedChange.set( false );
				});
			}
		});

		// overlay listener
		overlayInliers.addChangeListener( e ->
		{
			if ( !overlayInliers.isSelected() )
			{
				stimcard.bdvhandle().getViewerPanel().renderTransformListeners().remove( siftoverlay );
				stimcard.bdvhandle().getViewerPanel().getDisplay().overlays().remove( siftoverlay );
			}
			else
			{
				stimcard.bdvhandle().getViewerPanel().renderTransformListeners().add( siftoverlay );
				stimcard.bdvhandle().getViewerPanel().getDisplay().overlays().add( siftoverlay );
			}
			stimcard.bdvhandle().getViewerPanel().requestRepaint();
		});

		// make sure inliers are rounded
		inliersSlider.changeListeners().add( () -> {
			final int value = (int)Math.round( inliersSlider.getValue().getValue() );
			final BoundedValue b = new BoundedValue(
					Math.min( inliersSlider.getValue().getMinBound(), value ),
					Math.max( inliersSlider.getValue().getMaxBound(), value ),
					value );
			SwingUtilities.invokeLater( () -> inliersSlider.setValue( b ) );
		} );
		inliersPerGeneSlider.changeListeners().add( () -> {
			final int value = (int)Math.round( inliersPerGeneSlider.getValue().getValue() );
			final BoundedValue b = new BoundedValue(
					Math.min( inliersPerGeneSlider.getValue().getMinBound(), value ),
					Math.max( inliersPerGeneSlider.getValue().getMaxBound(), value ),
					value );
			SwingUtilities.invokeLater( () -> inliersPerGeneSlider.setValue( b ) );
		} );

		// disable lambdas if no regularization is selected
		boxModelRANSAC2.addActionListener( e ->
			SwingUtilities.invokeLater( () -> {
				tfRANSAC.setEnabled( boxModelRANSAC2.getSelectedIndex() != 0 );
				labelRANSACReg.setForeground( boxModelRANSAC2.getSelectedIndex() == 0 ? Color.gray : Color.black );
			})
		);

		boxModelFinal2.addActionListener( e -> 
			SwingUtilities.invokeLater( () ->
			{
				tfFinal.setEnabled( boxModelFinal2.getSelectedIndex() != 0 );
				labelFinalReg.setForeground( boxModelFinal2.getSelectedIndex() == 0 ? Color.gray : Color.black );
			})
		);

		//
		// Run SIFT alignment
		//
		run.addActionListener( l ->
		{
			if ( siftThread != null )
			{
				// request to cancel
				siftThread.stop();
				new ArrayList<>( threads ).forEach( t -> t.stop() );

				// wait a bit
				try { Thread.sleep( 500 ); } catch (InterruptedException e1) {}

				siftThread = null;
				threads.clear();

				SwingUtilities.invokeLater( () ->
				{
					reEnableControls();

					if ( manualCard != null )
					{
						manualCard.setTransformGUI( AlignTools.modelToAffineTransform2D( previousModel ) );
						manualCard.reEnableControlsExternal();
					}
				});

				setModel( previousModel );
				stimcard.applyTransformationToBDV( true );

				return;
			}

			// take the previous model simply from the first AddedGene list
			previousModel = (Affine2D)((Model)stimcard.sourceData().values().iterator().next().get( 0 ).currentModel()).copy();

			SwingUtilities.invokeLater( () ->
			{
				siftoverlay.setInliers( new ArrayList<>() );
				stimcard.bdvhandle().getViewerPanel().renderTransformListeners().remove( siftoverlay );
				stimcard.bdvhandle().getViewerPanel().getDisplay().overlays().remove( siftoverlay );
				run.setForeground( Color.red );
				run.setFont( run.getFont().deriveFont( Font.BOLD ) );
				run.setText( "  Cancel SIFT Align  " );
				cmdLine.setEnabled( false );
				reset.setEnabled( false );
				saveTransform.setEnabled( false );
				overlayInliers.setEnabled( false );
				bar.setValue( 1 );
				if ( icpCard != null )
				{
					icpCard.cmdLine.setEnabled( false );
					icpCard.run.setEnabled( false );
					icpCard.reset.setEnabled( false );
					icpCard.saveTransform.setEnabled( false );
				}
				if ( manualCard != null )
					manualCard.disableControlsExternal();
			});

			siftThread = new Thread( () ->
			{
				AddedGene.updateRemainingSources( stimcard.bdvhandle().getViewerPanel().state(), stimcard.geneToBDVSource(), stimcard.sourceData() );

				final Pair<Model<?>, Model<?>> modelPair = extractParamtersFromGUI( param );

				System.out.println( "Running SIFT align with the following parameters: \n" + param.toString() );
				System.out.println( "RANSAC model: " + optionsModel[ boxModelRANSAC1.getSelectedIndex() ] + ", regularizer: " + optionsModelReg[ boxModelRANSAC2.getSelectedIndex() ] + ", lambda=" + tfRANSAC.getText() );
				System.out.println( "FINAL model: " + optionsModel[ boxModelFinal1.getSelectedIndex() ] + ", regularizer: " + optionsModelReg[ boxModelFinal2.getSelectedIndex() ] + ", lambda=" + tfFinal.getText() );

				System.out.println( modelPair.getA().getClass().getSimpleName() );
				System.out.println( modelPair.getB().getClass().getSimpleName() );

				final boolean visResult = false;
				final double[] progressBarValue = new double[] { 1.0 };

				threads.clear();

				final SiftMatch match = PairwiseSIFT.pairwiseSIFT(
						stimcard.data().get( 0 ).data(),
						stimcard.data().get( 0 ).transform(),
						dataset1,
						stimcard.data().get( 1 ).data(),
						stimcard.data().get( 1 ).transform(),
						dataset2,
						(Affine2D & Model)modelPair.getA(), (Affine2D & Model)modelPair.getB(),
						new ArrayList<>( stimcard.geneToBDVSource().keySet() ),
						param,
						visResult, service, threads, v -> {
							synchronized ( this ) {
								progressBarValue[ 0 ] += v;
								bar.setValue( (int)Math.round( progressBarValue[ 0 ] ));
							}
						});

				System.out.println( match.getNumInliers() + "/" + match.getNumCandidates() );

				// 
				// apply transformations
				//
				if ( match.getInliers().size() > 0 )
				{
					try
					{
						modelPair.getB().fit( match.getInliers() );

						setModel( (Affine2D)modelPair.getB() );
						stimcard.applyTransformationToBDV( true );

						System.out.println( "2D model: " + modelPair.getB() );
						System.out.println( "2D transform: " + stimcard.sourceData().values().iterator().next().get( 0 ).currentModel2D() );
						System.out.println( "3D viewer transform: " + stimcard.sourceData().values().iterator().next().get( 0 ).currentModel3D() );

						SwingUtilities.invokeLater( () ->
						{
							if ( manualCard != null )
								manualCard.setTransformGUI( AlignTools.modelToAffineTransform2D( (Affine2D)modelPair.getB() ) );
						} );
					}
					catch ( Exception e )
					{
						e.printStackTrace();
					}

					// Overlay detections
					// TODO: they seem off, do we forget the boundary we added to the image???
					siftoverlay.setInliers( match.getInliers() );

					// Remember all genes with inliers
					genesWithInliers.clear();
					for ( final PointMatch pm : match.getInliers() )
						genesWithInliers.add(((PointST) pm.getP1()).getGene() );

					lastMaxError = param.maxError;

					System.out.println( "genes with inliers: ");
					genesWithInliers.forEach( s -> System.out.print( s + " " ) );
					System.out.println();

					SwingUtilities.invokeLater( () ->
					{
						overlayInliers.setSelected( true );
						overlayInliers.setEnabled( true );
					});
				}
				else
				{
					setModel( previousModel );
					stimcard.applyTransformationToBDV( true );

					lastMaxError = Double.NaN;

					SwingUtilities.invokeLater( () ->
					{
						if ( manualCard != null )
							manualCard.setTransformGUI( AlignTools.modelToAffineTransform2D( previousModel ) );
	
						siftoverlay.setInliers( new ArrayList<>() );
						genesWithInliers.clear();
						overlayInliers.setEnabled( false );
					});
				}

				SwingUtilities.invokeLater( () ->
				{
					if ( icpCard != null )
					{
						//System.out.println( "Updating ICP to " + genesWithInliers.size() );
						icpCard.siftResults().setText( STIMCardAlignICP.getSIFTResultLabelText( genesWithInliers.size() ) );
						icpCard.getPanel().updateUI();
					}
	
					reEnableControls();
	
					if ( manualCard != null )
						manualCard.reEnableControlsExternal();
				});

				stimcard.bdvhandle().getViewerPanel().requestRepaint();
				siftThread = null;
			});

			siftThread.start();
		});

		//
		// Save transform
		//
		saveTransform.addActionListener( l -> saveTransforms() );

		//
		// Reset transform
		//
		reset.addActionListener( l ->
		{
			setModel( new AffineModel2D() );
			stimcard.applyTransformationToBDV( true );

			lastMaxError = Double.NaN;

			siftoverlay.setInliers( new ArrayList<>() );
			genesWithInliers.clear();
			overlayInliers.setEnabled( false );

			if ( icpCard != null )
			{
				//System.out.println( "Updating ICP to " + genesWithInliers.size() );
				icpCard.siftResults().setText( STIMCardAlignICP.getSIFTResultLabelText( genesWithInliers.size() ) );
				icpCard.getPanel().updateUI();
			}

			if ( manualCard != null )
				manualCard.setTransformGUI( AlignTools.modelToAffineTransform2D( new AffineModel2D() ) );

			System.out.println( "reset transformations.");
		});

		//
		// Return command line paramters for the last SIFT align run ...
		//
		cmdLine.addActionListener( l -> 
		{
			final SIFTParam paramsCmdLine = new SIFTParam();
			final Pair<Model<?>, Model<?>> modelPair = extractParamtersFromGUI( paramsCmdLine );

			
			// TODO ...
		});
	}

	public HashSet< String> genesWithInliers() { return genesWithInliers; }
	public double lastMaxError() { return lastMaxError; }

	public void saveTransforms()
	{
		// the model and datasets for all genes are the same, we can just pick one
		final AffineTransform2D currentTransform = stimcard.sourceData().values().iterator().next().get( 0 ).currentModel2D();
		final String dataset = stimcard.sourceData().values().iterator().next().get( 0 ).dataset();
		final AffineTransform2D initialTransform = stimcard.data().get( 0 ).transform();

		final AffineTransform2D finalTransform = initialTransform.copy().preConcatenate( currentTransform );

		System.out.println( "Initial transformation: " + initialTransform );
		System.out.println( "SIFT transformation: " + currentTransform );
		System.out.println( "Final transformation: " + finalTransform );

		// the input path is the same for all AddedGene objects, we can just pick one
		final String path = new File( stimcard.inputPath(), dataset).getAbsolutePath();

		try
		{
			final SpatialDataIO sdout = SpatialDataIO.open( path, service);
			final N5Writer writer = (N5Writer) sdout.ioSupplier().get();
			
			sdout.writeTransformation( writer, finalTransform, SpatialDataIO.transformFieldName );

			System.out.println( "Written final transformation to: '" + path + "'");
		}
		catch (IOException e1)
		{
			System.out.println( "ERROR writing transformation to: '" + path + "': "+ e1);
			e1.printStackTrace();
		}
	}

	public void setModel( final Affine2D< ? > previousModel )
	{
		stimcard.sourceData().values().forEach( list ->
		{
			list.get( 0 ).setCurrentModel( previousModel );

			for ( int i = 1; i < list.size(); ++i )
				list.get( i ).setCurrentModel( new AffineModel2D() );
		});
	}

	public void setTransform( final AffineTransform2D previousModel )
	{
		stimcard.sourceData().values().forEach( list ->
		{
			list.get( 0 ).setCurrentModel2D( previousModel );

			for ( int i = 1; i < list.size(); ++i )
				list.get( i ).setCurrentModel2D( new AffineTransform2D() );
		});
	}

	public void setTransform3D( final AffineTransform3D previousModel )
	{
		stimcard.sourceData().values().forEach( list ->
		{
			list.get( 0 ).setCurrentModel3D( previousModel );

			for ( int i = 1; i < list.size(); ++i )
				list.get( i ).setCurrentModel3D( new AffineTransform3D() );
		});
	}

	public void updateMaxOctaveSize()
	{
		maxOS.setValue( this.param.sift.maxOctaveSize = getMaxOctaveSize(
				stimcard.data().get( 0 ).data(),
				stimcard.data().get( 0 ).transform(),
				stimcard.data().get( 1 ).data(),
				stimcard.data().get( 1 ).transform(),
				stimcard.currentScale() ) );
	}

	protected Pair<Model<?>, Model<?>> extractParamtersFromGUI( final SIFTParam param )
	{
		param.setIntrinsicParameters(
				(int)Integer.parseInt( fdSize.getText().trim() ),
				(int)Integer.parseInt( fdBins.getText().trim() ),
				(int)Integer.parseInt( minOS.getText().trim() ),
				(int)Integer.parseInt( steps.getText().trim() ),
				Double.parseDouble( initialSigma.getText().trim() ),
				biDirectional.isSelected(),
				Double.parseDouble( rod.getText().trim() ),
				Double.parseDouble( ilr.getText().trim() ),
				(int)Math.round( inliersPerGeneSlider.getValue().getValue() ),
				(int)Math.round( inliersSlider.getValue().getValue() ),
				(int)Integer.parseInt( it.getText().trim() ) );

		param.setDatasetParameters(
				maxErrorSlider.getValue().getValue(),
				stimcard.currentScale(),
				(int)Integer.parseInt( maxOS.getText().trim() ),
				stimcardFilter.filterFactories(),
				stimcard.currentDisplayMode(), stimcard.currentRenderingFactor(), stimcard.currentBrightnessMin(), stimcard.currentBrightnessMax() );

		final double lambda1 = Double.parseDouble( tfRANSAC.getText().trim() );
		final double lambda2 = Double.parseDouble( tfFinal.getText().trim() );

		final Model model1 = getModelFor( boxModelRANSAC1.getSelectedIndex(), boxModelRANSAC2.getSelectedIndex(), lambda1 );
		final Model model2 = getModelFor( boxModelFinal1.getSelectedIndex(), boxModelFinal2.getSelectedIndex(), lambda2 );

		return new ValuePair<>( model1, model2 );
	}

	protected void reEnableControls()
	{
		cmdLine.setEnabled( true );
		reset.setEnabled( true );
		saveTransform.setEnabled( true );
		run.setText( "Run SIFT Alignment" );
		run.setFont( run.getFont().deriveFont( Font.BOLD ) );
		run.setForeground( Color.black );
		bar.setValue( 0 );

		if ( icpCard != null )
		{
			icpCard.cmdLine.setEnabled( true );
			icpCard.run.setEnabled( true );
			icpCard.reset.setEnabled( false );
			icpCard.saveTransform.setEnabled( false );
		}
	}

	protected int getMaxOctaveSize( final STData data1, final AffineTransform2D transform1, final STData data2, final AffineTransform2D transform2, final double scale )
	{
		final AffineTransform2D tScale = new AffineTransform2D();
		tScale.scale( scale );

		final AffineTransform2D t1 = transform1.copy().preConcatenate( tScale );
		final AffineTransform2D t2 = transform2.copy().preConcatenate( tScale );

		final Interval finalInterval = PairwiseSIFT.intervalForAlignment( data1, t1, data2, t2 );
		final int maxSize = (int)Math.max( finalInterval.dimension( 0 ), finalInterval.dimension( 1 ) );
		final int po2 = (int)Math.pow( 2, (maxSize == 0 ? 0 : 32 - Integer.numberOfLeadingZeros( maxSize - 1 ) ) );

		return po2;
	}

	public static NumberFormatter formatterInt()
	{
		final NumberFormatter formatter = new NumberFormatter(NumberFormat.getInstance());
		formatter.setValueClass(Integer.class);
		formatter.setMinimum(1);
		formatter.setMaximum(Integer.MAX_VALUE);
		formatter.setFormat(new DecimalFormat("#"));
		formatter.setAllowsInvalid(true);

		return formatter;
	}

	public static NumberFormatter formatterDouble()
	{
		final NumberFormatter formatterDouble = new NumberFormatter(NumberFormat.getInstance());
		formatterDouble.setValueClass(Double.class);
		formatterDouble.setMinimum(0);
		formatterDouble.setMaximum(Double.MAX_VALUE);
		formatterDouble.setAllowsInvalid(true);

		return formatterDouble;
	}

	public static NumberFormatter formatterDouble01()
	{
		final NumberFormatter formatterDouble01 = new NumberFormatter(NumberFormat.getInstance());
		formatterDouble01.setValueClass(Double.class);
		formatterDouble01.setMinimum(0);
		formatterDouble01.setMaximum(1);
		formatterDouble01.setAllowsInvalid(true);

		return formatterDouble01;
	}

	protected static Model<?> getModelFor( final int modelIndex, final int regIndex, final double lambda )
	{
		if ( regIndex == 0 )
		{
			if ( modelIndex == 0 )
				return new TranslationModel2D();
			else if ( modelIndex == 1 )
				return new RigidModel2D();
			else if ( modelIndex == 2 )
				return new SimilarityModel2D();
			else if ( modelIndex == 3 )
				return new AffineModel2D();
			else
				throw new RuntimeException( "Unknown model index: "+ modelIndex );
		}
		else if ( regIndex == 1 )
		{
			if ( modelIndex == 0 )
				return new TranslationModel2D();
			else if ( modelIndex == 1 )
				return new InterpolatedAffineModel2D<TranslationModel2D, RigidModel2D>( new TranslationModel2D(), new RigidModel2D(), lambda );
			else if ( modelIndex == 2 )
				return new InterpolatedAffineModel2D<TranslationModel2D, SimilarityModel2D>( new TranslationModel2D(), new SimilarityModel2D(), lambda );
			else if ( modelIndex == 3 )
				return new InterpolatedAffineModel2D<TranslationModel2D, AffineModel2D>( new TranslationModel2D(), new AffineModel2D(), lambda );
			else
				throw new RuntimeException( "Unknown model index: "+ modelIndex );
		}
		else if ( regIndex == 2 )
		{
			if ( modelIndex == 0 )
				return new InterpolatedAffineModel2D<RigidModel2D, TranslationModel2D>( new RigidModel2D(), new TranslationModel2D(), lambda );
			else if ( modelIndex == 1 )
				return new RigidModel2D();
			else if ( modelIndex == 2 )
				return new InterpolatedAffineModel2D<RigidModel2D, SimilarityModel2D>( new RigidModel2D(), new SimilarityModel2D(), lambda );
			else if ( modelIndex == 3 )
				return new InterpolatedAffineModel2D<RigidModel2D, AffineModel2D>( new RigidModel2D(), new AffineModel2D(), lambda );
			else
				throw new RuntimeException( "Unknown model index: "+ modelIndex );
		}
		else if ( regIndex == 3 )
		{
			if ( modelIndex == 0 )
				return new InterpolatedAffineModel2D<SimilarityModel2D, TranslationModel2D>( new SimilarityModel2D(), new TranslationModel2D(), lambda );
			else if ( modelIndex == 1 )
				return new InterpolatedAffineModel2D<SimilarityModel2D, RigidModel2D>( new SimilarityModel2D(), new RigidModel2D(), lambda );
			else if ( modelIndex == 2 )
				return new SimilarityModel2D();
			else if ( modelIndex == 3 )
				return new InterpolatedAffineModel2D<SimilarityModel2D, AffineModel2D>( new SimilarityModel2D(), new AffineModel2D(), lambda );
			else
				throw new RuntimeException( "Unknown model index: "+ modelIndex );
		}
		else if ( regIndex == 4 )
		{
			if ( modelIndex == 0 )
				return new InterpolatedAffineModel2D<AffineModel2D, TranslationModel2D>( new AffineModel2D(), new TranslationModel2D(), lambda );
			else if ( modelIndex == 1 )
				return new InterpolatedAffineModel2D<AffineModel2D, RigidModel2D>( new AffineModel2D(), new RigidModel2D(), lambda );
			else if ( modelIndex == 2 )
				return new InterpolatedAffineModel2D<AffineModel2D, SimilarityModel2D>( new AffineModel2D(), new SimilarityModel2D(), lambda );
			else if ( modelIndex == 3 )
				return new AffineModel2D();
			else
				throw new RuntimeException( "Unknown model index: "+ modelIndex );
		}
		else
			throw new RuntimeException( "Unknown regularizer model index: "+ modelIndex );
	}

	public void setICPCard( final STIMCardAlignICP icpCard ) { this.icpCard = icpCard; }
	public void setManualAlignCard( final STIMCardManualAlign manualCard ) { this.manualCard = manualCard; }
	public JPanel getPanel() { return panel; }
}
