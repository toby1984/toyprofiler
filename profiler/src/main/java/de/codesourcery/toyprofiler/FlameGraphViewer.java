package de.codesourcery.toyprofiler;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.HeadlessException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;

import de.codesourcery.toyprofiler.FlameGraphRenderer.FlameGraph;
import de.codesourcery.toyprofiler.FlameGraphRenderer.IDataProvider;
import de.codesourcery.toyprofiler.FlameGraphRenderer.IVisitor;
import de.codesourcery.toyprofiler.FlameGraphRenderer.RectangularRegion;
import de.codesourcery.toyprofiler.Profile.MethodStats;

public class FlameGraphViewer extends JFrame
{
	private final JComboBox<Profile> selectedProfile = new JComboBox<>();

	private final String title;

	private MethodDataProvider dataProvider;

	protected final class MethodDataProvider implements IDataProvider<MethodStats> {

		private final Profile profile;

		public MethodDataProvider(Profile p) {
			this.profile = p;
		}

		@Override
		public MethodStats getRoot() {
			return profile.getTopLevelMethod();
		}

		@Override
		public double getValue(MethodStats node) {
			return node.getTotalTimeMillis();
		}

		@Override
		public List<MethodStats> getChildren(MethodStats node) {
			return new ArrayList<>( node.callees.values() );
		}

		@Override
		public void visitSubtree(MethodStats startNode, IVisitor<MethodStats> visitor)
		{
			startNode.visit( (node,depth) -> visitor.visit( node , depth ) );
		}

		@Override
		public String getLabel(MethodStats node, Graphics2D graphics, int maxWidth)
		{
			final String clazz = node.getSimpleClassName();
			final String method = node.getMethodName().replaceAll("[\\(\\)]","");

			String className = clazz;
			String text = method;
			if ( getTextWidth( text , graphics ) < maxWidth )
			{
				for ( int i = 0 ; i <= clazz.length() ; i++ )
				{
					className = clazz.substring(0,i);
					String newText = (i!= clazz.length() ) ? className+"..."+method : className+"."+method;
					if ( getTextWidth(newText,graphics) > maxWidth ) {
						break;
					}
					text = newText;
				}
			}
			return text;
		}

		private int getTextWidth(String text,Graphics2D g)
		{
			final FontMetrics fm = g.getFontMetrics();
			return fm.stringWidth(text);
		}

	}

	protected final class MyPanel extends JPanel
	{
		private FlameGraphRenderer<MethodStats> builder;

		private int w = -1;
		private int h = -1;
		private FlameGraph<MethodStats> graph;

		private RectangularRegion<MethodStats> highlight;
		private MethodStats zoom;

		private final MouseAdapter mouseListener = new MouseAdapter()
		{
			@Override
			public void mouseMoved(java.awt.event.MouseEvent e)
			{
				if ( graph != null )
				{
					final RectangularRegion<MethodStats> region = graph.getRegion( e.getX() ,  e.getY() );
					setToolTipText( region == null ? null : getToolTip( region.stats ) );
					if ( highlight == null || ! highlight.matches( region ) ) {
						highlight = region;
						repaint();
					}
				}
			}

			private String getToolTip( MethodStats stats)
			{
				final StringBuilder buffer = new StringBuilder("<HTML><BODY>");
				final Map<String,String> data = new LinkedHashMap<>();
				data.put("Class", stats.getClassName() );
				data.put("Method", stats.getMethodName() );
				data.put("Signature", stats.getMethodSignature());
				data.put("Invocations" , Long.toString( stats.getInvocationCount() ) );
				data.put("Total time" , Float.toString( stats.getTotalTimeMillis() ) );

				final int col0MaxLen = data.keySet().stream().mapToInt( s -> s.length() ).max().orElse(0);
				for (final Iterator<String> it = new ArrayList<>( data.keySet() ).iterator(); it.hasNext();)
				{
					final String origKey = it.next();
					String key = origKey;
					while ( key.length() < col0MaxLen ) {
						key += " ";
					}
					buffer.append("<B>"+key+":</B> "+data.get(origKey) );
					if ( it.hasNext() ) {
						buffer.append("<BR/>");
					}
				}

				buffer.append("</BODY></HTML>");
				return buffer.toString();
			}

			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				boolean zoomChanged = false;
				if ( e.getButton() == MouseEvent.BUTTON3 && zoom != null ) { // zoom out
					final RectangularRegion<MethodStats> first = graph.getFirst();
					zoom = first == null ? null : first.stats.parent;
					zoomChanged=true;
				}
				else if ( e.getButton() == MouseEvent.BUTTON1 && highlight != null ) // zoom in
				{
					zoom = highlight.stats;
					zoomChanged=true;

				}

				if ( zoomChanged )
				{
					highlight = null;
					forcedRepaint();
				}
			}
		};

