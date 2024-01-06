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
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import javax.swing.JFrame;

import io.Path;
import io.SpatialDataContainer;

public class STDataExplorer
{
	final JFrame frame;
	final StDataExplorerPanel panel;

	public STDataExplorer( final List< STDataAssembly > slides, final String inputContainer, final List< String > datasets )
	{
		frame = new JFrame( "Spatial Transcriptomics Explorer" );
		panel = new StDataExplorerPanel( slides, inputContainer, datasets );
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
		final ExecutorService service = Executors.newFixedThreadPool(8);
		final String inputPath = Path.getPath() + "visium.n5";
		final List< String > datasets = SpatialDataContainer.openExisting(inputPath, service).getDatasets();

		final List<STDataAssembly> slides =
				SpatialDataContainer.openExisting(inputPath, service).openAllDatasets().stream()
						.map(sdio ->
							 {try {return sdio.readData();} catch (IOException e) {throw new RuntimeException(e);}})
						.collect(Collectors.toList());

		new STDataExplorer( slides, inputPath, datasets );
		service.shutdown();
	}
}
