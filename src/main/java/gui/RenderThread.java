package gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.swing.SwingUtilities;

import bdv.ui.BdvDefaultCards;
import bdv.ui.splitpanel.SplitPanel;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import bdv.viewer.SourceGroup;
import bdv.viewer.SynchronizedViewerState;
import data.STDataUtils;
import gui.bdv.AddedGene;
import gui.bdv.AddedGene.Rendering;
import gui.bdv.STIMCard;
import gui.bdv.STIMCardFilter;
import imglib2.TransformedIterableRealInterval;
import net.imglib2.Interval;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

public class RenderThread implements Runnable
{
	protected final BdvOptions options;
	protected BdvStackSource< ? > bdv;
	protected final Interval interval;
	protected final DoubleType outofbounds = new DoubleType( 0 );

	final String inputContainer;
	final List< String > datasets;
	final DisplayScaleOverlay overlay;
	final HashMap< String, List< AddedGene > > sourceData = new HashMap<>();
	final HashMap< String, SourceGroup > geneToBDVSource = new HashMap<>();
	protected final List< STDataAssembly > slides;

	protected STIMCard card = null;

	final Queue< Pair< String, Integer > > globalQueue = new ConcurrentLinkedQueue<>();

	public AtomicBoolean keepRunning = new AtomicBoolean( true );
	public AtomicBoolean isSleeping = new AtomicBoolean( false );

	public RenderThread( final List< STDataAssembly > slides, final String inputContainer, final List< String > datasets )
	{
		this.slides = slides;
		this.inputContainer = inputContainer;
		this.datasets = datasets;
		this.interval =
				STDataUtils.getCommonIterableInterval(
						slides.stream().map(
								slide -> new TransformedIterableRealInterval<>( slide.data(), slide.transform() ) )
						.collect( Collectors.toList() ) );

		this.options = BdvOptions.options().is2D().numRenderingThreads( Runtime.getRuntime().availableProcessors() );
		this.bdv = BdvFunctions.show( Views.extendZero( ArrayImgs.doubles( 1, 1 ) ), interval, "", options );
		this.bdv.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.SINGLE );

		this.overlay = new DisplayScaleOverlay();