		public void setProfile(Profile toRender)
		{
			dataProvider = new MethodDataProvider( toRender );
			builder = new FlameGraphRenderer<MethodStats>( dataProvider );
			forcedRepaint();
		}

		public MyPanel(Profile toRender)
		{
			dataProvider = new MethodDataProvider( toRender );
			builder = new FlameGraphRenderer<MethodStats>( dataProvider );

			addMouseListener( mouseListener );
			addMouseMotionListener( mouseListener);
		}

		public void forcedRepaint() {
			graph = null;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			if ( graph == null || getWidth() != w || getHeight() != h )
			{
				w = getWidth();
				h = getHeight();
				if ( zoom != null ) {
					graph = builder.render( zoom , w  , h );
				} else {
					graph = builder.render( w  , h );
				}
			}
			g.drawImage( graph.getImage() , 0 , 0 , null );

			if ( highlight != null )
			{
				g.setColor( Color.BLUE );
				((Graphics2D) g).draw( highlight );
			}
		}
	}

	public static void main(String[] args) throws FileNotFoundException, IOException, HeadlessException, InvocationTargetException, InterruptedException
	{
		if ( args.length != 1 ) {
			throw new RuntimeException("Expected exactly one argument (XML file to load)");
		}
		final List<Profile> profiles = Profile.load( new FileInputStream( args[0] ) );
		if ( profiles.isEmpty() ) {
			throw new RuntimeException("File "+args[0]+" contains no profiles at all");
		}
		new FlameGraphViewer("Flame graph: "+args[0] , profiles );
	}

	private void updateTitle(Profile toRender)
	{
		setTitle( title+" [ "+toRender.getThreadName()+" ]" );
	}

	public FlameGraphViewer(String title,List<Profile> profiles) throws FileNotFoundException, IOException
	{
		super( title );
		this.title = title;

		selectedProfile.setModel( new DefaultComboBoxModel<>( profiles.toArray( new Profile[ profiles.size() ] ) ) );
		selectedProfile.setRenderer( new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,boolean cellHasFocus)
			{
				final Component result = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if ( value instanceof Profile )
				{
					final Profile p = (Profile) value;
					updateTitle(p);
					setText( p.getThreadName() );
				}
				return result;
			}
		});
		final Profile toRender = profiles.get(0);
		updateTitle(toRender);

		final MyPanel graphPanel = new MyPanel( toRender );

		selectedProfile.setSelectedItem( toRender );

		selectedProfile.addActionListener( ev ->
		{
			graphPanel.setProfile( (Profile) selectedProfile.getSelectedItem() );
		});
		System.out.println( toRender.toString() );

		setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );

		final JPanel compound = new JPanel();
		compound.setLayout( new GridBagLayout() );
		GridBagConstraints cnstrs = new GridBagConstraints();
		cnstrs.weightx=0;
		cnstrs.weighty=0;
		cnstrs.gridx=0;
		cnstrs.gridy=0;
		cnstrs.fill = GridBagConstraints.NONE;
		compound.add( selectedProfile , cnstrs );

		cnstrs = new GridBagConstraints();
		cnstrs.weightx=1;
		cnstrs.weighty=1;
		cnstrs.gridx=0;
		cnstrs.gridy=1;
		cnstrs.fill = GridBagConstraints.BOTH;
		compound.add( graphPanel , cnstrs );

		getContentPane().add( compound );
		setPreferredSize( new Dimension(640,480 ) );
		pack();
		setVisible( true );
	}
}
