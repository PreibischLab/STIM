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
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.swing.JFrame;

import data.STData;
import data.STDataStatistics;
import io.N5IO;
import io.Path;
import net.imglib2.util.Pair;
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

		for ( final String puck : pucks )
		{
			final STData slide = N5IO.readN5( new File( path + "slide-seq/" + puck + ".n5" ) );
			final STDataStatistics stat = new STDataStatistics( slide );

			slides.add( new ValuePair<>( slide, stat ) );
		}

		new STDataExplorer( slides );

		//final RealRandomAccessible< DoubleType > renderRRA = Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) );

	}
}
