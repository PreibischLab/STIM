package util;
/*-
 * #%L
 * BigDataViewer core classes with minimal dependencies.
 * %%
 * Copyright (C) 2012 - 2021 BigDataViewer developers.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

import bdv.ui.UIUtils;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.text.DecimalFormat;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;
import net.miginfocom.swing.MigLayout;
import org.scijava.listeners.Listeners;

/**
 * A {@code JPanel} with a slider, a spinner, and a range bounds display.
 *
 * @author Tobias Pietzsch
 */
public class BoundedValuePanel extends JPanel
{
	private Supplier< JPopupMenu > popup;

	public interface ChangeListener
	{
		void boundedValueChanged();
	}

	private BoundedValue value;

	/**
	 * The range slider.
	 */
	private final JSlider valueSlider;

	/**
	 * Range slider number of steps.
	 */
	private static final int SLIDER_LENGTH = 10000;

	/**
	 * The value spinner.
	 */
	private final JSpinner valueSpinner;

	private final JLabel upperBoundLabel;

	private final JLabel lowerBoundLabel;

	private final Listeners.List< ChangeListener > listeners = new Listeners.SynchronizedList<>();

	public BoundedValuePanel()
	{
		this( new BoundedValue( 0, 1, 0.5 ) );
	}

	public BoundedValuePanel( final BoundedValue value )
	{
		setLayout( new MigLayout( "ins 5 0 5 10, fillx, filly, hidemode 3", "[grow]0[][]", "[]0[]" ) );

		valueSpinner = new JSpinner( new SpinnerNumberModel( 1.0, 0.0, 1.0, 1.0 ) );
		valueSlider = new JSlider( 0, SLIDER_LENGTH );
		upperBoundLabel = new JLabel();
		lowerBoundLabel = new JLabel();

		setupValueSpinner();
		setupRangeSlider();
		setupBoundLabels();
		setupPopupMenu();

		this.add(valueSlider, "growx, sy 2" );
		this.add( valueSpinner, "sy 2" );
		this.add( upperBoundLabel, "right, wrap" );
		this.add( lowerBoundLabel, "right" );

		setValue( value );
	}

	@Override
	public void setEnabled( final boolean enabled )
	{
		super.setEnabled( enabled );
		if ( valueSlider != null )
			valueSlider.setEnabled( enabled );
		if ( valueSpinner != null )
			valueSpinner.setEnabled( enabled );
		if ( upperBoundLabel != null )
			upperBoundLabel.setEnabled( enabled );
		if ( lowerBoundLabel != null )
			lowerBoundLabel.setEnabled( enabled );
	}

	@Override
	public void setBackground( final Color bg )
	{
		super.setBackground( bg );
		if ( valueSlider != null )
			valueSlider.setBackground( bg );
		if ( valueSpinner != null )
			valueSpinner.setBackground( bg );
		if ( upperBoundLabel != null )
			upperBoundLabel.setBackground( bg );
		if ( lowerBoundLabel != null )
			lowerBoundLabel.setBackground( bg );
	}

	private static class UnboundedNumberEditor extends JSpinner.NumberEditor
	{
		public UnboundedNumberEditor( final JSpinner spinner )
		{
			super( spinner );
			final JFormattedTextField ftf = getTextField();
			final DecimalFormat format = ( DecimalFormat ) ( ( NumberFormatter ) ftf.getFormatter() ).getFormat();
			final NumberFormatter formatter = new NumberFormatter( format );
			formatter.setValueClass( spinner.getValue().getClass() );
			final DefaultFormatterFactory factory = new DefaultFormatterFactory( formatter );
			ftf.setFormatterFactory( factory );
		}
	}

	private void setupValueSpinner()
	{
		UIUtils.setPreferredWidth( valueSpinner, 70 );

		valueSpinner.addChangeListener( e -> {
			final double value = ( Double ) valueSpinner.getValue();
			if ( value != this.value.getValue() )
				updateValue( this.value.withValue( value ) );
		} );

		valueSpinner.setEditor( new UnboundedNumberEditor( valueSpinner ) );
	}

