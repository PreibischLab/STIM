package gui.bdv;

import java.awt.Color;
import java.awt.Font;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.text.NumberFormatter;

import org.scijava.ui.behaviour.util.InputActionBindings;

import bdv.BigDataViewerActions;
import bdv.tools.transformation.ManualTransformationEditor;
import bdv.viewer.DisplayMode;
import mpicbg.models.Affine2D;
import mpicbg.models.Model;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.AffineTransform3D;
import net.miginfocom.swing.MigLayout;

public class STIMCardManualAlign
{
	private final JPanel panel;

	private final JFormattedTextField m00;
	private final JFormattedTextField m01;
	private final JFormattedTextField m02;
	private final JFormattedTextField m10;
	private final JFormattedTextField m11;
	private final JFormattedTextField m12;
	private final JButton reset, run, cancel;
	private Affine2D<?> previousModel = null;

	private final STIMCard stimcard;
	private final STIMCardAlignICP icpCard;
	private final STIMCardAlignSIFT siftCard;

	public STIMCardManualAlign(
			final STIMCard stimcard,
			final STIMCardAlignSIFT siftCard,
			final STIMCardAlignICP icpCard )
	{
		this.stimcard = stimcard;
		this.icpCard = icpCard;
		this.siftCard = siftCard;

		this.panel = new JPanel( new MigLayout("gap 0, ins 5 5 5 5, fill", "[sizegroup main, grow][sizegroup main,grow][sizegroup main, grow]") );//new GridBagLayout() );

		final NumberFormatter formatterDouble = STIMCardAlignSIFT.formatterDouble();
		formatterDouble.setFormat(new DecimalFormat("#0.0000000"));
		final AffineTransform2D model = stimcard.sourceData().values().iterator().next().get( 0 ).currentModel2D().copy();

		reset = new JButton( "Reset" );
		cancel = new JButton( "Cancel" );
		run = new JButton( "Start" );
		run.setFont( run.getFont().deriveFont( Font.BOLD ) );
		cancel.setEnabled( false );

		final Font fontTF = cancel.getFont().deriveFont( 9f );

		// initialSigma
		m00 = new JFormattedTextField( formatterDouble );
		m00.setBorder( new TitledBorder(null, "m00", TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION, fontTF, null ) );
		m00.setValue( model.get(0, 0) );

		m01 = new JFormattedTextField( formatterDouble );
		m01.setBorder( new TitledBorder(null, "m01", TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION, fontTF, null ) );
		m01.setValue( model.get(0, 1) );

		m02 = new JFormattedTextField( formatterDouble );
		m02.setBorder( new TitledBorder(null, "m02 / tx", TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION, fontTF, null ) );
		m02.setValue( model.get(0, 2) );

		m10 = new JFormattedTextField( formatterDouble );
		m10.setBorder( new TitledBorder(null, "m10", TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION, fontTF, null ) );
		m10.setValue( model.get(1, 0) );

		m11 = new JFormattedTextField( formatterDouble );
		m11.setBorder( new TitledBorder(null, "m11", TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION, fontTF, null ) );
		m11.setValue( model.get(1, 1) );

		m12 = new JFormattedTextField( formatterDouble );
		m12.setBorder( new TitledBorder(null, "m12 / ty", TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION, fontTF, null ) );
		m12.setValue( model.get(1, 2) );

		panel.add(m00, "growx" );
		panel.add(m01, "growx");
		panel.add(m02, "growx, wrap" );

		panel.add(m10, "growx" );
		panel.add(m11, "growx");
		panel.add(m12, "growx, wrap" );

		JPanel j = new JPanel();
		j.setBorder( BorderFactory.createEmptyBorder(0,0,1,0));
		panel.add( j, "growx, wrap" );

		panel.add( reset, "growx" );
		panel.add( cancel, "growx" );
		panel.add( run, "growx" );

		// remove key bindings for BDV Manual Transform
		final InputActionBindings keyBindings = stimcard.bdvhandle().getKeybindings();
		keyBindings.removeActionMap( "manual transform" );
		keyBindings.removeInputMap( "manual transform" );
		removeKey( keyBindings.getConcatenatedActionMap(), BigDataViewerActions.MANUAL_TRANSFORM );

		final ManualTransformationEditor m = stimcard.bdvhandle().getManualTransformEditor();
		final List< AddedGene > sources = new ArrayList<>();

		final AtomicBoolean isRunning = new AtomicBoolean( false );

		run.addActionListener( l ->
		{
			if ( isRunning.get() )
			{
				isRunning.set( false );
				m.setActive( false );

				// overwrites the fixedTransform, we need to apply it to all other sources
				final AffineTransform3D t3 = new AffineTransform3D();
				sources.get( 0 ).transformedSource().getFixedTransform(t3);
				siftCard.setTransform3D( t3 );
				stimcard.applyTransformationToBDV( true ); // should be identical

				final AffineTransform2D t = sources.get( 0 ).currentModel2D().copy();

				SwingUtilities.invokeLater( () ->
				{
					reEnableControls();
					setTransformGUI( t );
				});
			}
			else
			{
				previousModel = (Affine2D)((Model)stimcard.sourceData().values().iterator().next().get( 0 ).currentModel()).copy();

				SwingUtilities.invokeLater( () ->
				{
					// disable local controls
					isRunning.set( true );
					reset.setEnabled( false );
					cancel.setEnabled( true );
					cancel.setForeground( Color.red );
					run.setText( "Finish" );
					run.setForeground( new Color( 50, 150, 50 ) );
	
					// disable SIFT, ICP controls
					siftCard.run.setEnabled( false );
					siftCard.cmdLine.setEnabled( false );
					siftCard.reset.setEnabled( false );
					siftCard.overlayInliers.setSelected( false );
					siftCard.saveTransform.setEnabled( false );
					if ( icpCard != null )
					{
						icpCard.run.setEnabled( false );
						icpCard.cmdLine.setEnabled( false );
						icpCard.reset.setEnabled( false );
						icpCard.saveTransform.setEnabled( false );
					}
				});

				// we need to switch to single-source, fused mode showing only the current gene
				// and activate the first source to transform it
				final HashSet<String> visible = stimcard.currentlyVisibleGenes();
	
				if (visible.isEmpty())
				{
					System.out.println( "Nothing is visibile. Maybe consider restarting to return to predefined viewing mode.");
					return;
				}
		
				if ( visible.size() > 1 )
				{
					System.out.println( "More than one gene is visibile. Maybe consider restarting to return to predefined viewing mode.");
					return;
				}
	
				final String gene = visible.iterator().next();
				System.out.println( "Visible gene (used for manual transform) is: " + gene);
	
				//final SourceGroup sources = stimcard.geneToBDVSource().get( gene );
				final List<AddedGene> currentSources = stimcard.sourceData().get( gene );
	
				currentSources.get( 0 ).source().setActive( true );
				currentSources.get( 1 ).source().setActive( true );
	
				stimcard.sourceData().values().forEach( list -> {
					if ( list != currentSources )
					{
						list.get( 0 ).source().setActive( false );
						list.get( 1 ).source().setActive( false );
					}
				});

				currentSources.get( 0 ).source().setCurrent();

				sources.clear();
				sources.addAll( currentSources ); // for stopping

				stimcard.bdvhandle().getViewerPanel().setDisplayMode( DisplayMode.FUSED );

				SwingUtilities.invokeLater( () ->
				{
					m00.setEnabled( false );
					m01.setEnabled( false );
					m02.setEnabled( false );
					m10.setEnabled( false );
					m11.setEnabled( false );
					m12.setEnabled( false );
				});

				m.setActive( true );
			}
		});


		cancel.addActionListener( l ->
		{
			isRunning.set( false );
			m.setActive( false );

			siftCard.setModel( previousModel );
			stimcard.applyTransformationToBDV( true ); // should be identical

			final AffineTransform2D t = sources.get( 0 ).currentModel2D().copy();

			SwingUtilities.invokeLater( () ->
			{
				reEnableControls();
				setTransformGUI( t );
			});
		});

		reset.addActionListener( l -> 
		{
			isRunning.set( false );

			final AffineTransform2D transform = new AffineTransform2D();

			siftCard.setTransform( transform );
			stimcard.applyTransformationToBDV( true ); // should be identical

			SwingUtilities.invokeLater( () -> setTransformGUI( transform ) );
		});
	}

