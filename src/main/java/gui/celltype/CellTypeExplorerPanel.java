/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2021 Multiview Reconstruction developers.
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
package gui.celltype;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.HashMap;
import java.util.function.Function;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;

import bdv.viewer.ViewerPanel;
import net.imglib2.type.numeric.ARGBType;

public class CellTypeExplorerPanel extends JPanel implements Function<Long, Boolean>
{
	/**
	 * 
	 */
	private static final long serialVersionUID = -324210427063909058L;

	protected JTable table;
	protected CellTypeTableModel tableModel;
	protected JLabel label;
	ViewerPanel panel = null;

	public CellTypeExplorerPanel( final HashMap< Long, ARGBType > lut )
	{
		initComponent( lut );
	}

	public CellTypeTableModel getTableModel() { return tableModel; }
	public JTable getTable() { return table; }

	public void setBDV( final ViewerPanel panel )
	{
		this.panel = panel;
	}

	//if ( table.getSelectedRowCount() == 0 )
	//	table.getSelectionModel().setSelectionInterval( 0, 0 );

	public void initComponent( final HashMap< Long, ARGBType > lut )
	{
		tableModel = new CellTypeTableModel( lut, this );

		table = new JTable();
		table.setModel( tableModel );
		table.setSurrendersFocusOnKeystroke( true );
		table.setSelectionMode( ListSelectionModel.MULTIPLE_INTERVAL_SELECTION );
		
		final MyRenderer myRenderer = new MyRenderer();
		myRenderer.setHorizontalAlignment( JLabel.CENTER );
		
		// center all columns
		for ( int column = 0; column < tableModel.getColumnCount(); ++column )
			table.getColumnModel().getColumn( column ).setCellRenderer( myRenderer );

		table.setPreferredScrollableViewportSize( new Dimension( 150, 600 ) );
		final Font f = table.getFont();
		
		table.setFont( new Font( f.getName(), f.getStyle(), 11 ) );
		
		this.setLayout( new BorderLayout() );
		this.add( new JScrollPane( table ), BorderLayout.CENTER );

		table.getColumnModel().getColumn( 0 ).setPreferredWidth( 150 );

		table.addMouseListener( new MouseListener()
		{
			@Override
			public void mouseReleased(MouseEvent e) { table.repaint(); }

			@Override
			public void mousePressed(MouseEvent e) { table.repaint();}
			
			@Override
			public void mouseExited(MouseEvent e) {}
			
			@Override
			public void mouseEntered(MouseEvent e) {}
			
			@Override
			public void mouseClicked(MouseEvent e) { table.repaint(); }
		});
	}

	protected class MyRenderer extends DefaultTableCellRenderer
	{
		private static final long serialVersionUID = 1L;

		Color backgroundColor = getBackground();
		
		@Override
		public Component getTableCellRendererComponent( JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column )
		{
			final Component c = super.getTableCellRendererComponent( table, value, isSelected, hasFocus, row, column );
			final CellTypeTableModel model = (CellTypeTableModel) table.getModel();

			if ( table.getSelectedRowCount() > 0 ) // anything is selected
			{
				if ( isSelected )
					c.setBackground( model.getRowColor( row ) );
				else
					c.setBackground( backgroundColor );
			}
			else
			{
				c.setBackground( model.getRowColor( row ) );
			}

			if ( panel != null )
				panel.requestRepaint();
			return c;
		}
	}

	@Override
	public Boolean apply( final Long t )
	{
		// nothing is selected
		if ( table.getSelectedRowCount() == 0 )
			return true;

		// true for whatever is selected
		for ( final int i : table.getSelectedRows() )
			if ( i == t.intValue() )
				return true;

		return false;
	}
}
