package gui.bdv;

import java.awt.Font;
import java.awt.GridBagLayout;
import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.List;

import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.border.TitledBorder;
import javax.swing.text.NumberFormatter;

import org.scijava.ui.behaviour.util.InputActionBindings;

import bdv.BigDataViewerActions;
import bdv.tools.transformation.ManualTransformationEditor;
import bdv.viewer.DisplayMode;
import bdv.viewer.SourceGroup;
import bdv.viewer.ViewerPanel;
import net.imglib2.realtransform.AffineTransform2D;
import net.miginfocom.swing.MigLayout;

public class STIMCardManualAlign
{
	private final JPanel panel;

	private JFormattedTextField m00, m01, m02, m10, m11, m12;

	private AffineTransform2D previousModel;

	private final STIMCard stimcard;
	private final STIMCardFilter filterCard;
	private final STIMCardAlignSIFT siftCard;

	public STIMCardManualAlign(
			final STIMCard stimcard,
			final STIMCardFilter filterCard,
			final STIMCardAlignSIFT siftCard )
	{
		this.stimcard = stimcard;
		this.filterCard = filterCard;
		this.siftCard = siftCard;

		this.panel = new JPanel( new MigLayout("gap 0, ins 5 5 5 5, fill", "[sizegroup main, grow][sizegroup main,grow][sizegroup main, grow]") );//new GridBagLayout() );

		final NumberFormatter formatterDouble = STIMCardAlignSIFT.formatterDouble();
		formatterDouble.setFormat(new DecimalFormat("#0.0000000"));
		final AffineTransform2D model = stimcard.sourceData().values().iterator().next().get( 0 ).currentModel2D().copy();

		System.out.println( model );
		final JButton button = new JButton( "Manual alignment" );

		final Font fontTF = button.getFont().deriveFont( 9f );

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

		panel.add( button, "growx, span" );

		// remove key bindings for BDV Manual Transform
		final InputActionBindings keyBindings = stimcard.bdvhandle().getKeybindings();
		keyBindings.removeActionMap( "manual transform" );
		keyBindings.removeInputMap( "manual transform" );
		removeKey( keyBindings.getConcatenatedActionMap(), BigDataViewerActions.MANUAL_TRANSFORM );

		button.addActionListener( l ->
		{
			// we need to switch to single-source, fused mode showing only the current gene
			// and activate the first source to transform it

			final HashSet<String> visible = stimcard.currentlyVisibleGenes();

			if ( visible.size() == 0 )
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
			final List< AddedGene > sources = stimcard.sourceData().get( gene );

			sources.get( 0 ).source().setActive( true );
			sources.get( 1 ).source().setActive( true );

			stimcard.sourceData().values().forEach( list -> {
				if ( list != sources )
				{
					list.get( 0 ).source().setActive( false );
					list.get( 1 ).source().setActive( false );
				}
			});

			sources.get( 0 ).source().setCurrent();

			stimcard.bdvhandle().getViewerPanel().setDisplayMode( DisplayMode.FUSED );

			final ManualTransformationEditor m = stimcard.bdvhandle().getManualTransformEditor();
			m.toggle();

			m.manualTransformActiveListeners();
			//m.abort()
			
		});
		//panel.add(panelModel, "span,growx,pushy");
	}

	protected void removeKey( final ActionMap map, final Object key )
	{
		map.remove( key );

		if ( map.getParent() != null )
			removeKey( map.getParent(), key );
	}

	public JPanel getPanel() { return panel; }
}
