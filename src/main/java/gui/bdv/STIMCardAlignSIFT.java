package gui.bdv;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.BorderFactory;
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
import javax.swing.border.TitledBorder;
import javax.swing.text.NumberFormatter;

import align.PairwiseSIFT;
import align.PointST;
import align.SIFTParam;
import align.SIFTParam.SIFTPreset;
import align.SiftMatch;
import bdv.viewer.SynchronizedViewerState;
import cmd.InteractiveAlignment.AddedGene;
import data.STData;
import data.STDataUtils;
import gui.DisplayScaleOverlay;
import gui.overlay.SIFTOverlay;
import mpicbg.models.Affine2D;
import mpicbg.models.AffineModel2D;
import mpicbg.models.InterpolatedAffineModel2D;
import mpicbg.models.Model;
import mpicbg.models.PointMatch;
import mpicbg.models.RigidModel2D;
import mpicbg.models.SimilarityModel2D;
import mpicbg.models.TranslationModel2D;
import net.imglib2.Interval;
import net.miginfocom.swing.MigLayout;
import util.BoundedValue;
import util.BoundedValuePanel;

public class STIMCardAlignSIFT
{
	private final JPanel panel;
	private final SIFTOverlay siftoverlay;
	private final DisplayScaleOverlay overlay;
	private final STIMCard stimcard;
	private final JFormattedTextField maxOS;

	private Thread siftThread = null;
	private List< Thread > threads = new ArrayList<>();

	final SIFTParam param;
	final static String optionsSIFT[] = { "Fast", "Normal", "Thorough", "Very thorough", "Custom ..." };
	private boolean customModeSIFT = false; // we always start with "normal" for 

	private HashSet< String > genesWithInliers = new HashSet<>();
	private double lastMaxError = Double.NaN;

	final static String optionsModel[] = { "Translation", "Rigid", "Similarity", "Affine" };
	final static String optionsModelReg[] = { "No Reg.", "Transl.", "Rigid", "Simil.", "Affine" };

