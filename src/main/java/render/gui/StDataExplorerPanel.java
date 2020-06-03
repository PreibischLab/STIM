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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;

import data.STData;
import data.STDataStatistics;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class StDataExplorerPanel extends JPanel
{
	protected final static int maxRange = 100;

	private static final long serialVersionUID = -3767947754096099774L;

	protected final List< Pair< STData, STDataStatistics > > slides;
	protected final List< String > allGenes;
	protected final List< HashSet< String > > genesPresentPerCol;

	protected JTable table;
	protected STDataTableModel tableModel;
	protected JLabel label;
	protected JTextField text;

	protected final RenderThread renderThread;
	protected final Thread thread;

	public StDataExplorerPanel( final List< Pair< STData, STDataStatistics > > slides )
	{
		this.slides = slides;
		this.genesPresentPerCol = new ArrayList<>();

		final HashSet< String > genes = new HashSet<>();

		for ( final Pair< STData, STDataStatistics > slide : slides )
		{
			genes.addAll( slide.getA().getGeneNames() );
			genesPresentPerCol.add( new HashSet<>( slide.getA().getGeneNames() ) );
		}

		this.allGenes = new ArrayList<>( genes );

		Collections.sort( allGenes );

		initComponent( slides, allGenes );


		this.renderThread = new RenderThread( slides );
		this.thread = new Thread( this.renderThread );
		this.thread.start();
	}

	public STDataTableModel getTableModel() { return tableModel; }
	public JTable getTable() { return table; }

	int lastRow = -1, lastCol = -1;

	protected List< String > allGenes( final List< Pair< STData, STDataStatistics > > slides )
	{
		final HashSet< String > genes = new HashSet<>();

		for ( final Pair< STData, STDataStatistics > slide : slides )
			genes.addAll( slide.getA().getGeneNames() );

		final ArrayList< String > geneList = new ArrayList<>( genes );
		Collections.sort( geneList );

		return geneList;
	}

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

		if ( !this.genesPresentPerCol.get( col ).contains( gene ) )
			return;

		// please render this one if you can
		this.renderThread.globalQueue.add( new ValuePair<>( gene, col ) );

		// wake thread up if sleeping
		synchronized ( thread )
		{
			if ( renderThread.isSleeping.getAndSet( false ) )
				this.thread.interrupt();
		}
	}

	public void initComponent( final List< Pair< STData, STDataStatistics > > slides, final List< String > genes )
	{
		tableModel = new STDataTableModel(
				this,
				slides.stream().map( pair -> pair.getA().toString() ).collect( Collectors.toList() ),
				allGenes,
				genesPresentPerCol );

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
		this.label = new JLabel( "Search gene: " );
		this.add( label, BorderLayout.NORTH );
		this.add( new JTextField( "Pcp4" ), BorderLayout.NORTH );
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
