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
package gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JCheckBox;
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
	private static final long serialVersionUID = -3767947154096099774L;

	protected final List< STDataAssembly > slides;
	protected final List< String > allGenes, allGenesLowerCase;
	protected final HashMap< String, Integer > geneToLocation;
	protected final List< HashSet< String > > genesPresentPerCol;

	protected JTable table;
	protected STDataTableModel tableModel;
	protected JLabel label;
	protected JTextField text;
	protected JCheckBox visualization;

	protected VisualizationOptionsFrame visFrame = null;

	protected final RenderThread renderThread;
	protected final Thread thread;

	public StDataExplorerPanel( final List< STDataAssembly > slides )
	{
		this.slides = slides;
		this.genesPresentPerCol = new ArrayList<>();

		final HashSet< String > genes = new HashSet<>();

		for ( final STDataAssembly slide : slides )
		{
			genes.addAll( slide.data().getGeneNames() );
			genesPresentPerCol.add( new HashSet<>( slide.data().getGeneNames() ) );
		}

		this.allGenes = new ArrayList<>( genes );
		this.allGenesLowerCase = new ArrayList<>();

		Collections.sort( allGenes );

		this.geneToLocation = new HashMap<>();

		for ( int i = 0; i < allGenes.size(); ++i )
		{
			final String lowerCase = allGenes.get( i ).toLowerCase();
			geneToLocation.put( lowerCase, i );
			allGenesLowerCase.add( lowerCase );
		}

		initComponent( slides, allGenes );

		this.renderThread = new RenderThread( slides );
		this.thread = new Thread( this.renderThread );
		this.thread.start();
	}

	public STDataTableModel getTableModel() { return tableModel; }
	public JTable getTable() { return table; }

	int lastRow = -1, lastCol = -1;
	boolean forceUpdate = false;

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
		if ( table.getSelectedRowCount() == 0 || table.getSelectedColumnCount() == 0 )
			return;

		final int row = table.getSelectedRows()[ 0 ];
		final int col = table.getSelectedColumns()[ 0 ];

		// row and col changed at the same time, both listeners fire
		if ( !forceUpdate && row == lastRow && col == lastCol )
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

	public void initComponent( final List< STDataAssembly > slides, final List< String > genes )
	{
		tableModel = new STDataTableModel(
				this,
				slides.stream().map( slide -> slide.data().toString() ).collect( Collectors.toList() ),
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

		table.setPreferredScrollableViewportSize( new Dimension( 650, 400 ) );
		final Font f = table.getFont();
		table.setFont( new Font( f.getName(), f.getStyle(), 11 ) );

		this.setLayout( new BorderLayout() );
		this.label = new JLabel( "Search gene:" );
		this.text = new JTextField( "" );
		this.text.setPreferredSize( new Dimension( 200, 24 ) );
		this.text.addKeyListener( new KeyListener()
		{
			@Override
			public void keyTyped( KeyEvent e ) {}

			@Override
			public void keyReleased( KeyEvent e )
			{
				String input = text.getText().trim().toLowerCase();

				final boolean searchFromBeginning = input.startsWith( "_" );
				if ( searchFromBeginning )
				{
					input = input.substring( 1 );
				}

				final String search = input;

				if ( search.length() == 0 )
					return;

				for ( final String s : allGenesLowerCase )
				{
					if ( searchFromBeginning ? s.toLowerCase().startsWith( search ) : s.toLowerCase().contains( search ) )
					{
						final int row = geneToLocation.get( s );
						System.out.println( search  + " >> " + s + " @ " + row  );

						final int col;

						if ( table.getSelectedColumnCount() == 0 )
							col = 0;
						else
							col = table.getSelectedColumns()[ 0 ];

						table.getColumnModel().getSelectionModel().setSelectionInterval( col, col );
						table.getSelectionModel().setSelectionInterval( row, row );
						table.scrollRectToVisible(new Rectangle(table.getCellRect(row, 0, true)));

						break;
					}
				}
			}

			@Override
			public void keyPressed( KeyEvent e ) {}
		} );

		this.visualization = new JCheckBox( "Visualization Options" );
		this.visualization.addActionListener(
				e ->
				{
					if ( this.visualization.isSelected() )
					{
						this.visFrame = new VisualizationOptionsFrame( this );
					}
					else if ( this.visFrame.isVisible() )
					{
						this.visFrame.dispose();
					}
				} );

		this.add( label, BorderLayout.WEST );
		this.add( text, BorderLayout.CENTER );

		this.add( visualization, BorderLayout.EAST );

		this.add( new JScrollPane( table ), BorderLayout.SOUTH );

		// ensure that the cells are not made smaller to match the size,
		// which introduces horizontal scrollbars if nessecary
		table.setAutoResizeMode( JTable.AUTO_RESIZE_OFF );

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
				//int row = table.rowAtPoint( e.getPoint() );
				//int col = table.columnAtPoint( e.getPoint() );
			}
		});

		//addPopupMenu( table );
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
