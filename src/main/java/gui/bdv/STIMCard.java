package gui.bdv;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import bdv.util.BdvHandle;
import bdv.viewer.DisplayMode;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.SourceGroup;
import bdv.viewer.SynchronizedViewerState;
import cmd.InteractiveAlignment.AddedGene;
import cmd.InteractiveAlignment.AddedGene.Rendering;
import filter.Filters;
import filter.GaussianFilterFactory;
import filter.MeanFilterFactory;
import filter.MedianFilterFactory;
import filter.SingleSpotRemovingFilterFactory;
import gui.STDataAssembly;
import gui.geneselection.GeneSelectionExplorer;
import net.imglib2.RealCursor;
import net.imglib2.RealPointSampleList;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.miginfocom.swing.MigLayout;
import util.BoundedValue;
import util.BoundedValuePanel;

public class STIMCard
{
	private final JPanel panel;
	private GeneSelectionExplorer gse = null;
	private final STDataAssembly data1, data2;
	private final BdvHandle bdvhandle;
	private final HashMap< String, Pair< AddedGene, AddedGene > > sourceData;
	private final HashMap< String, SourceGroup > geneToBDVSource;
	private double currentSigma, currentBrightnessMin, currentBrightnessMax;
	private Rendering currentRendering;
	private double medianDistance;

