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
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import javax.swing.JFrame;

import org.janelia.saalfeldlab.n5.N5FSReader;

import data.STData;
import data.STDataStatistics;
import io.N5IO;
import io.Path;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.RealSum;
import net.imglib2.util.ValuePair;

public class STDataExplorer
{
	final JFrame frame;
	final StDataExplorerPanel panel;

	public STDataExplorer( final List< Pair< STData, STDataStatistics > > slides )
	{
		frame = new JFrame( "Interest Point Explorer" );
		panel = new StDataExplorerPanel( slides );
		frame.add( panel, BorderLayout.CENTER );

		frame.setSize( panel.getPreferredSize() );

		frame.pack();
		frame.setVisible( true );
		
		// Get the size of the screen
		final Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();

		// Move the window
		frame.setLocation( ( dim.width - frame.getSize().width ) / 2, ( dim.height - frame.getSize().height ) / 4 );

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

	public StDataExplorerPanel panel() { return panel; }

	public void quit()
	{
		panel().renderThread.keepRunning.set( false );
		frame.setVisible( false );
		frame.dispose();
	}

	public void updateContent()
	{
		panel.getTableModel().update();
		panel.getTableModel().fireTableDataChanged();
	}

	public static void main( String[] args ) throws IOException
	{
		final String path = Path.getPath();
		final String[] pucks = new String[] { "Puck_180531_23", "Puck_180531_22" };

		final ArrayList< Pair< STData, STDataStatistics > > slides = new ArrayList<>();
		final N5FSReader n5 = N5IO.openN5( new File( path + "slide-seq-normalized.n5" ) );
		// final List< String > pucks = N5IO.listAllDatasets( n5 );

		for ( final String puck : pucks )
		{
			final STData slide = /*new NormalizingSTData*/( N5IO.readN5( n5, puck ) );//.copy();

			final STDataStatistics stat = new STDataStatistics( slide );

			slides.add( new ValuePair<>( slide, stat ) );
		}

		/*
			mt-Rnr2: 98406.00148799573
			Malat1: 57573.36309555761
			Calm1: 51896.11190606482
			Fth1: 50462.27704927554
			mt-Cytb: 40567.66176993931
			mt-Nd1: 38026.647857079464
			Actb: 36143.7881367091 <<
			Cst3: 35665.60488383998
			Ubb: 33296.58213607102
			Apoe: 32112.9165355219
			
			mt-Rnr2: 2.77292097770305
			Malat1: 2.7092015887283845
			Fth1: 2.579159459129725
			Calm1: 2.5671422766341654
			mt-Cytb: 2.4166567930779324
			mt-Nd1: 2.354738505000795
			Cst3: 2.3154520298436196
			Actb: 2.280770607027878
			Apoe: 2.2256420300803264
			Ubb: 2.2035294179635043
		 */
		new STDataExplorer( slides );
	}
}
