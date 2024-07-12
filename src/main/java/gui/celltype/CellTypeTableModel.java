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

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map.Entry;

import javax.swing.table.AbstractTableModel;

import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RealLocalizable;
import net.imglib2.type.numeric.ARGBType;

public class CellTypeTableModel extends AbstractTableModel
{
	private static final long serialVersionUID = -1263388435427674269L;
	
	final ArrayList< String > columnNames;
	final HashMap< Long, Color > lut;
	final ArrayList< Long > entries;
	final CellTypeExplorerPanel panel;

	HashMap< ViewId, Collection< ? extends RealLocalizable > > points = new HashMap<>();

	public CellTypeTableModel( final HashMap< Long, ARGBType > lut, final CellTypeExplorerPanel panel )
	{
		this.columnNames = new ArrayList<>();

		this.columnNames.add( "Cell Type" );

		this.lut = new HashMap<>();

		for ( final Entry< Long, ARGBType > entry : lut.entrySet() )
		{
			final ARGBType col = entry.getValue();
			final int r = ARGBType.red( col.get() );
			final int g = ARGBType.green( col.get() );
			final int b = ARGBType.blue( col.get() );
			final int a = ARGBType.alpha( col.get() );

			this.lut.put( entry.getKey(), new Color(r, g, b, a) );
		}

		this.entries = new ArrayList<>( this.lut.keySet() );
		Collections.sort( entries );

		this.panel = panel;
	}

	@Override
	public int getColumnCount() { return columnNames.size(); }
	
	@Override
	public int getRowCount()
	{
		return lut.keySet().size();
	}

	@Override
	public Object getValueAt( final int row, final int column )
	{
		return entries.get( row );
	}

	@Override
	public String getColumnName( final int column )
	{
		return columnNames.get( column );
	}

	public Color getRowColor( final int row )
	{
		return lut.get( entries.get( row ) );
	}
}
