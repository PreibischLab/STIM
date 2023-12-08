package gui.bdv;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import bdv.util.BdvHandle;
import bdv.viewer.DisplayMode;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.SourceGroup;
import bdv.viewer.SynchronizedViewerState;
import cmd.InteractiveAlignment.AddedGene;
import cmd.InteractiveAlignment.AddedGene.Rendering;
import gui.STDataAssembly;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.miginfocom.swing.MigLayout;
import util.BoundedValue;
import util.BoundedValuePanel;

public class STIMCard
{
	private final JPanel panel;
	private final HashMap< String, Pair< AddedGene, AddedGene > > sourceData;
	private final HashMap< String, SourceGroup > geneToBDVSource;
	private double currentSigma, currentBrightnessMin, currentBrightnessMax;
	private Rendering currentRendering;

	public STIMCard(
			final STDataAssembly data1,
			final STDataAssembly data2,
			final HashMap< String, Pair< AddedGene, AddedGene > > sourceData,
			final HashMap< String, SourceGroup > geneToBDVSource,
			final double medianDistance,
			final Rendering initialRendering,
			final double initialSigma,
			final double initialBrightnessMin,
			final double initialBrightnessMax,
			final BdvHandle bdvhandle )
	{
		this.sourceData = sourceData;
		this.geneToBDVSource = geneToBDVSource;
		this.currentBrightnessMin = initialBrightnessMin;
		this.currentBrightnessMax = initialBrightnessMax;
		this.currentSigma = initialSigma;
		this.currentRendering = initialRendering;

		this.panel = new JPanel(new MigLayout("gap 0, ins 5 5 5 0, fill", "[right][grow]", "center"));

		// brightness slider
		final BoundedValuePanel brightnessSliderMin = new BoundedValuePanel(new BoundedValue(0, 1, currentBrightnessMin ));
		final JLabel brightnessLabelMin = new JLabel("brightness (-bmin)");
		final Font font = brightnessLabelMin.getFont().deriveFont( 10f );
		brightnessLabelMin.setFont( font );
		brightnessLabelMin.setBorder( null );
		brightnessSliderMin.setBorder(null);
		panel.add(brightnessLabelMin, "aligny baseline");
		panel.add(brightnessSliderMin, "growx, wrap");

		final BoundedValuePanel brightnessSliderMax = new BoundedValuePanel(new BoundedValue(0, 1, currentBrightnessMax ));
		final JLabel brightnessLabelMax = new JLabel("brightness (-bmax)");
		brightnessLabelMax.setFont( font );
		brightnessLabelMax.setBorder( null );
		brightnessSliderMin.setBorder(null);
		brightnessSliderMax.setBorder(null);
		panel.add(brightnessLabelMax, "aligny baseline");
		panel.add(brightnessSliderMax, "growx, wrap");

		//final JButton testButton = new JButton( "Show as NN" );
		//panel.add(testButton, "span,growx,pushy");

		//final String options[] = { "Gauss", "Nearest Neighbor", "Linear", "Mean" }; // TODO: Advanced parameters for many of them
		final String options[] = Arrays.asList( Rendering.values() ).stream().map( r -> r.name() ).toArray(String[]::new);

		final JComboBox< String > box = new JComboBox< String > (options);
		box.setBorder( null );
		box.setSelectedIndex( currentRendering.ordinal() );
		final JLabel boxLabel = new JLabel("Display mode ");
		panel.add( boxLabel, "aligny baseline" );
		panel.add( box, "growx, wrap" );


		// sigma slider
		final BoundedValuePanel sigmaSlider = new BoundedValuePanel(new BoundedValue(0, Math.round( Math.ceil( Math.max( 2.5, currentSigma * 1.5 ) ) ), currentSigma ));
		sigmaSlider.setBorder(null);
		final JLabel sigmaLabel = new JLabel( currentRendering == Rendering.Gauss ? "sigma (-sf)" : "radius (-r)" );
		panel.add(sigmaLabel, "aligny baseline");
		panel.add(sigmaSlider, "growx, wrap");

		// rendering listener
		box.addActionListener( e -> {

			if ( Rendering.values()[ box.getSelectedIndex() ] != currentRendering )
			{
				currentRendering = Rendering.values()[ box.getSelectedIndex() ];
				System.out.println( "now rendering as: " + currentRendering );

				final SynchronizedViewerState state = bdvhandle.getViewerPanel().state();
				AddedGene.updateRemainingSources( state, geneToBDVSource, sourceData );

				for ( final String gene : geneToBDVSource.keySet() )
				{
					System.out.println( "replacing sources for '" + gene + "'");

					final SourceGroup currentSourceGroup = geneToBDVSource.get( gene );
					final ArrayList<SourceAndConverter<?>> currentSources = new ArrayList<>( state.getSourcesInGroup( currentSourceGroup ) );

					// TODO: re-use KDtree!
					AddedGene gene1 = AddedGene.addGene( currentRendering, bdvhandle, data1, gene, currentSigma, new ARGBType( ARGBType.rgba(0, 255, 0, 0) ), currentBrightnessMin, currentBrightnessMax );
					AddedGene gene2 = AddedGene.addGene( currentRendering, bdvhandle, data2, gene, currentSigma, new ARGBType( ARGBType.rgba(255, 0, 255, 0) ), currentBrightnessMin, currentBrightnessMax );

					state.addSourceToGroup( state.getSources().get( state.getSources().size() - 2 ), currentSourceGroup );
					state.addSourceToGroup( state.getSources().get( state.getSources().size() - 1 ), currentSourceGroup );

					for ( final SourceAndConverter<?> s : currentSources )
						state.removeSourceFromGroup( s, currentSourceGroup );

					sourceData.put( gene, new ValuePair<>( gene1, gene2 ) );
				}

				if ( currentRendering == Rendering.Gauss )
					sigmaLabel.setText( "sigma (-sf)" );
				else
					sigmaLabel.setText( "radius (-r)" );

				bdvhandle.getViewerPanel().setDisplayMode( DisplayMode.GROUP );
			}
		} );

		// sigma listener
		sigmaSlider.changeListeners().add( () ->
		{
			final double oldSigma = currentSigma;
			currentSigma = sigmaSlider.getValue().getValue();

			if ( oldSigma != currentSigma )
			{
				final SynchronizedViewerState state = bdvhandle.getViewerPanel().state();
				AddedGene.updateRemainingSources( state, geneToBDVSource, sourceData );

				final double actualSigma = currentSigma * medianDistance;
				sourceData.values().forEach( p ->
				{
					if ( p.getA().gaussFactory() != null )
						p.getA().gaussFactory().setSigma( actualSigma );
					else if ( p.getA().radiusFactory() != null )
						p.getA().radiusFactory().setRadius( actualSigma );
					else
						p.getA().maxDistanceParam().setMaxDistance( actualSigma );

					if ( p.getB().gaussFactory() != null )
						p.getB().gaussFactory().setSigma( actualSigma );
					else if ( p.getB().radiusFactory() != null )
						p.getB().radiusFactory().setRadius( actualSigma );
					else
						p.getB().maxDistanceParam().setMaxDistance( actualSigma );
				} );

				bdvhandle.getViewerPanel().requestRepaint();
			}
		} );

		// brightness listener
		brightnessSliderMin.changeListeners().add( () -> {

			final double oldBrightness = currentBrightnessMin;
			currentBrightnessMin = brightnessSliderMin.getValue().getValue();

			if ( currentBrightnessMin > currentBrightnessMax )
			{
				currentBrightnessMin = currentBrightnessMax;
				brightnessSliderMin.setValue( new BoundedValue(brightnessSliderMin.getValue().getMinBound(), brightnessSliderMin.getValue().getMaxBound(), currentBrightnessMin) );
			}

			if ( oldBrightness != currentBrightnessMin )
			{
				sourceData.values().forEach( p -> {
					final double displayMin = AddedGene.getDisplayMin( p.getA().min(), p.getA().max(), currentBrightnessMin );
					final double displayMax = AddedGene.getDisplayMax( p.getA().max(), currentBrightnessMax );

					p.getA().source().setDisplayRange(displayMin, displayMax);
					p.getB().source().setDisplayRange(displayMin, displayMax);
				} );

				final SynchronizedViewerState state = bdvhandle.getViewerPanel().state();
				AddedGene.updateRemainingSources( state, geneToBDVSource, sourceData );

				bdvhandle.getViewerPanel().requestRepaint();
			}
		} );

		brightnessSliderMax.changeListeners().add( () -> {

			final double oldBrightness = currentBrightnessMax;
			currentBrightnessMax = brightnessSliderMax.getValue().getValue();

			if ( currentBrightnessMax < currentBrightnessMin )
			{
				currentBrightnessMax = currentBrightnessMin;
				brightnessSliderMax.setValue( new BoundedValue(brightnessSliderMax.getValue().getMinBound(), brightnessSliderMax.getValue().getMaxBound(), currentBrightnessMax) );
			}

			if ( oldBrightness != currentBrightnessMax )
			{
				sourceData.values().forEach( p -> {
					final double displayMin = AddedGene.getDisplayMin( p.getA().min(), p.getA().max(), currentBrightnessMin );
					final double displayMax = AddedGene.getDisplayMax( p.getA().max(), currentBrightnessMax );

					p.getA().source().setDisplayRange(displayMin, displayMax);
					p.getB().source().setDisplayRange(displayMin, displayMax);
				} );

				final SynchronizedViewerState state = bdvhandle.getViewerPanel().state();
				AddedGene.updateRemainingSources( state, geneToBDVSource, sourceData );

				bdvhandle.getViewerPanel().requestRepaint();
			}
		} );

		// popups
		final JPopupMenu menu1 = new JPopupMenu();
		menu1.add(runnableItem("set bounds ...", sigmaSlider::setBoundsDialog));
		sigmaSlider.setPopup(() -> menu1);

		final JPopupMenu menu2 = new JPopupMenu();
		menu2.add(runnableItem("set bounds ...", brightnessSliderMin::setBoundsDialog));
		brightnessSliderMin.setPopup(() -> menu2);

		final JPopupMenu menu3 = new JPopupMenu();
		menu3.add(runnableItem("set bounds ...", brightnessSliderMax::setBoundsDialog));
		brightnessSliderMax.setPopup(() -> menu3);

		System.out.println( "Done ... " );
	}

	public HashMap< String, Pair< AddedGene, AddedGene > > sourceData() { return sourceData; }
	public HashMap< String, SourceGroup > geneToBDVSource() { return geneToBDVSource; }
	public double currentBrightnessMin() { return currentBrightnessMin; }
	public double currentBrightnessMax() { return currentBrightnessMax; }
	public double currentSigma() { return currentSigma; }
	public Rendering currentRendering() { return currentRendering; }
	public JPanel getPanel() { return panel; }

	private JMenuItem runnableItem(final String text, final Runnable action) {
		final JMenuItem item = new JMenuItem(text);
		item.addActionListener(e -> action.run());
		return item;
	}
}
