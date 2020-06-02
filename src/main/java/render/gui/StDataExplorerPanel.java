/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2017 Multiview Reconstruction developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package render.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;

import align.Pairwise;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import data.STData;
import data.STDataStatistics;
import filter.GaussianFilterFactory;
import imglib2.ImgLib2Util;
import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import render.Render;

public class StDataExplorerPanel extends JPanel
{
	protected final static int maxRange = 100;

	private static final long serialVersionUID = -3767947754096099774L;

	protected final List< Pair< STData, STDataStatistics > > slides;
	protected final List< String > genes;

	protected JTable table;
	protected STDataTableModel tableModel;
	protected JLabel label;

	protected final RenderThread renderThread;
	protected final Thread thread;
	/*
	protected final BdvOptions options;
	protected BdvStackSource< ? > bdv = null;
	protected final Interval interval;
	protected final DoubleType outofbounds = new DoubleType( 0 );
	*/

	public StDataExplorerPanel( final List< Pair< STData, STDataStatistics > > slides, final List< String > genes )
	{
		initComponent( slides, genes );

		this.slides = slides;
		this.genes = genes;
		
		this.renderThread = new RenderThread( slides );
		this.thread = new Thread( this.renderThread );
		this.thread.start();

		/*
		this.interval = Pairwise.getCommonInterval( slides.stream().map( pair -> pair.getA() ).collect( Collectors.toList() ) );
		this.options = BdvOptions.options().is2D().numRenderingThreads( Runtime.getRuntime().availableProcessors() );
		this.bdv = BdvFunctions.show( Views.extendZero( ArrayImgs.doubles( 1, 1 ) ), interval, "", options );
		bdv.setDisplayRange( 0.9, 10 );
		bdv.setDisplayRangeBounds( 0, maxRange );
		bdv.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.SINGLE );
		*/
	}

	public STDataTableModel getTableModel() { return tableModel; }
	public JTable getTable() { return table; }

	int lastRow = -1, lastCol = -1;

	public void update()
	{
		final int row = table.getSelectedRows()[ 0 ];
		final int col = table.getSelectedColumns()[ 0 ];

		// row and col changed at the same time, both listeners fire
		if ( row == lastRow && col == lastCol )
		{
			return;
		}
		else
		{
			lastRow = row;
			lastCol = col;
		}

		final String gene = (String)tableModel.getValueAt( row, col );

		// please render this one if you can
		this.renderThread.globalQueue.add( new ValuePair<>( gene, col ) );
		synchronized ( thread )
		{
			if ( renderThread.isSleeping )
				this.thread.interrupt();
		}

		/*
		final Pair< STData, STDataStatistics > slide = slides.get( col );

		System.out.println( "gene: " + gene );
		System.out.println( "slide: " + slide.getA().toString() );

		IterableRealInterval< DoubleType > data = slide.getA().getExprData( gene );

		if ( data == null )
		{
			System.out.println( "gene " + gene + " does not exist for slide " + slide.getA().toString() );
			return;
		}

		data = Converters.convert(
				data,
				new Converter< DoubleType, DoubleType >()
				{
					@Override
					public void convert( final DoubleType input, final DoubleType output )
					{
						output.set( input.get() + 1.0 );
					}
				},
				new DoubleType() );

		final Pair< DoubleType, DoubleType > minmax = ImgLib2Util.minmax( data );

		System.out.println( "Min intensity: " + minmax.getA() );
		System.out.println( "Max intensity: " + minmax.getB() );

		// gauss crisp
		double gaussRenderSigma = slide.getB().getMedianDistance();
		double gaussRenderRadius = slide.getB().getMedianDistance() * 4;

		final RealRandomAccessible< DoubleType > renderRRA = Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) );

		BdvStackSource< ? > old = bdv;
		bdv = BdvFunctions.show( renderRRA, interval, gene, options.addTo( old ) );
		bdv.setDisplayRange( 0.9, minmax.getB().get() * 2 );
		bdv.setDisplayRangeBounds( 0, Math.max( maxRange, minmax.getB().get() * 10 ) );
		bdv.setCurrent();
		old.removeFromBdv();
		*/
	}

