package gui.bdv;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import bdv.tools.transformation.TransformedSource;
import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.SourceGroup;
import bdv.viewer.SynchronizedViewerState;
import data.STDataUtils;
import filter.GaussianFilterFactory;
import filter.MeanFilterFactory;
import filter.RadiusSearchFilterFactory;
import filter.GaussianFilterFactory.WeightType;
import gui.STDataAssembly;
import imglib2.TransformedIterableRealInterval;
import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.KDTree;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import render.MaxDistanceParam;
import render.Render;
import util.BDVUtils;

public class AddedGene
{
	public static enum Rendering { Gauss, Mean, NN, Linear };

	final String inputPath, dataset;
	final STDataAssembly data;
	final RealRandomAccessible< DoubleType > rra;
	final KDTree< DoubleType > tree;
	final ArrayList< Double > originalValues;
	final private GaussianFilterFactory< DoubleType, DoubleType > gaussFactory;
	final private RadiusSearchFilterFactory< DoubleType, DoubleType > radiusFactory;
	final private MaxDistanceParam maxDistanceParam;
	final private BdvStackSource<?> source;
	final TransformedSource<?> transformedSource;
	final SourceAndConverter<?> soc;
	final private double min, max;

	public AddedGene(
			final String inputPath,
			final String dataset,
			final STDataAssembly data,
			final RealRandomAccessible< DoubleType > rra,
			final KDTree< DoubleType > tree,
			final GaussianFilterFactory< DoubleType, DoubleType > gaussFactory,
			final RadiusSearchFilterFactory< DoubleType, DoubleType > radiusFactory,
			final MaxDistanceParam maxDistanceParam,
			final BdvStackSource<?> source,
			final SourceAndConverter<?> soc,
			final TransformedSource<?> transformedSource,
			final double min,
			final double max )
	{
		this.inputPath = inputPath;
		this.dataset = dataset;
		this.data = data;
		this.rra = rra;
		this.tree = tree;
		this.gaussFactory = gaussFactory;
		this.radiusFactory = radiusFactory;
		this.maxDistanceParam = maxDistanceParam;
		this.source = source;
		this.soc = soc;
		this.transformedSource = transformedSource;
		this.min = min;
		this.max = max;

		this.originalValues = new ArrayList<>();
		tree.forEach( t -> originalValues.add( t.get() ) );
	}

	public List< Double > originalValues() { return originalValues; }

	public String inputPath() { return inputPath; }
	public String dataset() { return dataset; }
	public STDataAssembly data() { return data; }
	public RealRandomAccessible< DoubleType > rra() { return rra; }
	public KDTree< DoubleType > tree() { return tree; }
	public GaussianFilterFactory< DoubleType, DoubleType > gaussFactory(){ return gaussFactory; }
	public RadiusSearchFilterFactory< DoubleType, DoubleType > radiusFactory(){ return radiusFactory; }
	public MaxDistanceParam maxDistanceParam(){ return maxDistanceParam; }
	public BdvStackSource<?> source(){ return source; }
	public SourceAndConverter<?> soc() { return soc; }
	public TransformedSource<?> transformedSource() { return transformedSource; }
	public double min(){ return min; }
	public double max(){ return max; }

	public static synchronized void updateRemainingSources(
			final SynchronizedViewerState state,
			final Map< String, SourceGroup > geneToBDVSource,
			final Map< String, Pair< AddedGene, AddedGene > > sourceData )
	{
		final ArrayList< SourceGroup > currentGroups = new ArrayList<>( state.getGroups() );

		final ArrayList< String > toRemove = new ArrayList<>();
		for ( final Entry<String, SourceGroup > entry : geneToBDVSource.entrySet() )
			if ( !currentGroups.contains( entry.getValue() ) )
				toRemove.add( entry.getKey() );

		toRemove.forEach( s -> {
			geneToBDVSource.remove( s );
			sourceData.remove( s );
			} );
	}

	public static double getDisplayMin( final double min, final double max, final double bMin ) { return min + max * bMin; }
	public static double getDisplayMax( final double max, final double bMax ) { return max * bMax; }

	public static < T extends RealType<T>> double[] minmax( final Iterable< T > data )
	{
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;

		for ( final T t : data )
		{
			min = Math.min( min, t.getRealDouble() );
			max = Math.max( max, t.getRealDouble() );
		}

		return new double[] { min, max };
	}