		SwingUtilities.invokeLater( () ->
		{
			// add scale (so the right size of the images for alignment can be selected)
			bdv.getBdvHandle().getViewerPanel().renderTransformListeners().add(overlay);
			bdv.getBdvHandle().getViewerPanel().getDisplay().overlays().add(overlay);
	
			// show scalebar (so the right error can be selected)
			bdv.getBdvHandle().getAppearanceManager().appearance().setShowScaleBar( true );
	
			// collapse all existing panels (except sources)
			bdv.getBdvHandle().getCardPanel().setCardExpanded(BdvDefaultCards.DEFAULT_SOURCEGROUPS_CARD, false); // collapse groups panel
			bdv.getBdvHandle().getCardPanel().setCardExpanded(BdvDefaultCards.DEFAULT_SOURCES_CARD, false); // collapse sources panel
			bdv.getBdvHandle().getCardPanel().setCardExpanded(BdvDefaultCards.DEFAULT_VIEWERMODES_CARD, false); // collapse display modes panel
		});
	}

	@Override
	public void run()
	{
		do
		{
			try
			{
				Thread.interrupted();

				isSleeping.set( true );

				// avoid 
				synchronized ( this )
				{
					Thread.sleep( 2000 );
					isSleeping.set( false );
				}
			}
			catch (InterruptedException e)
			{
				isSleeping.set( false );
				Thread.interrupted();
			}

			Pair< String, Integer > lastElement = null;
			Pair< String, Integer > element;

			synchronized ( globalQueue )
			{
				do
				{
					element = globalQueue.poll();
					if ( element != null )
						lastElement = element;
				}
				while ( element != null );
			}

			if ( lastElement != null )
			{
				final String gene = lastElement.getA();
				final STDataAssembly slide = slides.get( lastElement.getB() );

				System.out.println( "rendering gene: " + gene + " of slide: " + slide.data().toString() );

				// not initalized
				if ( card == null )
				{
					final BdvStackSource<?> old = bdv;

					final AddedGene addedGene = AddedGene.addGene(
							inputContainer,
							datasets.get( lastElement.getB() ),
							Rendering.Gauss,
							bdv,
							slide,
							null, //AddedGene.convert2Dto3D( slide.transform() ), //m3d,
							gene,
							1.5,
							new ARGBType( ARGBType.rgba(255, 255, 255, 0) ),
							0,
							0.5 );

					bdv = addedGene.source();
					bdv.setCurrent();
					old.removeFromBdv();

					sourceData.put( gene, new ArrayList<>( Arrays.asList( addedGene ) ) );

					final SynchronizedViewerState state = bdv.getBdvHandle().getViewerPanel().state();
					final ArrayList< SourceGroup > oldGroups = new ArrayList<>( state.getGroups() );

					final SourceGroup handle = new SourceGroup();
					state.addGroup( handle );
					state.setGroupName( handle, gene );
					state.setGroupActive( handle, true );
					state.addSourceToGroup( state.getSources().get(0), handle );

					geneToBDVSource.put( gene, handle );

					bdv.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.GROUP );
					state.removeGroups( oldGroups );

					// add STIMCard panel
					card = new STIMCard(
							new ArrayList<>( Arrays.asList( addedGene.data() ) ),
							addedGene.data().data().getGeneNames().stream().map( s -> new ValuePair<String, Double>(s, null) ).collect( Collectors.toList() ),
							sourceData,
							geneToBDVSource,
							overlay,
							addedGene.data().statistics().getMedianDistance(),
							Rendering.Gauss, 1.5, 0, 0.5, bdv.getBdvHandle());

					// add STIMCardFilter panel
					final STIMCardFilter cardFilter = new STIMCardFilter( card, null, null, null, null, Executors.newFixedThreadPool( 1 ));

					SwingUtilities.invokeLater( () -> 
					{
						bdv.getBdvHandle().getCardPanel().addCard( "STIM Display Options", "STIM Display Options", card.getPanel(), true );
						bdv.getBdvHandle().getCardPanel().addCard( "STIM Filtering Options", "STIM Filtering Options", cardFilter.getPanel(), true );
	
						// the side panel
						final SplitPanel splitPanel = bdv.getBdvHandle().getSplitPanel();
	
						// Expands the split Panel (after waiting 1 secs for the BDV to calm down)
						//SimpleMultiThreading.threadWait( 1000 );
						splitPanel.setCollapsed(false);
					});
				}
				else
				{
					final List< String > geneList = new ArrayList<>( Arrays.asList( gene ) );
					final List< String > inputPaths = new ArrayList<>( Arrays.asList( inputContainer ) );
					final List< String > datasets = new ArrayList<>( Arrays.asList( this.datasets.get( lastElement.getB() ) ) );
					final List< AffineTransform3D > transforms = new ArrayList<>( Arrays.asList( new AffineTransform3D() ) );
					final List< ARGBType > colors = new ArrayList<>( Arrays.asList( new ARGBType( ARGBType.rgba(255, 255, 255, 0) ) ) );

					final SynchronizedViewerState state = bdv.getBdvHandle().getViewerPanel().state();
					final ArrayList< SourceGroup > oldGroups = new ArrayList<>( state.getGroups() );

					// could be same gene, different dataset - that is then not added
					card.geneToBDVSource().clear();
					card.data().set( 0, slide );
					final HashMap<String, List<AddedGene> > added = card.addGenes( geneList, inputPaths, datasets, transforms, colors);

					bdv = added.values().iterator().next().get( 0 ).source();
					state.removeGroups( oldGroups );
				}
			}
			else
			{
				//System.out.println( "queue empty." );
			}
		}
		while ( keepRunning.get() );

		bdv.close();
	}
}