	public void initComponent( final List< Pair< STData, STDataStatistics > > slides, final List< String > genes )
	{
		tableModel = new STDataTableModel(
				this,
				slides.stream().map( pair -> pair.getA().toString() ).collect( Collectors.toList() ),
				genes );

		table = new JTable();
		table.setModel( tableModel );
		table.setSurrendersFocusOnKeystroke( true );
		//table.setSelectionMode( ListSelectionModel.SINGLE_INTERVAL_SELECTION );
		//table.setSelectionMode( ListSelectionModel.MULTIPLE_INTERVAL_SELECTION );
		table.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
		table.setCellSelectionEnabled( true );
		
		final MyRenderer myRenderer = new MyRenderer();
		myRenderer.setHorizontalAlignment( JLabel.CENTER );
		
		// center all columns
		for ( int column = 0; column < tableModel.getColumnCount(); ++column )
			table.getColumnModel().getColumn( column ).setCellRenderer( myRenderer );

		table.setPreferredScrollableViewportSize( new Dimension( 300, 400 ) );
		final Font f = table.getFont();
		
		table.setFont( new Font( f.getName(), f.getStyle(), 11 ) );
		
		this.setLayout( new BorderLayout() );
		this.label = new JLabel( "Selected gene: " );
		this.add( label, BorderLayout.NORTH );
		this.add( new JScrollPane( table ), BorderLayout.CENTER );

		for ( int column = 0; column < tableModel.getColumnCount(); ++column )
			table.getColumnModel().getColumn( column ).setPreferredWidth( 150 );

		table.getSelectionModel().addListSelectionListener( new ListSelectionListener()
		{
			public void valueChanged( ListSelectionEvent e )
			{
				// Ignore extra messages.
				if ( e.getValueIsAdjusting() )
					return;

				System.out.println( "A" );
				update();
			}
		} );

		table.getColumnModel().getSelectionModel().addListSelectionListener( new ListSelectionListener()
		{
			public void valueChanged( ListSelectionEvent e )
			{
				// Ignore extra messages.
				if ( e.getValueIsAdjusting() )
					return;

				System.out.println( "B" );

				update();
			}
		} );

		table.addMouseListener( new MouseListener()
		{
			@Override
			public void mouseReleased(MouseEvent e) {}
			
			@Override
			public void mousePressed(MouseEvent e) {}
			
			@Override
			public void mouseExited(MouseEvent e) {}
			
			@Override
			public void mouseEntered(MouseEvent e) {}
			
			@Override
			public void mouseClicked(MouseEvent e)
			{
				int row = table.rowAtPoint( e.getPoint() );
				int col = table.columnAtPoint( e.getPoint() );

				
				

				/*
				if ( tableModel.getSelectedRow() == row && tableModel.getSelectedCol() == col )
					tableModel.setSelected( -1, -1 );
				else
					tableModel.setSelected( row, col );

				// update everything
				final int sr = table.getSelectedRow();
				tableModel.fireTableDataChanged();
				table.setRowSelectionInterval( sr, sr );
				*/
			}
		});

		addPopupMenu( table );
	}

	protected static class MyRenderer extends DefaultTableCellRenderer
	{
		private static final long serialVersionUID = 1L;

		/*
		Color backgroundColor = getBackground();
		
		@Override
		public Component getTableCellRendererComponent( JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column )
		{
			final Component c = super.getTableCellRendererComponent( table, value, isSelected, hasFocus, row, column );
			final STDataTableModel model = (STDataTableModel) table.getModel();

			if ( model.getState( row, column ) )
				c.setBackground( Color.red );
			else if ( !isSelected )
				c.setBackground( backgroundColor );

			return c;
		}
		*/
	}

	protected void addPopupMenu( final JTable table )
	{
		final JPopupMenu popupMenu = new JPopupMenu();
		final JMenuItem deleteItem = new JMenuItem( "Delete" );

		deleteItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				System.out.println( "Right-click performed on table and choose DELETE" );
			}
		});

		popupMenu.add( deleteItem );

		table.setComponentPopupMenu( popupMenu );
	}
}
