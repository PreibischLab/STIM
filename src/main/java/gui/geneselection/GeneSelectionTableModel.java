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

import java.util.ArrayList;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import net.imglib2.util.Pair;

public class GeneSelectionTableModel extends AbstractTableModel
{
	private static final long serialVersionUID = 7165959978585019639L;

	final List< String > columnNames;
	final GeneSelectionPanel panel;

	final List< Pair< String, Double > > data;

	public GeneSelectionTableModel(
			final GeneSelectionPanel panel,
			final List< Pair< String, Double > > data )
	{
		this.panel = panel;
		this.data = data;

		this.columnNames = new ArrayList< String >();
		this.columnNames.add( "Gene name" );
		this.columnNames.add( "StDev (expression)" );
	}

	protected void update()
	{
		// update everything
		fireTableDataChanged();
	}

	@Override
	public int getColumnCount() { return columnNames.size(); }
	
	@Override
	public int getRowCount()
	{
		return data.size();
	}

	@Override
	public boolean isCellEditable( final int row, final int column )
	{
		return false;//(column == 5);
	}

	@Override
	public void setValueAt( final Object value, final int row, final int column ) {}

	@Override
	public Object getValueAt( final int row, final int column )
	{
		if ( column == 0 )
			return data.get( row ).getA();
		else
			return data.get( row ).getB();
	}

	@Override
	public String getColumnName( final int column )
	{
		return columnNames.get( column );
	}
}