	public STIMCard(
			final STDataAssembly data1,
			final STDataAssembly data2,
			final List< Pair< String, Double > > allGenes,
			final HashMap< String, Pair< AddedGene, AddedGene > > sourceData,
			final HashMap< String, SourceGroup > geneToBDVSource,
			final double medianDistance,
			final Rendering initialRendering,
			final double initialSigma,
			final double initialBrightnessMin,
			final double initialBrightnessMax,
			final BdvHandle bdvhandle )
	{
		this.data1 = data1;
		this.data2 = data2;
		this.bdvhandle = bdvhandle;
		this.sourceData = sourceData;
		this.geneToBDVSource = geneToBDVSource;
		this.currentBrightnessMin = initialBrightnessMin;
		this.currentBrightnessMax = initialBrightnessMax;
		this.currentSigma = initialSigma;
		this.currentRendering = initialRendering;
		this.medianDistance = medianDistance;

		this.panel = new JPanel(new MigLayout("gap 0, ins 5 5 5 0, fill", "[right][grow]", "center"));

		// display mode, gene selection
		final JLabel boxLabel = new JLabel("Display mode (-dm) ");
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
		final BoundedValuePanel sigmaSlider = new BoundedValuePanel(new BoundedValue(0, Math.round( Math.ceil( Math.max( 2.5, currentSigma * 1.5 ) ) ), currentSigma ));
		sigmaSlider.setBorder(null);
		final JLabel sigmaLabel = new JLabel( currentRendering == Rendering.Gauss ? "sigma (-sf)" : "radius (-r)" );
		panel.add(sigmaLabel, "aligny baseline");
		panel.add(sigmaSlider, "growx, wrap");

		// brightness slider
		final BoundedValuePanel brightnessSliderMin = new BoundedValuePanel(new BoundedValue(0, 1, currentBrightnessMin ));
		final JLabel brightnessLabelMin = new JLabel("brightness (-bmin)");
		brightnessLabelMin.setFont( font );
		panel.add(brightnessLabelMin, "aligny baseline");
		panel.add(brightnessSliderMin, "growx, wrap");

		final BoundedValuePanel brightnessSliderMax = new BoundedValuePanel(new BoundedValue(0, 1, currentBrightnessMax ));
		final JLabel brightnessLabelMax = new JLabel("brightness (-bmax)");
		brightnessLabelMax.setFont( font );
		panel.add(brightnessLabelMax, "aligny baseline");
		panel.add(brightnessSliderMax, "growx, wrap");

		// Initializing the JTable
		final Component tablePanel = setupTable();
		//panel.add( tablePanel, "span,growx,pushy");

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

		// brightness listeners
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
						//
						// first check if all groups are still present that are in the HashMap
						//
						final SynchronizedViewerState state = bdvhandle.getViewerPanel().state();
						AddedGene.updateRemainingSources( state, geneToBDVSource, sourceData );

						//
						// Now add the new ones (if it's not already there)
						//
						for ( final String gene : list )
						{
							if ( !geneToBDVSource.containsKey( gene ) )
							{
								System.out.println( "Gene " + gene + " will be added." );

								final AddedGene gene1 = AddedGene.addGene( currentRendering(), bdvhandle, data1, gene, currentSigma(), new ARGBType( ARGBType.rgba(0, 255, 0, 0) ), currentBrightnessMin(), currentBrightnessMax() );
								final AddedGene gene2 = AddedGene.addGene( currentRendering(), bdvhandle, data2, gene, currentSigma(), new ARGBType( ARGBType.rgba(255, 0, 255, 0) ), currentBrightnessMin(), currentBrightnessMax() );

								sourceData.put( gene, new ValuePair<>( gene1, gene2 ) );
								//stimcard.gaussFactories().add( gene1.factory );
								//stimcard.gaussFactories().add( gene2.factory );

								final SourceGroup handle = new SourceGroup();
								state.addGroup( handle );
								state.setGroupName( handle, gene );
								state.setGroupActive( handle, true );
								state.addSourceToGroup( state.getSources().get( state.getSources().size() - 2 ), handle );
								state.addSourceToGroup( state.getSources().get( state.getSources().size() - 1 ), handle );

								geneToBDVSource.put( gene, handle );

								bdvhandle.getViewerPanel().setDisplayMode( DisplayMode.GROUP );
							}
							else
							{
								System.out.println( "Gene " + gene + " is already being displayed, ignoring." );
								// TODO: remove gaussFactories? - maybe not necessary
							}
						}
					} );
		});

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
	public BdvHandle bdvhandle() { return bdvhandle; }
	public double medianDistance() { return medianDistance; }
	public STDataAssembly data1() { return data1; }
	public STDataAssembly data2() { return data2; }

	private JMenuItem runnableItem(final String text, final Runnable action) {
		final JMenuItem item = new JMenuItem(text);
		item.addActionListener(e -> action.run());
		return item;
	}

	protected Component setupTable()
	{
		final JTable table = new JTable();
		table.setModel( new FilterTableModel( table ) );
		table.setPreferredScrollableViewportSize(new Dimension(260, 65));
		table.setBorder( BorderFactory.createEmptyBorder(0, 0, 0, 10));
		table.getColumnModel().getColumn(0).setPreferredWidth(40);
		table.getColumnModel().getColumn(1).setPreferredWidth(260-40-50);
		table.getColumnModel().getColumn(2).setPreferredWidth(50);

		table.setRowSelectionAllowed(false);

		final DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
		centerRenderer.setHorizontalAlignment( JLabel.CENTER );
		table.getColumnModel().getColumn(2).setCellRenderer( centerRenderer );

		table.getModel().addTableModelListener( e -> System.out.println( e ) );
		// adding it to JScrollPane
		final JScrollPane sp = new JScrollPane(table);
		final JPanel extraPanel2 = new JPanel();
		extraPanel2.setBorder( BorderFactory.createEmptyBorder(0, 0, 0, 15));
		extraPanel2.add( sp );

		return extraPanel2;
	}

	protected class FilterTableModel extends AbstractTableModel
	{
		private static final long serialVersionUID = -8220316170021615088L;

		boolean[] currentActiveValues = { false, false, false, false };
		double[] currentRadiusValues = { 1.5, 5.0, 5.0, 5.0 };

		boolean isEditable = true;

		final JTable table;
		final double medianDistance;

		final Object[][] filters = {
				{ false, "Single Spot Removing Filter", 1.5 },
				{ false, "Median Filter", 2.0 },
				{ false, "Gaussian Filter", 2.0 },
				{ false, "Mean/Avg Filter", 3.5 } };

		final String[] columnNames = { "Active", "Filter type", "Radius" };

		public FilterTableModel( final JTable table )
		{
			this.table = table;
			this.medianDistance = (data1.statistics().getMedianDistance() + data2.statistics().getMedianDistance()) / 2.0;
		}

		@Override
		public boolean isCellEditable( final int row, final int column )
		{
			return isEditable && (column == 0 || column == 2);
		}

		@Override
		public void setValueAt( final Object value, final int row, final int column )
		{
			filters[row][column] = value;

			boolean changed = false;
			for ( int r = 0; r < getRowCount(); ++r )
			{
				if ( currentActiveValues[ r ] != (boolean)filters[ r ][ 0 ] )
				{
					changed = true;
					currentActiveValues[ r ] = (boolean)filters[ r ][ 0 ];
				}

				if ( currentRadiusValues[ r ] != (double)filters[ r ][ 2 ] )
				{
					changed = true;
					currentRadiusValues[ r ] = (double)filters[ r ][ 2 ];
				}
			}

			if ( changed )
			{
				isEditable = false;

				new Thread( () ->
				{
					table.setForeground( Color.lightGray );

					// replace original values first
					sourceData.forEach( (gene,data) ->
					{
						final Iterator<Double> iAFilt = data.getA().originalValues().iterator();
						data.getA().tree().forEach( t -> t.set( iAFilt.next() ) );

						final Iterator<Double> iBFilt = data.getB().originalValues().iterator();
						data.getB().tree().forEach( t -> t.set( iBFilt.next() ) );
					} );

					if ( currentActiveValues[ 0 ] ) // single spot filter
					{
						sourceData.forEach( (gene,data) ->
						{
							final RealPointSampleList<DoubleType> filteredA =
									Filters.filter( data1.data().getExprData( gene ), data.getA().tree().iterator(), new SingleSpotRemovingFilterFactory<>( new DoubleType( 0 ), medianDistance * currentRadiusValues[ 0 ] ) );
							final RealPointSampleList<DoubleType> filteredB =
									Filters.filter( data2.data().getExprData( gene ), data.getB().tree().iterator(), new SingleSpotRemovingFilterFactory<>( new DoubleType( 0 ), medianDistance * currentRadiusValues[ 0 ] ) );

							final RealCursor<DoubleType> iAFilt = filteredA.cursor();
							data.getA().tree().forEach( t -> t.set( iAFilt.next() ) );

							final RealCursor<DoubleType> iBFilt = filteredB.cursor();
							data.getB().tree().forEach( t -> t.set( iBFilt.next() ) );
						} );
					}

					if ( currentActiveValues[ 1 ] ) // median filter
					{
						sourceData.forEach( (gene,data) ->
						{
							final RealPointSampleList<DoubleType> filteredA =
									Filters.filter( data1.data().getExprData( gene ), data.getA().tree().iterator(), new MedianFilterFactory<>( new DoubleType( 0 ), medianDistance * currentRadiusValues[ 1 ] ) );
							final RealPointSampleList<DoubleType> filteredB =
									Filters.filter( data2.data().getExprData( gene ), data.getB().tree().iterator(), new MedianFilterFactory<>( new DoubleType( 0 ), medianDistance * currentRadiusValues[ 1 ] ) );

							final RealCursor<DoubleType> iAFilt = filteredA.cursor();
							data.getA().tree().forEach( t -> t.set( iAFilt.next() ) );

							final RealCursor<DoubleType> iBFilt = filteredB.cursor();
							data.getB().tree().forEach( t -> t.set( iBFilt.next() ) );
						} );
					}

					if ( currentActiveValues[ 2 ] ) // Gaussian filter
					{
						sourceData.forEach( (gene,data) ->
						{
							final RealPointSampleList<DoubleType> filteredA =
									Filters.filter( data1.data().getExprData( gene ), data.getA().tree().iterator(), new GaussianFilterFactory<>( new DoubleType( 0 ), medianDistance * currentRadiusValues[ 2] ) );
							final RealPointSampleList<DoubleType> filteredB =
									Filters.filter( data2.data().getExprData( gene ), data.getB().tree().iterator(), new GaussianFilterFactory<>( new DoubleType( 0 ), medianDistance * currentRadiusValues[ 2 ] ) );

							final RealCursor<DoubleType> iAFilt = filteredA.cursor();
							data.getA().tree().forEach( t -> t.set( iAFilt.next() ) );

							final RealCursor<DoubleType> iBFilt = filteredB.cursor();
							data.getB().tree().forEach( t -> t.set( iBFilt.next() ) );
						} );
					}

					if ( currentActiveValues[ 3 ] ) // Mean filter
					{
						sourceData.forEach( (gene,data) ->
						{
							final RealPointSampleList<DoubleType> filteredA =
									Filters.filter( data1.data().getExprData( gene ), data.getA().tree().iterator(), new MeanFilterFactory<>( new DoubleType( 0 ), medianDistance * currentRadiusValues[ 3 ] ) );
							final RealPointSampleList<DoubleType> filteredB =
									Filters.filter( data2.data().getExprData( gene ), data.getB().tree().iterator(), new MeanFilterFactory<>( new DoubleType( 0 ), medianDistance * currentRadiusValues[ 3 ] ) );

							final RealCursor<DoubleType> iAFilt = filteredA.cursor();
							data.getA().tree().forEach( t -> t.set( iAFilt.next() ) );

							final RealCursor<DoubleType> iBFilt = filteredB.cursor();
							data.getB().tree().forEach( t -> t.set( iBFilt.next() ) );
						} );
					}

					bdvhandle.getViewerPanel().requestRepaint();
					System.out.println( "Filtered.");


					//SimpleMultiThreading.threadWait( 2000 );
					table.setForeground( Color.black );
					isEditable = true;
				}).start();
			}
		}

		@Override
		public Object getValueAt( final int row, final int column )
		{
			return filters[ row ][ column ];
		}

		@Override
		public Class<?> getColumnClass( final int column)
		{
			if ( column == 0 )
				return Boolean.class;
			else if ( column == 2 )
				return Double.class;
			else
				return String.class;
		}

		@Override
		public String getColumnName( final int column ) { return columnNames[ column ]; }

		@Override
		public int getRowCount() { return filters.length; }

		@Override
		public int getColumnCount() { return filters[ 0 ].length; }
		
	}
}