	private void setupRangeSlider()
	{
		UIUtils.setPreferredWidth(valueSlider, 50 );
		valueSlider.setValue( 0 );
		valueSlider.setFocusable( false );

		valueSlider.addChangeListener( e -> {
			updateValue( value.withValue( posToValue( valueSlider.getValue() ) ) );
		} );

		valueSlider.addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized( final ComponentEvent e )
			{
				updateNumberFormat();
			}
		} );
	}

	private void setupBoundLabels()
	{
		final Font font = upperBoundLabel.getFont().deriveFont( 10f );
		upperBoundLabel.setFont( font );
		lowerBoundLabel.setFont( font );
		upperBoundLabel.setBorder( null );
		lowerBoundLabel.setBorder( null );
	}

	private void setupPopupMenu()
	{
		final MouseListener ml = new MouseAdapter()
		{
			@Override
			public void mousePressed( final MouseEvent e )
			{
				if ( e.isPopupTrigger() ||
						( e.getButton() == MouseEvent.BUTTON1 && e.getX() > upperBoundLabel.getX() ) )
					doPop( e );
			}

			@Override
			public void mouseReleased( final MouseEvent e )
			{
				if ( e.isPopupTrigger() )
					doPop( e );
			}

			private void doPop( final MouseEvent e )
			{
				if ( isEnabled() && popup != null )
				{
					final JPopupMenu menu = popup.get();
					if ( menu != null )
						menu.show( e.getComponent(), e.getX(), e.getY() );
				}
			}
		};
		this.addMouseListener( ml );
		valueSlider.addMouseListener( ml );
	}

	/**
	 * Convert range-slider position to value.
	 *
	 * @param pos
	 *            of range-slider
	 */
	private double posToValue( final int pos )
	{
		final double dmin = value.getMinBound();
		final double dmax = value.getMaxBound();
		return ( pos * ( dmax - dmin ) / SLIDER_LENGTH ) + dmin;
	}

	/**
	 * Convert value to range-slider position.
	 */
	private int valueToPos( final double value )
	{
		final double dmin = this.value.getMinBound();
		final double dmax = this.value.getMaxBound();
		return ( int ) Math.round( ( value - dmin ) * SLIDER_LENGTH / ( dmax - dmin ) );
	}

	private synchronized void updateNumberFormat()
	{
//		if ( userDefinedNumberFormat )
//			return;

		final int sw = valueSlider.getWidth();
		if ( sw > 0 )
		{
			final double vrange = value.getMaxBound() - value.getMinBound();
			final int digits = ( int ) Math.ceil( Math.log10( sw / vrange ) );

			blockUpdates = true;

			JSpinner.NumberEditor numberEditor = ( ( JSpinner.NumberEditor ) valueSpinner.getEditor() );
			numberEditor.getFormat().setMaximumFractionDigits( digits );
			numberEditor.stateChanged( new ChangeEvent( valueSpinner ) );

			blockUpdates = false;
		}
	}

	private synchronized void updateValue( final BoundedValue newValue )
	{
		if ( !blockUpdates )
			setValue( newValue );
	}

	private boolean blockUpdates = false;

	public synchronized void setValue( final BoundedValue value )
	{
		if ( Objects.equals( this.value, value ) )
			return;

		this.value = value;

		blockUpdates = true;

		final double minBound = value.getMinBound();
		final double maxBound = value.getMaxBound();

		final SpinnerNumberModel valueSpinnerModel = ( SpinnerNumberModel ) valueSpinner.getModel();
		valueSpinnerModel.setMinimum( minBound );
		valueSpinnerModel.setMaximum( maxBound );
		valueSpinnerModel.setValue( value.getValue() );

		valueSlider.setValue( valueToPos( value.getValue() ) );

		final double frac = Math.max(
				Math.abs( Math.round( minBound ) - minBound ),
				Math.abs( Math.round( maxBound ) - maxBound ) );
		final String format = frac > 0.005 ? "%.2f" : "%.0f";
		upperBoundLabel.setText( String.format( format, maxBound ) );
		lowerBoundLabel.setText( String.format( format, minBound ) );
		this.invalidate();

		blockUpdates = false;

		listeners.list.forEach( ChangeListener::boundedValueChanged);
	}

	public BoundedValue getValue()
	{
		return value;
	}

	public Listeners< ChangeListener > changeListeners()
	{
		return listeners;
	}

	public void setPopup( final Supplier< JPopupMenu > popup )
	{
		this.popup = popup;
	}

	public void setBoundsDialog()
	{
		final JPanel panel = new JPanel( new MigLayout( "fillx", "[][grow]", "" ) );
		final JSpinner minSpinner = new JSpinner( new SpinnerNumberModel( 0.0, 0.0, 1.0, 1.0 ) );
		final JSpinner maxSpinner = new JSpinner( new SpinnerNumberModel( 0.0, 0.0, 1.0, 1.0 ) );
		minSpinner.setEditor( new UnboundedNumberEditor( minSpinner ) );
		maxSpinner.setEditor( new UnboundedNumberEditor( maxSpinner ) );
		minSpinner.setValue( value.getMinBound() );
		maxSpinner.setValue( value.getMaxBound() );
		minSpinner.addChangeListener( e -> {
			final double value = ( Double ) minSpinner.getValue();
			if ( value > ( Double ) maxSpinner.getValue() )
				maxSpinner.setValue( value );
		} );
		maxSpinner.addChangeListener( e -> {
			final double value = ( Double ) maxSpinner.getValue();
			if ( value < ( Double ) minSpinner.getValue() )
				minSpinner.setValue( value );
		} );
		panel.add( "right", new JLabel( "min" ) );
		panel.add( "growx, wrap", minSpinner );
		panel.add( "right", new JLabel( "max" ) );
		panel.add( "growx", maxSpinner );
		final int result = JOptionPane.showConfirmDialog( null, panel, "Set Bounds", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE );
		if ( result == JOptionPane.YES_OPTION )
		{
			final double min = ( Double ) minSpinner.getValue();
			final double max = ( Double ) maxSpinner.getValue();
			updateValue( value.withMinBound( min ).withMaxBound( max ) );
		}
	}
}