package gui.bdv;

import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import align.AlignTools;
import align.PairwiseSIFT;
import align.PairwiseSIFT.SIFTParam;
import align.PairwiseSIFT.SIFTParam.SIFTMatching;
import align.SiftMatch;
import bdv.tools.transformation.TransformedSource;
import bdv.util.BdvHandle;
import bdv.viewer.DisplayMode;
import bdv.viewer.SourceGroup;
import bdv.viewer.SynchronizedViewerState;
import cmd.InteractiveAlignment.AddedGene;
import gui.DisplayScaleOverlay;
import gui.STDataAssembly;
import gui.geneselection.GeneSelectionExplorer;
import gui.overlay.SIFTOverlay;
import mpicbg.models.Affine2D;
import mpicbg.models.AffineModel2D;
import mpicbg.models.InterpolatedAffineModel2D;
import mpicbg.models.Model;
import mpicbg.models.RigidModel2D;
import mpicbg.models.SimilarityModel2D;
import mpicbg.models.TranslationModel2D;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.miginfocom.swing.MigLayout;
import util.BDVUtils;
import util.BoundedValue;
import util.BoundedValuePanel;

public class STIMAlignmentCard
{
	private final JPanel panel;
	private GeneSelectionExplorer gse = null;
	private final SIFTOverlay siftoverlay;

	final String optionsModel[] = { "Translation", "Rigid", "Similarity", "Affine" };
	final String optionsModelReg[] = { "No Reg.", "Transl.", "Rigid", "Simil.", "Affine" };