	public void setTransformGUI( final AffineTransform2D t )
	{
		m00.setValue( t.get( 0, 0 ) );
		m01.setValue( t.get( 0, 1 ) );
		m02.setValue( t.get( 0, 2 ) );
		m10.setValue( t.get( 1, 0 ) );
		m11.setValue( t.get( 1, 1 ) );
		m12.setValue( t.get( 1, 2 ) );
	}

	// called by ICP, SIFT
	public void disableControlsExternal()
	{
		reset.setEnabled( false );
		cancel.setEnabled( false );
		run.setEnabled( false );

		m00.setEnabled( false );
		m01.setEnabled( false );
		m02.setEnabled( false );
		m10.setEnabled( false );
		m11.setEnabled( false );
		m12.setEnabled( false );
	}

	// called by ICP, SIFT
	public void reEnableControlsExternal()
	{
		reset.setEnabled( true );
		cancel.setEnabled( false );
		run.setEnabled( true );

		m00.setEnabled( true );
		m01.setEnabled( true );
		m02.setEnabled( true );
		m10.setEnabled( true );
		m11.setEnabled( true );
		m12.setEnabled( true );
	}

	// called locally
	private void reEnableControls()
	{
		siftCard.run.setEnabled( true );
		siftCard.cmdLine.setEnabled( true );
		siftCard.reset.setEnabled( true );
		siftCard.saveTransform.setEnabled( true );
		if ( icpCard != null )
		{
			icpCard.run.setEnabled( true );
			icpCard.cmdLine.setEnabled( true );
			icpCard.reset.setEnabled( true );
			icpCard.saveTransform.setEnabled( true );
		}

		run.setText( "Start" );
		run.setForeground( Color.black );
		cancel.setEnabled( false );
		cancel.setForeground( Color.black );
		reset.setEnabled( true );

		m00.setEnabled( true );
		m01.setEnabled( true );
		m02.setEnabled( true );
		m10.setEnabled( true );
		m11.setEnabled( true );
		m12.setEnabled( true );

		stimcard.sourceData().values().forEach( list -> {
			list.get( 0 ).source().setActive( true );
			list.get( 1 ).source().setActive( true );
		});

		stimcard.bdvhandle().getViewerPanel().setDisplayMode( DisplayMode.GROUP );
	}

	protected void removeKey( final ActionMap map, final Object key )
	{
		map.remove( key );

		if ( map.getParent() != null )
			removeKey( map.getParent(), key );
	}

	public JPanel getPanel() { return panel; }
}
