package gui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.JTextField;

public class VisualizationOptionsFrame extends JFrame
{
	static Point location = null;
	private static final long serialVersionUID = -4951358486016907958L;

	final StDataExplorerPanel panel;

	public VisualizationOptionsFrame( final StDataExplorerPanel panel )
	{
		super( "Visualization options" );

		this.panel = panel;
		this.setSize( 400, 200 );
	
		setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );

		final JTextField textMedian = new JTextField( 3 );
		final JSlider sliderMedian = new JSlider( 0, 100, 0 );

		final JTextField textGauss = new JTextField( 2 );
		final JSlider sliderGauss = new JSlider( 0, 1000, 100 ); // maps to 0-10

		sliderMedian.addChangeListener( e -> textMedian.setText( String.valueOf( updateMedian( sliderMedian.getValue() ) ) ) );
		sliderGauss.addChangeListener( e -> textGauss.setText( String.valueOf( updateGauss( sliderGauss.getValue() ) / 100.0 ) ) );

		textMedian.addKeyListener(
				new KeyAdapter()
				{
					@Override
					public void keyReleased( final KeyEvent ke )
					{
						final String typed = textMedian.getText();
						if( typed.matches( "\\d+" ) )
							sliderMedian.setValue( updateMedian( Integer.parseInt( typed ) ) );
					}
				} );

		textGauss.addKeyListener(
				new KeyAdapter()
				{
					@Override
					public void keyReleased( final KeyEvent ke )
					{
						final String typed = textGauss.getText();
						if( typed.matches( "\\d+" ) )
							sliderGauss.setValue( updateGauss( Integer.parseInt( typed ) - 1 ) );
					}
				} );

		textMedian.setText( String.valueOf( RenderThread.medianFilter ) );
		sliderMedian.setValue( RenderThread.medianFilter );
		textGauss.setText( String.valueOf( RenderThread.gaussFactor ) );
		sliderGauss.setValue( (int)Math.round( RenderThread.gaussFactor * 100 ) );

		/* Location */
		final GridBagLayout layout = new GridBagLayout();
		this.setLayout( layout );

		final GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;

		c.gridy = 0;
		c.gridx = 0;
		c.gridwidth = 1;
		this.add( new JLabel("Median Filter"), c);
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 1;
		c.gridwidth = 2;
		this.add(sliderMedian, c);
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 3;
		c.gridwidth = 1;
		this.add(textMedian, c);

		c.gridy = 1;
		c.gridx = 0;
		c.gridwidth = 1;
		this.add( new JLabel("Gauss Rendering"), c);
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 1;
		c.gridwidth = 2;
		this.add(sliderGauss, c);
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 3;
		c.gridwidth = 1;
		this.add(textGauss, c);

		this.pack();
		this.setVisible( true );

		if ( location != null )
			this.setLocation( location );
	}

	protected int updateGauss( final int newValue )
	{
		RenderThread.gaussFactor = (double)newValue / 100.0;
		panel.forceUpdate = true;
		panel.update();
		panel.forceUpdate = false;
		return newValue;
	}

	protected int updateMedian( final int newValue )
	{
		RenderThread.medianFilter = newValue;
		panel.forceUpdate = true;
		panel.update();
		panel.forceUpdate = false;
		return newValue;
	}

	@Override
	public void dispose()
	{
		System.out.println( RenderThread.medianFilter + " " + RenderThread.gaussFactor );
		panel.visualization.setSelected( false );
		panel.visFrame = null;
		location = this.getLocation();
		super.dispose();
	}
}