	public static AddedGene addGene(
			final String inputContainer,
			final String dataset,
			final Rendering renderType,
			final Bdv bdv,
			final STDataAssembly data,
			final AffineTransform3D fixedTransform, // NOTE: options.sourceTransform != setFixedTransform
			final String gene,
			final double smoothnessFactor,
			final ARGBType color,
			final double relativeInitialBrightnessMin,
			final double relativeInitialBrightnessMax )
	{
		final double[] minmax = minmax( data.data().getExprData( gene ) );

		final double min = minmax[ 0 ];
		final double max = minmax[ 1 ];

		final double minDisplay = getDisplayMin( min, max, relativeInitialBrightnessMin );
		final double maxDisplay = getDisplayMax( max, relativeInitialBrightnessMax );

		System.out.println( "min/max: " + min + "/" + max );
		System.out.println( "min/max display range: " + minDisplay + "/" + maxDisplay );

		final RealRandomAccessible< DoubleType > rra;
		final KDTree< DoubleType > tree;
		final GaussianFilterFactory< DoubleType, DoubleType > gaussFactory;
		final RadiusSearchFilterFactory< DoubleType, DoubleType > radiusFactory;
		final MaxDistanceParam maxDistanceParam;

		final IterableRealInterval< DoubleType > iri = Render.getRealIterable( data, gene, null );
		final double medianDistance = data.statistics().getMedianDistance();

		if ( renderType == Rendering.Gauss )
		{
			gaussFactory = new GaussianFilterFactory<>( new DoubleType( 0 ), smoothnessFactor * medianDistance, WeightType.PARTIAL_BY_SUM_OF_WEIGHTS );
			radiusFactory = null;
			maxDistanceParam = null;

			final Pair<RealRandomAccessible<DoubleType>, KDTree<DoubleType>> r = Render.render2( iri, gaussFactory );
			rra = r.getA();
			tree = r.getB();
		}
		else if ( renderType == Rendering.NN )
		{
			maxDistanceParam = new MaxDistanceParam( smoothnessFactor * medianDistance );
			radiusFactory = null;
			gaussFactory = null;

			final Pair<RealRandomAccessible<DoubleType>, KDTree<DoubleType>> r = Render.renderNN2( iri, new DoubleType( 0 ), maxDistanceParam );
			rra = r.getA();
			tree = r.getB();
		}
		else if ( renderType == Rendering.Mean )
		{
			radiusFactory = new MeanFilterFactory<>( new DoubleType( 0 ), smoothnessFactor * medianDistance );
			maxDistanceParam = null;
			gaussFactory = null;

			final Pair<RealRandomAccessible<DoubleType>, KDTree<DoubleType>> r = Render.render2( iri, radiusFactory );
			rra = r.getA();
			tree = r.getB();
		}
		else // LINEAR
		{
			radiusFactory = null;
			gaussFactory = null;
			maxDistanceParam = new MaxDistanceParam( smoothnessFactor * medianDistance );

			final Pair<RealRandomAccessible<DoubleType>, KDTree<DoubleType>> r = Render.renderLinear2( iri, 5, 3.0, new DoubleType( 0 ), maxDistanceParam );
			rra = r.getA();
			tree = r.getB();
		}

		final Interval interval =
					STDataUtils.getIterableInterval(
							new TransformedIterableRealInterval<>(
									data.data(),
									new AffineTransform2D()/*data.transform()*/ ) );

		BdvOptions options = BdvOptions.options().numRenderingThreads(Math.max(2,Runtime.getRuntime().availableProcessors() / 2))
				.addTo(bdv).is2D().preferredSize(1000, 890);

		final BdvStackSource< ? > source = BdvFunctions.show( rra, interval, gene, options );

		// get TransformedSource (that is dynamically updated with the alignment)
		final TransformedSource<?> transformedSource = BDVUtils.getTransformedSource( source );

		SourceAndConverter< ? > soc = source.getSources().get( 0 );

		if ( fixedTransform != null )
			transformedSource.setFixedTransform( fixedTransform );

		source.setDisplayRangeBounds( 0, max );
		source.setDisplayRange( minDisplay, maxDisplay );
		source.setColor( color );
		source.setCurrent();

		/*
		final AffineTransform3D mipmapTransform = getMipmapTransforms()[ level ];
		currentSourceTransforms[ level ].set( reg );
		currentSourceTransforms[ level ].concatenate( mipmapTransform );
		 */

		final AffineTransform3D t = new AffineTransform3D();
		source.getBdvHandle().getViewerPanel().state().getViewerTransform( t );
		t.set( 0, 2, 3 );
		source.getBdvHandle().getViewerPanel().state().setViewerTransform( t );

		return new AddedGene(
				inputContainer, dataset, data, rra, tree, gaussFactory, radiusFactory,
				maxDistanceParam, source, soc, transformedSource, min, max );
	}
}