	public STIMAlignmentCard(
			final STDataAssembly data1,
			final STDataAssembly data2,
			final String dataset1,
			final String dataset2,
			final DisplayScaleOverlay overlay,
			final STIMCard stimcard,
			final List< Pair< String, Double > > allGenes,
			final HashMap< String, Pair< AddedGene, AddedGene > > sourceData,
			final HashMap< String, SourceGroup > geneToBDVSource,
			final double medianDistance,
			final double errorInit,
			final int minNumInliersInit,
			final int minNumInliersGeneInit,
			final int numGenesInit,
			final BdvHandle bdvhandle,
			final ExecutorService service )
	{
		this.siftoverlay = new SIFTOverlay( new ArrayList<>(), bdvhandle );
		this.panel = new JPanel(new MigLayout("gap 0, ins 5 5 5 5, fill", "[right][grow]", "center"));

		final String options[] = { "Fast", "Normal", "Thorough", "Very thorough" }; // TODO: Advanced with window popping up
		final JComboBox< String > box = new JComboBox< String > (options);
		box.setBorder( null );
		box.setSelectedIndex( 1 );
		final JLabel boxLabel = new JLabel("SIFT Matching ");
		panel.add( boxLabel, "aligny baseline" );
		panel.add( box, "growx, wrap" );

		final BoundedValuePanel maxErrorSlider = new BoundedValuePanel(new BoundedValue(0, Math.round( Math.ceil( medianDistance * 5 ) ), errorInit ));
		maxErrorSlider.setBorder(null);
		final JLabel maxErrorLabel = new JLabel("max. error (px)");
		panel.add(maxErrorLabel, "aligny baseline");
		panel.add(maxErrorSlider, "growx, wrap");

		final BoundedValuePanel inliersSlider = new BoundedValuePanel(new BoundedValue(0, 50, minNumInliersInit ));
		inliersSlider.setBorder(null);
		final JLabel inliersLabel = new JLabel("min inliers (total)");
		final Font font = inliersLabel.getFont().deriveFont( 10f );
		inliersLabel.setFont( font );
		inliersLabel.setBorder( null );
		panel.add(inliersLabel, "aligny baseline");
		panel.add(inliersSlider, "growx, wrap");

		final BoundedValuePanel inliersPerGeneSlider = new BoundedValuePanel(new BoundedValue(0, 25, minNumInliersGeneInit ));
		inliersPerGeneSlider.setBorder(null);
		final JLabel inliersPerGeneLabel = new JLabel("min inliers (gene)");
		inliersPerGeneLabel.setFont( font );
		inliersPerGeneLabel.setBorder( null );
		panel.add(inliersPerGeneLabel, "aligny baseline");
		panel.add(inliersPerGeneSlider, "growx, wrap");

		// Panel for RANSAC MODEL
		final JComboBox< String > boxModelRANSAC1 = new JComboBox< String > (optionsModel);
		boxModelRANSAC1.setSelectedIndex( 1 );
		final JLabel boxModeRANSACLabel1 = new JLabel("RANSAC model ");
		panel.add( boxModeRANSACLabel1, "aligny baseline, sy 2" );
		panel.add( boxModelRANSAC1, "growx, wrap" );
		final JPanel panRANSAC = new JPanel( new MigLayout("gap 0, ins 0 0 0 0, fill", "[right][grow]", "center") );
		final JComboBox< String > boxModelRANSAC2 = new JComboBox< String > (optionsModelReg);
		boxModelRANSAC2.setSelectedIndex( 0 );
		panRANSAC.add( boxModelRANSAC2 );
		final JLabel labelRANSACReg = new JLabel( "λ=" );
		panRANSAC.add( labelRANSACReg, "alignx right" );
		final JTextField tfRANSAC = new JTextField( "0.1" );
		panRANSAC.add( tfRANSAC, "growx" );
		panRANSAC.setBorder( BorderFactory.createEmptyBorder(0,0,5,0));
		panel.add( panRANSAC, "growx, wrap" );

		// Panel for FINAL MODEL
		final JComboBox< String > boxModelFinal1 = new JComboBox< String > (optionsModel);
		boxModelFinal1.setSelectedIndex( 1 );
		final JLabel boxModeFinalLabel1 = new JLabel("Final model ");
		panel.add( boxModeFinalLabel1, "aligny baseline, sy 2" );
		panel.add( boxModelFinal1, "growx, wrap" );
		final JPanel panFinal = new JPanel( new MigLayout("gap 0, ins 0 0 0 0, fill", "[right][grow]", "center") );
		final JComboBox< String > boxModelFinal2 = new JComboBox< String > (optionsModelReg);
		boxModelFinal2.setSelectedIndex( 0 );
		panFinal.add( boxModelFinal2 );
		final JLabel labelFinalReg = new JLabel( "λ=" );
		panFinal.add( labelFinalReg, "alignx right" );
		final JTextField tfFinal = new JTextField( "0.1" );
		panFinal.add( tfFinal, "growx" );
		panFinal.setBorder( BorderFactory.createEmptyBorder(0,0,5,0));
		panel.add( panFinal, "growx, wrap" );

		final JCheckBox overlayInliers = new JCheckBox( "Overlay SIFT features" );
		panel.add(overlayInliers, "span,growx,pushy");
		overlayInliers.setSelected( true );
		overlayInliers.setEnabled( false );
		overlayInliers.addChangeListener( e ->
		{
			if ( !overlayInliers.isSelected() )
			{
				bdvhandle.getViewerPanel().renderTransformListeners().remove( siftoverlay );
				bdvhandle.getViewerPanel().getDisplay().overlays().remove( siftoverlay );
			}
			else
			{
				bdvhandle.getViewerPanel().renderTransformListeners().add( siftoverlay );
				bdvhandle.getViewerPanel().getDisplay().overlays().add( siftoverlay );
			}
			bdvhandle.getViewerPanel().requestRepaint();
		});

		final JProgressBar bar = new JProgressBar(SwingConstants.HORIZONTAL, 0, 100);
		bar.setValue( 0 );
		bar.setStringPainted(false);
		panel.add(bar, "span,growx,pushy");

		final JButton add = new JButton("Add genes");
		panel.add(add, "aligny baseline");
		final JButton run = new JButton("Run SIFT");
		panel.add(run, "growx, wrap");

		//
		// Run SIFT alignment
		//
		run.addActionListener( l ->
		{
			siftoverlay.setInliers( new ArrayList<>() );
			bdvhandle.getViewerPanel().renderTransformListeners().remove( siftoverlay );
			bdvhandle.getViewerPanel().getDisplay().overlays().remove( siftoverlay );
			run.setEnabled( false );
			add.setEnabled( false );
			overlayInliers.setEnabled( false );
			bar.setValue( 1 );

			new Thread( () ->
			{
				final SynchronizedViewerState state = bdvhandle.getViewerPanel().state();
				AddedGene.updateRemainingSources( state, geneToBDVSource, sourceData );

				final int minInliers = (int)Math.round( inliersSlider.getValue().getValue() );
				final int minInliersPerGene = (int)Math.round( inliersPerGeneSlider.getValue().getValue() );
				final double maxError = maxErrorSlider.getValue().getValue();
				final double scale = overlay.currentScale();
				final double sigma = stimcard.currentSigma();
				final double lambda1 = Double.parseDouble( tfRANSAC.getText().trim() );
				final double lambda2 = Double.parseDouble( tfFinal.getText().trim() );
				final SIFTParam p = new SIFTParam( SIFTMatching.values()[ box.getSelectedIndex() ] );

				final Model model1 = getModelFor( boxModelRANSAC1.getSelectedIndex(), boxModelRANSAC2.getSelectedIndex(), lambda1 );
				final Model model2 = getModelFor( boxModelFinal1.getSelectedIndex(), boxModelFinal2.getSelectedIndex(), lambda2 );

				System.out.println( "Running SIFT align with the following parameters: ");
				System.out.println( "maxError: " + maxError + ", minInliers (over all genes): " + minInliers + ", minInliers (per genes): " + minInliersPerGene );
				System.out.println( "scale: " + scale + ", sigma: " + sigma );
				System.out.println( "SIFT: " + SIFTMatching.values()[ box.getSelectedIndex() ] );
				System.out.println( "RANSAC model: " + optionsModel[ boxModelRANSAC1.getSelectedIndex() ] + ", regularizer: " + optionsModelReg[ boxModelRANSAC2.getSelectedIndex() ] + ", lambda=" + lambda1 );
				System.out.println( "FINAL model: " + optionsModel[ boxModelFinal1.getSelectedIndex() ] + ", regularizer: " + optionsModelReg[ boxModelFinal2.getSelectedIndex() ] + ", lambda=" + lambda2 );

				System.out.println( model1.getClass().getSimpleName() );
				System.out.println( model2.getClass().getSimpleName() );

				final boolean visResult = false;
				final double[] progressBarValue = new double[] { 1.0 };
				
				final SiftMatch match = PairwiseSIFT.pairwiseSIFT(
						data1.data(), dataset1, data2.data(), dataset2,
						(Affine2D & Model)model1, (Affine2D & Model)model1,
						new ArrayList<>( geneToBDVSource.keySet() ),
						p, scale, sigma, maxError,
						minInliers, minInliersPerGene,
						visResult, service, (v) -> {
							synchronized ( this ) {
								progressBarValue[ 0 ] += v;
								bar.setValue( (int)Math.round( progressBarValue[ 0 ] ));
							}
						});

				System.out.println( match.getNumInliers() + "/" + match.getNumCandidates() );

				// TODO: print out cmd-line args

				// 
				// apply transformations
				//
				if ( match.getInliers().size() > 0 )
				{
					try
					{
						//final RigidModel2D model = new RigidModel2D();
						//final AffineModel2D model = new AffineModel2D();
						model2.fit( match.getInliers() );
						final AffineTransform2D m = AlignTools.modelToAffineTransform2D( (Affine2D)model2 ).inverse();
						final AffineTransform3D m3d = new AffineTransform3D();
						m3d.set(m.get(0, 0), 0, 0 ); // row, column
						m3d.set(m.get(0, 1), 0, 1 ); // row, column
						m3d.set(m.get(1, 0), 1, 0 ); // row, column
						m3d.set(m.get(1, 1), 1, 1 ); // row, column
						m3d.set(m.get(0, 2), 0, 3 ); // row, column
						m3d.set(m.get(1, 2), 1, 3 ); // row, column

						System.out.println( "final model" + m );
						//System.out.println( m3d );

						final List<TransformedSource<?>> tsources = BDVUtils.getTransformedSources(state);

						// every second source will be transformed
						for ( int i = 1; i < tsources.size(); i = i + 2 )
							tsources.get( i ).setFixedTransform( m3d );

					} catch (Exception e)
					{
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

					//
					// Overlay detections
					//
					siftoverlay.setInliers( match.getInliers() );

					// Very good to know!
					//for ( final PointMatch pm : match.getInliers() )
					//	System.out.println( ((PointST) pm.getP1()).getGene() );

					overlayInliers.setSelected( true );
					overlayInliers.setEnabled( true );
				}
				else
				{
					siftoverlay.setInliers( new ArrayList<>() );
					overlayInliers.setEnabled( false );
				}

				bar.setValue( 100 );
				add.setEnabled( true );
				run.setEnabled( true );
				bdvhandle.getViewerPanel().requestRepaint();
			}).start();

		});

		//
		// Add genes ...
		//
		add.addActionListener( l -> 
		{
			if ( gse == null || gse.frame().isVisible() == false )
				gse = new GeneSelectionExplorer(
					allGenes,
					list ->
					{
						//
						// first check if all groups are still present that are in the HashMap
						//
						final SynchronizedViewerState state = bdvhandle.getViewerPanel().state();
						AddedGene.updateRemainingSources( state, geneToBDVSource, sourceData );

						//
						// Now add the new ones (if it's not already there)
						//
						for ( final String gene : list )
						{
							if ( !geneToBDVSource.containsKey( gene ) )
							{
								System.out.println( "Gene " + gene + " will be added." );

								final AddedGene gene1 = AddedGene.addGene( stimcard.currentRendering(), bdvhandle, data1, gene, stimcard.currentSigma(), new ARGBType( ARGBType.rgba(0, 255, 0, 0) ), stimcard.currentBrightnessMin(), stimcard.currentBrightnessMax() );
								final AddedGene gene2 = AddedGene.addGene( stimcard.currentRendering(), bdvhandle, data2, gene, stimcard.currentSigma(), new ARGBType( ARGBType.rgba(255, 0, 255, 0) ), stimcard.currentBrightnessMin(), stimcard.currentBrightnessMax() );

								sourceData.put( gene, new ValuePair<>( gene1, gene2 ) );
								//stimcard.gaussFactories().add( gene1.factory );
								//stimcard.gaussFactories().add( gene2.factory );

								final SourceGroup handle = new SourceGroup();
								state.addGroup( handle );
								state.setGroupName( handle, gene );
								state.setGroupActive( handle, true );
								state.addSourceToGroup( state.getSources().get( state.getSources().size() - 2 ), handle );
								state.addSourceToGroup( state.getSources().get( state.getSources().size() - 1 ), handle );

								geneToBDVSource.put( gene, handle );

								bdvhandle.getViewerPanel().setDisplayMode( DisplayMode.GROUP );
							}
							else
							{
								System.out.println( "Gene " + gene + " is already being displayed, ignoring." );
								// TODO: remove gaussFactories? - maybe not necessary
							}
						}
					} );
		});


		/*
		final BoundedValuePanel sigmaSlider = new BoundedValuePanel(new BoundedValue(0, Math.max( 2.50, currentSigma * 1.5 ), currentSigma ));
		sigmaSlider.setBorder(null);
		final JLabel sigmaLabel = new JLabel("sigma (-sf)");
		panel.add(sigmaLabel, "aligny baseline");
		panel.add(sigmaSlider, "growx, wrap");
		//panel.add
		//final MinSigmaEditor minSigmaEditor = new MinSigmaEditor(minSigmaLabel, minSigmaSlider, editor.getModel());

		sigmaSlider.changeListeners().add( () -> {
			gaussFactory.setSigma( sigmaSlider.getValue().getValue() * medianDistance);
			bdvhandle.getViewerPanel().requestRepaint();
		} );

		final JPopupMenu menu = new JPopupMenu();
		menu.add(runnableItem("set bounds ...", sigmaSlider::setBoundsDialog));
		sigmaSlider.setPopup(() -> menu);*/
	}

	protected Model<?> getModelFor( final int modelIndex, final int regIndex, final double lambda )
	{
		if ( regIndex == 0 )
		{
			if ( modelIndex == 0 )
				return new TranslationModel2D();
			else if ( modelIndex == 1 )
				return new RigidModel2D();
			else if ( modelIndex == 2 )
				return new SimilarityModel2D();
			else if ( modelIndex == 3 )
				return new AffineModel2D();
			else
				throw new RuntimeException( "Unknown model index: "+ modelIndex );
		}
		else if ( regIndex == 1 )
		{
			if ( modelIndex == 0 )
				return new TranslationModel2D();
			else if ( modelIndex == 1 )
				return new InterpolatedAffineModel2D<TranslationModel2D, RigidModel2D>( new TranslationModel2D(), new RigidModel2D(), lambda );
			else if ( modelIndex == 2 )
				return new InterpolatedAffineModel2D<TranslationModel2D, SimilarityModel2D>( new TranslationModel2D(), new SimilarityModel2D(), lambda );
			else if ( modelIndex == 3 )
				return new InterpolatedAffineModel2D<TranslationModel2D, AffineModel2D>( new TranslationModel2D(), new AffineModel2D(), lambda );
			else
				throw new RuntimeException( "Unknown model index: "+ modelIndex );
		}
		else if ( regIndex == 2 )
		{
			if ( modelIndex == 0 )
				return new InterpolatedAffineModel2D<RigidModel2D, TranslationModel2D>( new RigidModel2D(), new TranslationModel2D(), lambda );
			else if ( modelIndex == 1 )
				return new RigidModel2D();
			else if ( modelIndex == 2 )
				return new InterpolatedAffineModel2D<RigidModel2D, SimilarityModel2D>( new RigidModel2D(), new SimilarityModel2D(), lambda );
			else if ( modelIndex == 3 )
				return new InterpolatedAffineModel2D<RigidModel2D, AffineModel2D>( new RigidModel2D(), new AffineModel2D(), lambda );
			else
				throw new RuntimeException( "Unknown model index: "+ modelIndex );
		}
		else if ( regIndex == 3 )
		{
			if ( modelIndex == 0 )
				return new InterpolatedAffineModel2D<SimilarityModel2D, TranslationModel2D>( new SimilarityModel2D(), new TranslationModel2D(), lambda );
			else if ( modelIndex == 1 )
				return new InterpolatedAffineModel2D<SimilarityModel2D, RigidModel2D>( new SimilarityModel2D(), new RigidModel2D(), lambda );
			else if ( modelIndex == 2 )
				return new SimilarityModel2D();
			else if ( modelIndex == 3 )
				return new InterpolatedAffineModel2D<SimilarityModel2D, AffineModel2D>( new SimilarityModel2D(), new AffineModel2D(), lambda );
			else
				throw new RuntimeException( "Unknown model index: "+ modelIndex );
		}
		else if ( regIndex == 4 )
		{
			if ( modelIndex == 0 )
				return new InterpolatedAffineModel2D<AffineModel2D, TranslationModel2D>( new AffineModel2D(), new TranslationModel2D(), lambda );
			else if ( modelIndex == 1 )
				return new InterpolatedAffineModel2D<AffineModel2D, RigidModel2D>( new AffineModel2D(), new RigidModel2D(), lambda );
			else if ( modelIndex == 2 )
				return new InterpolatedAffineModel2D<AffineModel2D, SimilarityModel2D>( new AffineModel2D(), new SimilarityModel2D(), lambda );
			else if ( modelIndex == 3 )
				return new AffineModel2D();
			else
				throw new RuntimeException( "Unknown model index: "+ modelIndex );
		}
		else
			throw new RuntimeException( "Unknown regularizer model index: "+ modelIndex );
	}

	public JPanel getPanel() { return panel; }
}