	public STIMCardAlignSIFT(
			final String dataset1,
			final String dataset2,
			final DisplayScaleOverlay overlay,
			final STIMCard stimcard,
			final STIMCardFilter stimcardFilter,
			final ExecutorService service )
	{
		// setup initial parameters
		final int initSIFTPreset = 1; // NORMAL
		this.param = new SIFTParam();
		this.param.setIntrinsicParameters( SIFTPreset.values()[ initSIFTPreset ] );

		final Interval interval = STDataUtils.getCommonInterval( stimcard.data1().data(), stimcard.data2().data() );
		this.param.maxError = Math.max( interval.dimension( 0 ), interval.dimension( 1 ) ) / 20;
		this.param.sift.maxOctaveSize = 1024; // TODO find out

		this.siftoverlay = new SIFTOverlay( new ArrayList<>(), stimcard.bdvhandle() );
		this.overlay = overlay;
		this.stimcard = stimcard;
		this.panel = new JPanel(new MigLayout("gap 0, ins 5 5 5 5, fill", "[right][grow]", "center"));

		// lists for components
		final List< Component > customComponents = new ArrayList<>();
		final List< Component > advancedSIFTComponents = new ArrayList<>();

		// setup formatters
		final NumberFormatter formatter = new NumberFormatter(NumberFormat.getInstance());
		formatter.setValueClass(Integer.class);
		formatter.setMinimum(1);
		formatter.setMaximum(Integer.MAX_VALUE);
		formatter.setFormat(new DecimalFormat("#"));
		formatter.setAllowsInvalid(true);

		final NumberFormatter formatterDouble = new NumberFormatter(NumberFormat.getInstance());
		formatterDouble.setValueClass(Double.class);
		formatterDouble.setMinimum(0);
		formatterDouble.setMaximum(Double.MAX_VALUE);
		formatterDouble.setAllowsInvalid(true);

		final NumberFormatter formatterDouble01 = new NumberFormatter(NumberFormat.getInstance());
		formatterDouble01.setValueClass(Double.class);
		formatterDouble01.setMinimum(0);
		formatterDouble01.setMaximum(1);
		formatterDouble01.setAllowsInvalid(true);

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
		final BoundedValuePanel maxErrorSlider = new BoundedValuePanel(new BoundedValue(0, Math.round( Math.ceil( param.maxError * 2 ) ), param.maxError ));
		maxErrorSlider.setBorder(null);
		final JLabel maxErrorLabel = new JLabel("max. error (px)");
		panel.add(maxErrorLabel, "aligny baseline");
		panel.add(maxErrorSlider, "growx, wrap");

		// inliers
		final BoundedValuePanel inliersSlider = new BoundedValuePanel(new BoundedValue(0, param.minInliersTotal * 2, param.minInliersTotal ));
		inliersSlider.setBorder(null);
		customComponents.add( inliersSlider );
		final JLabel inliersLabel = new JLabel("min inliers (total)");
		inliersLabel.setFont( font );
		inliersLabel.setBorder( null );
		panel.add(inliersLabel, "aligny baseline");
		panel.add(inliersSlider, "growx, wrap");

		// inliersPerGene
		final BoundedValuePanel inliersPerGeneSlider = new BoundedValuePanel(new BoundedValue(0, param.minInliersGene * 2, param.minInliersGene ));
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
		final JComboBox< String > boxModelRANSAC1 = new JComboBox< String > (optionsModel);
		boxModelRANSAC1.setSelectedIndex( 1 );
		final JLabel boxModeRANSACLabel1 = new JLabel("RANSAC model ");
		panel.add( boxModeRANSACLabel1, "aligny baseline, sy 2" );
		panel.add( boxModelRANSAC1, "growx, wrap" );
		final JPanel panRANSAC = new JPanel( new MigLayout("gap 0, ins 0 0 0 0, fill", "[right][grow]", "center") );
		final JComboBox< String > boxModelRANSAC2 = new JComboBox< String > (optionsModelReg);
		boxModelRANSAC2.setSelectedIndex( 0 );
		panRANSAC.add( boxModelRANSAC2 );
		final JLabel labelRANSACReg = new JLabel( "λ=" );
		panRANSAC.add( labelRANSACReg, "alignx right" );
		final JTextField tfRANSAC = new JTextField( "0.1" );
		tfRANSAC.setEnabled( false );
		labelRANSACReg.setForeground( Color.gray );
		panRANSAC.add( tfRANSAC, "growx" );
		panRANSAC.setBorder( BorderFactory.createEmptyBorder(0,0,5,0));
		panel.add( panRANSAC, "growx, wrap" );

		// Panel for FINAL MODEL
		final JComboBox< String > boxModelFinal1 = new JComboBox< String > (optionsModel);
		boxModelFinal1.setSelectedIndex( 1 );
		final JLabel boxModeFinalLabel1 = new JLabel("Final model ");
		panel.add( boxModeFinalLabel1, "aligny baseline, sy 2" );
		panel.add( boxModelFinal1, "growx, wrap" );
		final JPanel panFinal = new JPanel( new MigLayout("gap 0, ins 0 0 0 0, fill", "[right][grow]", "center") );
		final JComboBox< String > boxModelFinal2 = new JComboBox< String > (optionsModelReg);
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
		final JCheckBox overlayInliers = new JCheckBox( "Overlay SIFT features" );
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
		final JButton run = new JButton("Run SIFT alignment");
		panel.add(run, "growx, wrap");

		//
		// for the advanced mode (move to separate object?)
		final JSeparator j1 = new JSeparator();// j1.setBackground( Color.gray );
		advancedSIFTComponents.add( j1 );

		final Font fontTF = inliersLabel.getFont().deriveFont( 9f );

		// fdsize
		final JFormattedTextField fdSize = new JFormattedTextField( formatter );
		fdSize.setBorder( new TitledBorder(null, "Feature descriptor size (-fdSize)", TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION, fontTF, null ) );
		fdSize.setValue( param.sift.fdSize );
		customComponents.add( fdSize );
		advancedSIFTComponents.add( fdSize );

		// fdBins
		final JFormattedTextField fdBins = new JFormattedTextField( formatter );
		fdBins.setBorder( new TitledBorder(null, "Feature descriptor orientation bins (-fdBins)", TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION, fontTF, null ) );
		fdBins.setValue( param.sift.fdBins );
		customComponents.add( fdBins );
		advancedSIFTComponents.add( fdBins );

		// ratio of distance
		final JFormattedTextField rod = new JFormattedTextField( formatterDouble01 );
		rod.setBorder( new TitledBorder(null, "Feature descriptor closest/next closest ratio (-rod)", TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION, fontTF, null ) );
		rod.setValue( param.rod );
		customComponents.add(rod);
		advancedSIFTComponents.add(rod);

		// octave steps
		final JFormattedTextField steps = new JFormattedTextField( formatter );
		steps.setBorder( new TitledBorder(null, "Steps per Scale Octave (-so)", TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION, fontTF, null ) );
		steps.setValue( param.sift.steps );
		customComponents.add(steps);
		advancedSIFTComponents.add(steps);

		// initialSigma
		final JFormattedTextField initialSigma = new JFormattedTextField( formatterDouble );
		initialSigma.setBorder( new TitledBorder(null, "Initial sigma of each Scale Octave (-is)", TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION, fontTF, null ) );
		initialSigma.setValue( param.sift.initialSigma );
		customComponents.add(initialSigma);
		advancedSIFTComponents.add(initialSigma);

		// min octave size
		final JFormattedTextField minOS = new JFormattedTextField( formatter );
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
		final JFormattedTextField ilr = new JFormattedTextField( formatterDouble01 );
		ilr.setBorder( new TitledBorder(null, "RANSAC minimal inlier ratio (-ilr)", TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION, fontTF, null ) );
		ilr.setValue( param.minInlierRatio );
		customComponents.add(ilr);
		advancedSIFTComponents.add(ilr);

		// iterations
		final JFormattedTextField it = new JFormattedTextField( formatter );
		it.setBorder( new TitledBorder(null, "RANSAC iterations (-it)", TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION, fontTF, null ) );
		it.setValue( param.iterations );
		customComponents.add(it);
		advancedSIFTComponents.add(it);

		// bi-directional matching
		final JCheckBox biDirectional = new JCheckBox( "Bi-directional alignment (-bidir)" );
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
		stimcard.bdvhandle().getViewerPanel().transformListeners().add( l -> updateMaxOctaveSize() );

		// advanced options listener (changes menu)
		advancedOptions.addChangeListener( e ->
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

		// advanced menu listener (changes menu)
		box.addActionListener( e -> {

			if ( box.getSelectedIndex() < 4 )
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
			inliersSlider.setValue( b );
		} );
		inliersPerGeneSlider.changeListeners().add( () -> {
			final int value = (int)Math.round( inliersPerGeneSlider.getValue().getValue() );
			final BoundedValue b = new BoundedValue(
					Math.min( inliersPerGeneSlider.getValue().getMinBound(), value ),
					Math.max( inliersPerGeneSlider.getValue().getMaxBound(), value ),
					value );
			inliersPerGeneSlider.setValue( b );
		} );

		// disable lambdas if no regularization is selected
		boxModelRANSAC2.addActionListener( e -> {
			tfRANSAC.setEnabled( boxModelRANSAC2.getSelectedIndex() != 0 );
			labelRANSACReg.setForeground( boxModelRANSAC2.getSelectedIndex() == 0 ? Color.gray : Color.black );
		} );

		boxModelFinal2.addActionListener( e -> {
			tfFinal.setEnabled( boxModelFinal2.getSelectedIndex() != 0 );
			labelFinalReg.setForeground( boxModelFinal2.getSelectedIndex() == 0 ? Color.gray : Color.black );
		} );

		//
		// Run SIFT alignment
		//
		run.addActionListener( l ->
		{
			if ( siftThread != null )
			{
				// request to cancel
				siftThread.stop();
				threads.forEach( t -> t.stop() );

				// wait a bit
				try { Thread.sleep( 1000 ); } catch (InterruptedException e1) {}

				cmdLine.setEnabled( true );
				run.setText( "Run SIFT alignment" );
				run.setFont( run.getFont().deriveFont( Font.PLAIN ) );
				run.setForeground( Color.black );
				bar.setValue( 0 );
				siftThread = null;
				threads.clear();
				return;
			}

			siftoverlay.setInliers( new ArrayList<>() );
			stimcard.bdvhandle().getViewerPanel().renderTransformListeners().remove( siftoverlay );
			stimcard.bdvhandle().getViewerPanel().getDisplay().overlays().remove( siftoverlay );
			run.setForeground( Color.red );
			run.setFont( run.getFont().deriveFont( Font.BOLD ) );
			run.setText( "Cancel SIFT run");
			cmdLine.setEnabled( false );
			overlayInliers.setEnabled( false );
			bar.setValue( 1 );

			siftThread = new Thread( () ->
			{
				final SynchronizedViewerState state = stimcard.bdvhandle().getViewerPanel().state();
				AddedGene.updateRemainingSources( state, stimcard.geneToBDVSource(), stimcard.sourceData() );

				final double lambda1 = Double.parseDouble( tfRANSAC.getText().trim() );
				final double lambda2 = Double.parseDouble( tfFinal.getText().trim() );

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
						overlay.currentScale(),
						(int)Integer.parseInt( maxOS.getText().trim() ),
						stimcardFilter.filterFactories(),
						stimcard.currentRendering(), stimcard.currentSigma(), stimcard.currentBrightnessMin(), stimcard.currentBrightnessMax() );

				final Model model1 = getModelFor( boxModelRANSAC1.getSelectedIndex(), boxModelRANSAC2.getSelectedIndex(), lambda1 );
				final Model model2 = getModelFor( boxModelFinal1.getSelectedIndex(), boxModelFinal2.getSelectedIndex(), lambda2 );

				System.out.println( "Running SIFT align with the following parameters: \n" + param.toString() );
				System.out.println( "RANSAC model: " + optionsModel[ boxModelRANSAC1.getSelectedIndex() ] + ", regularizer: " + optionsModelReg[ boxModelRANSAC2.getSelectedIndex() ] + ", lambda=" + lambda1 );
				System.out.println( "FINAL model: " + optionsModel[ boxModelFinal1.getSelectedIndex() ] + ", regularizer: " + optionsModelReg[ boxModelFinal2.getSelectedIndex() ] + ", lambda=" + lambda2 );

				System.out.println( model1.getClass().getSimpleName() );
				System.out.println( model2.getClass().getSimpleName() );

				final boolean visResult = false;
				final double[] progressBarValue = new double[] { 1.0 };

				threads.clear();

				final SiftMatch match = PairwiseSIFT.pairwiseSIFT(
						stimcard.data1().data(), dataset1, stimcard.data2().data(), dataset2,
						(Affine2D & Model)model1, (Affine2D & Model)model1,
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
						model2.fit( match.getInliers() );
						stimcard.setCurrentModel( (Affine2D)model2 );

						System.out.println( "2D model: " + stimcard.currentModel() );
						System.out.println( "2D transform: " + stimcard.currentModel2D() );
						System.out.println( "3D viewer transform: " + stimcard.currentModel3D() );

						stimcard.applyTransformationToBDV( state, false );
					}
					catch ( Exception e )
					{
						e.printStackTrace();
					}

					// Overlay detections
					siftoverlay.setInliers( match.getInliers() );

					// Remember all genes with inliers
					genesWithInliers.clear();
					for ( final PointMatch pm : match.getInliers() )
						genesWithInliers.add(((PointST) pm.getP1()).getGene() );

					lastMaxError = param.maxError;

					System.out.println( "genes with inliers: ");
					genesWithInliers.forEach( s -> System.out.println( s ) );

					overlayInliers.setSelected( true );
					overlayInliers.setEnabled( true );
				}
				else
				{
					stimcard.setCurrentModel( new AffineModel2D() );
					stimcard.applyTransformationToBDV( state, false );

					lastMaxError = Double.NaN;

					siftoverlay.setInliers( new ArrayList<>() );
					genesWithInliers.clear();
					overlayInliers.setEnabled( false );
				}

				bar.setValue( 100 );
				cmdLine.setEnabled( true );
				run.setText( "Run SIFT alignment" );
				run.setFont( run.getFont().deriveFont( Font.PLAIN ) );
				run.setForeground( Color.black );

				stimcard.bdvhandle().getViewerPanel().requestRepaint();
				siftThread = null;
			});

			siftThread.start();
		});

		//
		// Return command line paramters for the last SIFT align run ...
		//
		cmdLine.addActionListener( l -> 
		{
			// TODO ...
		});
	}

	public HashSet< String> genesWithInliers() { return genesWithInliers; }
	public double lastMaxError() { return lastMaxError; }

	public void updateMaxOctaveSize()
	{
		maxOS.setValue( this.param.sift.maxOctaveSize = getMaxOctaveSize( stimcard.data1().data(), stimcard.data2().data(), overlay.currentScale() ) );
	}

	protected int getMaxOctaveSize( final STData data1, final STData data2, final double scale )
	{
		final Interval finalInterval = PairwiseSIFT.intervalForAlignment( data1, data2, scale );
		final int maxSize = (int)Math.max( finalInterval.dimension( 0 ), finalInterval.dimension( 1 ) );
		final int po2 = (int)Math.pow( 2, (maxSize == 0 ? 0 : 32 - Integer.numberOfLeadingZeros( maxSize - 1 ) ) );

		return po2;
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

	public JPanel getPanel() { return panel; }
}
