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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JFrame;

import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class GeneSelectionExplorer
{
	final JFrame frame;
	final GeneSelectionPanel panel;

	public GeneSelectionExplorer(
			final List< Pair< String, Double > > genes,
			final Consumer< List< String > > action )
	{
		frame = new JFrame( "Add Genes to BDV" );
		panel = new GeneSelectionPanel( this, genes, action );

		frame.add( panel, BorderLayout.CENTER );

		//frame.setSize( new Dimension( 300, 400 ) );
		frame.setSize( panel.getPreferredSize() );

		frame.pack();
		frame.setVisible( true );
		
		// Get the size of the screen
		//final Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();

		// Move the window
		//frame.setSize( new Dimension( 350, 480 ) );
		//frame.setSize( panel.getPreferredSize() );
		//frame.setLocation( ( dim.width - frame.getSize().width ) / 2, ( dim.height - frame.getSize().height ) / 4 );

		frame.addWindowListener( new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				quit();
				e.getWindow().dispose();
			}
		});
	}

	public JFrame frame() { return frame; }

	public GeneSelectionPanel panel() { return panel; }

	public void quit()
	{
		frame.setVisible( false );
		frame.dispose();
	}

	public void updateContent()
	{
		panel.getTableModel().update();
		panel.getTableModel().fireTableDataChanged();
	}

	public static void main( String[] args )
	{
		final List< Pair< String, Double > > genes = new ArrayList<>();

		for ( int i = 100; i >=0; --i )
			genes.add(new ValuePair<>("gene " + i, (double) i) );

		new GeneSelectionExplorer( genes, list -> list.forEach(System.out::println) );
	}
}
