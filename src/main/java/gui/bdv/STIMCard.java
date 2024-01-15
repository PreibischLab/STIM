package gui.bdv;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import bdv.util.BdvHandle;
import bdv.viewer.DisplayMode;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.SourceGroup;
import bdv.viewer.SynchronizedViewerState;
import gui.DisplayScaleOverlay;
import gui.STDataAssembly;
import gui.bdv.AddedGene.Rendering;
import gui.geneselection.GeneSelectionExplorer;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.Pair;
import net.miginfocom.swing.MigLayout;
import util.BoundedValue;
import util.BoundedValuePanel;

public class STIMCard
{
	private final JPanel panel;
	private GeneSelectionExplorer gse = null;
	private final List<STDataAssembly> data;
	private final BdvHandle bdvhandle;
	private final HashMap< String, List< AddedGene > > sourceData;
	private final HashMap< String, SourceGroup > geneToBDVSource;
	private final DisplayScaleOverlay overlay;
	private double currentRF, currentBrightnessMin, currentBrightnessMax;
	private Rendering currentRendering;
	private double medianDistance;

	public STIMCard(
			final List<STDataAssembly> data,
			final List< Pair< String, Double > > allGenes,
			final HashMap< String, List< AddedGene > > sourceData,
			final HashMap< String, SourceGroup > geneToBDVSource,
			final DisplayScaleOverlay overlay,
			final double medianDistance,
			final Rendering initialRendering,
			final double initialRF,
			final double initialBrightnessMin,
			final double initialBrightnessMax,
			final BdvHandle bdvhandle )
	{
		this.data = data;
		this.bdvhandle = bdvhandle;
		this.sourceData = sourceData;
		this.overlay = overlay;
		this.geneToBDVSource = geneToBDVSource;
		this.currentBrightnessMin = initialBrightnessMin;
		this.currentBrightnessMax = initialBrightnessMax;
		this.currentRF = initialRF;
		this.currentRendering = initialRendering;
		this.medianDistance = medianDistance;

		this.panel = new JPanel(new MigLayout("gap 0, ins 5 5 5 0, fill", "[right][grow]", "center"));

		// display mode, gene selection
		final JLabel boxLabel = new JLabel("Rendering ");
		final Font font = boxLabel.getFont().deriveFont( 10f );
		boxLabel.setFont( font );
		panel.add( boxLabel, "aligny baseline" );

		final JPanel extraPanel = new JPanel( new MigLayout("gap 0, ins 0 0 0 0, fill", "[right][grow]", "center") );
		// TODO: Advanced parameters for many of them
		final String options[] = Arrays.asList( Rendering.values() ).stream().map( r -> r.name() ).toArray(String[]::new);
		final JComboBox< String > box = new JComboBox< String > (options);
		box.setBorder( BorderFactory.createEmptyBorder(0, 10, 0, 5));
		box.setSelectedIndex( currentRendering.ordinal() );
		extraPanel.add( box, "aligny baseline" /*"growx, wrap"*/ );
		final JButton add = new JButton("Genes (+)");
		add.setFont( add.getFont().deriveFont( 10f ).deriveFont( Font.BOLD ) );
		add.setForeground( new Color(20, 128, 52));
		extraPanel.add(add, "growx, wrap");
		extraPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 3, 10));
		panel.add( extraPanel, "growx, wrap");

		// sigma/radius slider
		final BoundedValuePanel rfSlider = new BoundedValuePanel(new BoundedValue(0, Math.round( Math.ceil( Math.max( 2.5, currentRF * 1.5 ) ) ), currentRF ));
		rfSlider.setBorder(null);
		final JLabel rfLabel = new JLabel( "Render factor (-rf)" );
		rfLabel.setFont( rfLabel.getFont().deriveFont( 10f ) );
		panel.add(rfLabel, "aligny baseline");
		panel.add(rfSlider, "growx, wrap");

		// brightness slider
		final BoundedValuePanel brightnessSliderMin = new BoundedValuePanel(new BoundedValue(0, 1, currentBrightnessMin ));
		final JLabel brightnessLabelMin = new JLabel("Brightness (-bmin)");
		brightnessLabelMin.setFont( font );
		panel.add(brightnessLabelMin, "aligny baseline");
		panel.add(brightnessSliderMin, "growx, wrap");

		final BoundedValuePanel brightnessSliderMax = new BoundedValuePanel(new BoundedValue(0, 1, currentBrightnessMax ));
		final JLabel brightnessLabelMax = new JLabel("Brightness (-bmax)");
		brightnessLabelMax.setFont( font );
		panel.add(brightnessLabelMax, "aligny baseline");
		panel.add(brightnessSliderMax, "growx, wrap");

		// rendering listener
		box.addActionListener( e -> {

			synchronized ( this )
			{
				if ( Rendering.values()[ box.getSelectedIndex() ] != currentRendering )
				{
					currentRendering = Rendering.values()[ box.getSelectedIndex() ];
					System.out.println( "now rendering as: " + currentRendering );

					final SynchronizedViewerState state = bdvhandle.getViewerPanel().state();
					AddedGene.updateRemainingSources( state, geneToBDVSource, sourceData );

					final List<AddedGene> anyDatasets = sourceData.values().iterator().next();

					for ( final String gene : geneToBDVSource.keySet() )
					{
						System.out.println( "replacing sources for '" + gene + "'");
	
						final SourceGroup currentSourceGroup = geneToBDVSource.get( gene );
						final ArrayList<SourceAndConverter<?>> currentSources = new ArrayList<>( state.getSourcesInGroup( currentSourceGroup ) );

						final List<AddedGene> newDatasets = new ArrayList<>();

						for ( int i = 0; i < anyDatasets.size(); ++i )
						{
							// TODO: re-use KDtree!
							newDatasets.add(
									AddedGene.addGene( 
											anyDatasets.get(i).inputPath(),
											anyDatasets.get(i).dataset(),
											currentRendering,
											bdvhandle, data.get( i ),
											anyDatasets.get(i).currentModel3D(), //TODO
											gene,
											currentRF,
											anyDatasets.get(i).color(), //new ARGBType( ARGBType.rgba(0, 255, 0, 0) ),
											currentBrightnessMin,
											currentBrightnessMax ) );
							
						}

						// TODO: re-use KDtree!
						//AddedGene gene1 = AddedGene.addGene( anyPair.getA().inputPath(), anyPair.getA().dataset(), currentRendering, bdvhandle, data1, currentModel3D(), gene, currentRF, new ARGBType( ARGBType.rgba(0, 255, 0, 0) ), currentBrightnessMin, currentBrightnessMax );
						//AddedGene gene2 = AddedGene.addGene( anyPair.getB().inputPath(), anyPair.getB().dataset(), currentRendering, bdvhandle, data2, null, gene, currentRF, new ARGBType( ARGBType.rgba(255, 0, 255, 0) ), currentBrightnessMin, currentBrightnessMax );

						for ( int i = anyDatasets.size(); i > 0; --i )
							state.addSourceToGroup( state.getSources().get( state.getSources().size() - i ), currentSourceGroup );

						//state.addSourceToGroup( state.getSources().get( state.getSources().size() - 2 ), currentSourceGroup );
						//state.addSourceToGroup( state.getSources().get( state.getSources().size() - 1 ), currentSourceGroup );
	
						for ( final SourceAndConverter<?> s : currentSources )
							state.removeSourceFromGroup( s, currentSourceGroup );
	
						//sourceData.put( gene, new ValuePair<>( gene1, gene2 ) );
						sourceData.put( gene, newDatasets );
					}

					bdvhandle.getViewerPanel().setDisplayMode( DisplayMode.GROUP );
				}
			}
		} );

		// sigma listener
		rfSlider.changeListeners().add( () ->
		{
			final double oldRF = currentRF;
			currentRF = rfSlider.getValue().getValue();

			if ( oldRF != currentRF )
			{
				final SynchronizedViewerState state = bdvhandle.getViewerPanel().state();
				AddedGene.updateRemainingSources( state, geneToBDVSource, sourceData );

				final double actualSigma = currentRF * medianDistance;
				sourceData.values().forEach( list ->
				{
					list.forEach( gene -> {
						if ( gene.gaussFactory() != null )
							gene.gaussFactory().setSigma( actualSigma );
						else if ( gene.radiusFactory() != null )
							gene.radiusFactory().setRadius( actualSigma );
						else
							gene.maxDistanceParam().setMaxDistance( actualSigma );
						
					});
					/*
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
					*/
				} );

				bdvhandle.getViewerPanel().requestRepaint();
			}
		} );

		// brightness listeners
		brightnessSliderMin.changeListeners().add( () -> {

			final double oldBrightness = currentBrightnessMin;
			currentBrightnessMin = brightnessSliderMin.getValue().getValue();

			if ( currentBrightnessMin > currentBrightnessMax )
			{
				currentBrightnessMin = currentBrightnessMax;
				SwingUtilities.invokeLater( () ->
				brightnessSliderMin.setValue(
						new BoundedValue(
								brightnessSliderMin.getValue().getMinBound(),
								brightnessSliderMin.getValue().getMaxBound(),
								currentBrightnessMin) ) );
			}

			if ( oldBrightness != currentBrightnessMin )
			{
				sourceData.values().forEach( list -> {
					list.forEach( gene -> {

						final double displayMin = AddedGene.getDisplayMin( gene.min(), gene.max(), currentBrightnessMin );
						final double displayMax = AddedGene.getDisplayMax( gene.max(), currentBrightnessMax );

						gene.source().setDisplayRange(displayMin, displayMax);
						gene.source().setDisplayRange(displayMin, displayMax);
					});
					/*
					final double displayMin = AddedGene.getDisplayMin( p.getA().min(), p.getA().max(), currentBrightnessMin );
					final double displayMax = AddedGene.getDisplayMax( p.getA().max(), currentBrightnessMax );

					p.getA().source().setDisplayRange(displayMin, displayMax);
					p.getB().source().setDisplayRange(displayMin, displayMax);
					*/
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
				SwingUtilities.invokeLater( () ->
					brightnessSliderMax.setValue(
							new BoundedValue(
									brightnessSliderMax.getValue().getMinBound(),
									brightnessSliderMax.getValue().getMaxBound(),
									currentBrightnessMax) ) );
			}

			if ( oldBrightness != currentBrightnessMax )
			{
				sourceData.values().forEach( list -> {
					list.forEach( gene -> {

						final double displayMin = AddedGene.getDisplayMin( gene.min(), gene.max(), currentBrightnessMin );
						final double displayMax = AddedGene.getDisplayMax( gene.max(), currentBrightnessMax );

						gene.source().setDisplayRange(displayMin, displayMax);
						gene.source().setDisplayRange(displayMin, displayMax);
					});
					/*
					final double displayMin = AddedGene.getDisplayMin( p.getA().min(), p.getA().max(), currentBrightnessMin );
					final double displayMax = AddedGene.getDisplayMax( p.getA().max(), currentBrightnessMax );

					p.getA().source().setDisplayRange(displayMin, displayMax);
					p.getB().source().setDisplayRange(displayMin, displayMax);
					*/
				} );

				final SynchronizedViewerState state = bdvhandle.getViewerPanel().state();
				AddedGene.updateRemainingSources( state, geneToBDVSource, sourceData );

				bdvhandle.getViewerPanel().requestRepaint();
			}
		} );

		//
		// Add genes ...
		//
		add.addActionListener( l -> 
		{
			if ( gse == null || gse.frame().isVisible() == false )
				gse = new GeneSelectionExplorer(
					allGenes,
					list ->
					{
						synchronized ( this )
						{
							final List<AddedGene> anyDatasets = sourceData.values().iterator().next();

							final List< String > inputPaths = new ArrayList<>();
							final List< String > datasets = new ArrayList<>();
							final List< AffineTransform3D > transforms = new ArrayList<>();
							final List< ARGBType > colors = new ArrayList<>();

							for ( int i = 0; i < anyDatasets.size(); ++i )
							{
								inputPaths.add( anyDatasets.get(i).inputPath() );
								datasets.add( anyDatasets.get(i).dataset() );
								transforms.add( anyDatasets.get(i).currentModel3D() );
								colors.add( anyDatasets.get(i).color() );
							}

							addGenes( list, inputPaths, datasets, transforms, colors );
						}
					} );
		});

		// popups
		final JPopupMenu menu1 = new JPopupMenu();
		menu1.add(runnableItem("set bounds ...", rfSlider::setBoundsDialog));
		rfSlider.setPopup(() -> menu1);

		final JPopupMenu menu2 = new JPopupMenu();
		menu2.add(runnableItem("set bounds ...", brightnessSliderMin::setBoundsDialog));
		brightnessSliderMin.setPopup(() -> menu2);

		final JPopupMenu menu3 = new JPopupMenu();
		menu3.add(runnableItem("set bounds ...", brightnessSliderMax::setBoundsDialog));
		brightnessSliderMax.setPopup(() -> menu3);

		System.out.println( "Done ... " );
	}

	public HashMap< String, List< AddedGene > > sourceData() { return sourceData; }
	public HashMap< String, SourceGroup > geneToBDVSource() { return geneToBDVSource; }
	public DisplayScaleOverlay scaleOverlay() { return overlay; }
	public double currentScale() { return scaleOverlay().currentScale(); }
	public double currentBrightnessMin() { return currentBrightnessMin; }
	public double currentBrightnessMax() { return currentBrightnessMax; }
	public double currentRenderingFactor() { return currentRF; }
	public Rendering currentDisplayMode() { return currentRendering; }
	public JPanel getPanel() { return panel; }
	public BdvHandle bdvhandle() { return bdvhandle; }
	public double medianDistance() { return medianDistance; }
	public List<STDataAssembly> data() { return data; }
	public String inputPath() { return sourceData.values().iterator().next().get( 0 ).inputPath(); } // the input path is the same for all AddedGene objects, we can just pick one

	public synchronized HashMap<String, List<AddedGene> > addGenes(
			final List< String > geneList,
			final List< String > inputPaths,
			final List< String > datasets,
			final List< AffineTransform3D > transforms,
			final List< ARGBType > colors )
	{
		//
		// first check if all groups are still present that are in the HashMap
		//
		final SynchronizedViewerState state = bdvhandle.getViewerPanel().state();
		AddedGene.updateRemainingSources( state, geneToBDVSource, sourceData );

		final HashMap<String, List<AddedGene> > allAddedGenes = new HashMap<>();

		//
		// Now add the new ones (if it's not already there)
		//
		for ( final String gene : geneList )
		{
			if ( !geneToBDVSource.containsKey( gene ) )
			{
				System.out.println( "Gene " + gene + " will be added." );

				final List<AddedGene> newDatasets = new ArrayList<>();

				for ( int i = 0; i < datasets.size(); ++i )
				{
					// TODO: re-use KDtree!
					newDatasets.add(
							AddedGene.addGene( 
									inputPaths.get(i),
									datasets.get(i),
									currentDisplayMode(),
									bdvhandle,
									data.get( i ),
									transforms.get(i),
									gene,
									currentRenderingFactor(),
									colors.get(i), //new ARGBType( ARGBType.rgba(0, 255, 0, 0) ),
									currentBrightnessMin(),
									currentBrightnessMax() ) );
					
				}

				sourceData.put( gene, newDatasets );
				allAddedGenes.put( gene, newDatasets );

				final SourceGroup handle = new SourceGroup();
				state.addGroup( handle );
				state.setGroupName( handle, gene );
				state.setGroupActive( handle, true );

				for ( int i = datasets.size(); i > 0; --i )
					state.addSourceToGroup( state.getSources().get( state.getSources().size() - i ), handle );

				geneToBDVSource.put( gene, handle );

				bdvhandle.getViewerPanel().setDisplayMode( DisplayMode.GROUP );
			}
			else
			{
				System.out.println( "Gene " + gene + " is already being displayed, ignoring." );
				// TODO: remove gaussFactories? - maybe not necessary
			}
		}

		return allAddedGenes;
	}

	public String createCmdLineArgs( final boolean addDataset, final boolean addGenes )
	{
		String cmdLine = "";

		if ( addDataset )
		{
			cmdLine += "-i " + inputPath() + " ";

			final HashSet<String> datasets = currentlyVisibleDatasets();

			if ( datasets.size() > 0 )
			{
				final Iterator<String> i = datasets.iterator();
				cmdLine += "-d " + i.next();

				while ( i.hasNext() )
					cmdLine += "," + i.next();

				cmdLine += " ";
			}
		}

		if ( addGenes )
		{
			final HashSet<String> genes = currentlyVisibleGenes();

			if ( genes.size() > 0 )
			{
				final Iterator<String> i = genes.iterator();
				cmdLine += "-g " + i.next();

				while ( i.hasNext() )
					cmdLine += "," + i.next();

				cmdLine += " ";
			}
		}

		cmdLine += "--rendering " + currentDisplayMode() + " -bmin " + currentBrightnessMin() + " -bmax " + currentBrightnessMax() + " -rf " + currentRenderingFactor() + " ";

		return cmdLine;
	}

	public HashSet< String > currentlyVisibleGenes()
	{
		final SynchronizedViewerState state = bdvhandle().getViewerPanel().state();
		final Set<SourceAndConverter<?>> active = state.getVisibleSources();

		final HashSet< String > genes = new HashSet<>();

		sourceData().entrySet().forEach( set ->
		{
			active.forEach( source ->
			{
				set.getValue().forEach( gene ->
				{
					if ( gene.soc() == source )
						genes.add( set.getKey() );
				});
				//if ( set.getValue().getA().soc() == source || set.getValue().getB().soc() == source )
				//	genes.add( set.getKey() );
			} );
		});

		return genes; 
	}

	public HashSet< String > currentlyVisibleDatasets()
	{
		final SynchronizedViewerState state = bdvhandle().getViewerPanel().state();
		final Set<SourceAndConverter<?>> active = state.getVisibleSources();

		final HashSet< String > datasets = new HashSet<>();

		sourceData().entrySet().forEach( set ->
		{
			active.forEach( source ->
			{
				set.getValue().forEach( gene ->
				{
					if ( gene.soc() == source )
						datasets.add( gene.dataset() );
				});
				//if ( set.getValue().getA().soc() == source )
				//	datasets.add( set.getValue().getA().dataset() );

				//if ( set.getValue().getB().soc() == source )
				//	datasets.add( set.getValue().getB().dataset() );
					
			} );
		});

		return datasets;
	}

	// not the class, but each AddedGene should has it's own transform
	public synchronized void applyTransformationToBDV( final boolean requestUpdateBDV )
	{
		sourceData.forEach( (gene,data) ->
				data.forEach( d -> d.transformedSource().setFixedTransform( d.currentModel3D() ) )
		);

		//data.getA().transformedSource().setFixedTransform( m3d );
		//data.getB().transformedSource().setFixedTransform( new AffineTransform3D() );

		if ( requestUpdateBDV )
			bdvhandle().getViewerPanel().requestRepaint();
	}

	public static void addPopUp( final Component comp, final JPopupMenu menu )
	{
		comp.addMouseListener(
			new MouseAdapter()
			{
				@Override
				public void mousePressed( final MouseEvent e )
				{
					if ( e.isPopupTrigger() )
						doPop( e );
				}

				@Override
				public void mouseReleased( final MouseEvent e )
				{
					if ( e.isPopupTrigger() )
						doPop( e );
				}

				private void doPop( final MouseEvent e )
				{
					menu.show( e.getComponent(), e.getX(), e.getY() );
				}
			});
	}

	public static JMenuItem runnableItem(final String text, final Runnable action) {
		return runnableItem(text, null, action);
	}

	public static JMenuItem runnableItem(final String text, final Font font, final Runnable action) {
		final JMenuItem item = new JMenuItem(text);
		if ( font != null )
			item.setFont( font );
		item.addActionListener(e -> action.run());
		return item;
	}
}
