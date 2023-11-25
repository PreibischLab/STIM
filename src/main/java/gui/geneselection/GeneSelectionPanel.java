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
package gui.geneselection;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

import data.STData;
import data.STDataStatistics;
import net.imglib2.util.Pair;

public class GeneSelectionPanel extends JPanel
{
	private static final long serialVersionUID = -3767947154096099774L;

	protected final List< Pair< String, Double > > data;

	final GeneSelectionExplorer parent;

	protected final List< String > allGenesLowerCase;
	protected final HashMap< String, Integer > geneToLocation;

	protected JTable table;
	protected GeneSelectionTableModel tableModel;
	protected JLabel label;
	protected JTextField text;

	/**
	 * 
	 * @param data - sorted list of pair (geneName, stDev)
	 */
	public GeneSelectionPanel(
			final GeneSelectionExplorer parent,
			final List< Pair< String, Double > > data,
			final Consumer< List< String > > action )
	{
		this.parent = parent;
		this.data = data;

		this.geneToLocation = new HashMap<>();
		this.allGenesLowerCase = new ArrayList<>();

		for ( int i = 0; i < data.size(); ++i )
		{
			final String lowerCase = data.get( i ).getA().toLowerCase();
			geneToLocation.put( lowerCase, i );
			allGenesLowerCase.add( lowerCase );
		}

		initComponent( data, action );
	}

	public GeneSelectionTableModel getTableModel() { return tableModel; }
	public JTable getTable() { return table; }

	protected List< String > allGenes( final List< Pair< STData, STDataStatistics > > slides )
	{
		final HashSet< String > genes = new HashSet<>();

		for ( final Pair< STData, STDataStatistics > slide : slides )
			genes.addAll( slide.getA().getGeneNames() );

		final ArrayList< String > geneList = new ArrayList<>( genes );
		Collections.sort( geneList );

		return geneList;
	}

	public void initComponent(
			final List< Pair< String, Double > > data,
			final Consumer< List< String > > action )
	{
		tableModel = new GeneSelectionTableModel( this, data );

		table = new JTable();
		table.setModel( tableModel );
		table.setSurrendersFocusOnKeystroke( true );
		//table.setSelectionMode( ListSelectionModel.SINGLE_INTERVAL_SELECTION );
		table.setSelectionMode( ListSelectionModel.MULTIPLE_INTERVAL_SELECTION );
		//table.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
		//table.setCellSelectionEnabled( true );
		table.setCellSelectionEnabled( false );
		table.setRowSelectionAllowed( true );

		final TableCellRenderer rendererFromHeader = table.getTableHeader().getDefaultRenderer();
		final JLabel headerLabel = (JLabel) rendererFromHeader;
		headerLabel.setHorizontalAlignment(JLabel.CENTER);

		final MyRenderer myRenderer = new MyRenderer();
		myRenderer.setHorizontalAlignment( JLabel.CENTER );
		
		// center all columns
		for ( int column = 0; column < tableModel.getColumnCount(); ++column )
			table.getColumnModel().getColumn( column ).setCellRenderer( myRenderer );

		table.setPreferredScrollableViewportSize( new Dimension( 350, 500 ) );
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
						//System.out.println( search  + " >> " + s + " @ " + row  );

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
			public void keyPressed( KeyEvent e )
			{
				if ( e.getKeyCode()==KeyEvent.VK_ENTER )
				{
					go( action );
				}
			}
		} );

		final JButton addButton = new JButton( "Add & Close" );
		addButton.addActionListener( e ->
		{
			go( action );
			/*
			final int[] rows = table.getSelectedRows();
			if ( rows != null && rows.length > 0 )
			{
				final ArrayList< String > selected = new ArrayList<>();
				for ( final int r : rows )
					selected.add( data.get( r ).getA() );

				action.accept( selected );
			}

			parent.quit();*/
		});

		this.add( label, BorderLayout.WEST );
		this.add( text, BorderLayout.CENTER );
		this.add( addButton, BorderLayout.EAST );

		this.add( new JScrollPane( table ), BorderLayout.SOUTH );

		// ensure that the cells are not made smaller to match the size,
		// which introduces horizontal scrollbars if necessary
		table.setAutoResizeMode( JTable.AUTO_RESIZE_OFF );

		for ( int column = 0; column < tableModel.getColumnCount(); ++column )
			table.getColumnModel().getColumn( column ).setPreferredWidth( 150 );

		/*
		table.getSelectionModel().addListSelectionListener(e -> {
			// Ignore extra messages.
			if ( e.getValueIsAdjusting() )
				return;
			update();
		});

		table.getColumnModel().getSelectionModel().addListSelectionListener(e -> {
			// Ignore extra messages.
			if ( e.getValueIsAdjusting() )
				return;
			update();
		});
		 */

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

		//if ( popUpAction != null && popUpItemName != null )
		//	addPopupMenu( table, popUpItemName, popUpAction );
	}

	protected void go( final Consumer< List< String > > action )
	{
		final int[] rows = table.getSelectedRows();
		if ( rows != null && rows.length > 0 )
		{
			final ArrayList< String > selected = new ArrayList<>();
			for ( final int r : rows )
				selected.add( data.get( r ).getA() );

			action.accept( selected );
		}

		parent.quit();
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

	protected void addPopupMenu( final JTable table, final String itemName, final Consumer< List< String > > action )
	{
		
		final JPopupMenu popupMenu = new JPopupMenu();
		final JMenuItem item = new JMenuItem( itemName );

		item.addActionListener(
				e ->
				{
					final int[] rows = table.getSelectedRows();
					if ( rows != null && rows.length > 0 )
					{
						final ArrayList< String > selected = new ArrayList<>();
						for ( final int r : rows )
							selected.add( data.get( r ).getA() );

						action.accept( selected );
					}
				});

		popupMenu.add( item );

		table.setComponentPopupMenu( popupMenu );
	}
}
