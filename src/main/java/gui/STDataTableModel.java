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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.swing.table.AbstractTableModel;

public class STDataTableModel extends AbstractTableModel
{
	private static final long serialVersionUID = -1263388435427674269L;
 
	final List< String > columnNames;
	final StDataExplorerPanel panel;

	final List< HashSet< String > > genesPresentPerCol;
	final List< String > allGenes;

	public STDataTableModel(
			final StDataExplorerPanel panel,
			final List< String > slideNames,
			final List< String > allGenes,
			final List< HashSet< String > > genesPresentPerCol )
	{
		this.columnNames = new ArrayList< String >();

		this.allGenes = allGenes;
		this.genesPresentPerCol = genesPresentPerCol;
		this.columnNames.addAll( slideNames );

		this.panel = panel;
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
		return allGenes.size();
	}

	@Override
	public boolean isCellEditable( final int row, final int column )
	{
		if ( column == 5 )
			return true;
		else
			return false;
	}

	@Override
	public void setValueAt( final Object value, final int row, final int column ) {}

	@Override
	public Object getValueAt( final int row, final int column )
	{
		final String geneName = allGenes.get( row );
		
		if ( this.genesPresentPerCol.get( column ).contains( geneName ) )
			return geneName;
		else
			return geneName + " [missing]";
	}

	@Override
	public String getColumnName( final int column )
	{
		return columnNames.get( column );
	}
}
