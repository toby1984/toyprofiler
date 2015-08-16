package de.codesourcery.toyprofiler.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import de.codesourcery.toyprofiler.MethodStatsHelper;
import de.codesourcery.toyprofiler.Profile.MethodStats;
import de.codesourcery.toyprofiler.ui.FlameGraphRenderer.FlameGraph;
import de.codesourcery.toyprofiler.ui.FlameGraphRenderer.RectangularRegion;
import de.codesourcery.toyprofiler.ui.FlameGraphViewer.MethodDataProvider;
import de.codesourcery.toyprofiler.ui.ViewingHistory.IViewChangeListener;

public final class FlameGraphPanel extends JPanel implements IViewChangeListener
{
    public interface IMethodStatSelectionListener 
    {
        public void selectionChanged(MethodStats stats,MethodStatsHelper helper);
    }
    
    private final List<IMethodStatSelectionListener> listeners = new ArrayList<>();
    
    private FlameGraphRenderer<MethodStats> renderer;

    private int w = -1;
    private int h = -1;
    
    private MethodStatsHelper resolver;
    private MethodDataProvider dataProvider;
    
    private FlameGraph<MethodStats> graph;

    private RectangularRegion<MethodStats> highlight;
    
    private RectangularRegion<MethodStats> currentSelection;
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
                if ( ( highlight == null || ! highlight.matches( region ) ) ) 
                {
                    highlight = region;
                    if ( currentSelection == null ) {
                        selectionChanged( highlight == null ? null : highlight.stats );
                    }
                    repaint();
                }
            }
        }

        private String getToolTip( MethodStats stats)
        {
            final StringBuilder buffer = new StringBuilder("<HTML><BODY>");
            final Map<String,String> data = new LinkedHashMap<>();
            data.put("Class", resolver.getRawClassName(stats) );
            data.put("Method", resolver.getMethodName(stats) );
            data.put("Signature", resolver.getRawMethodSignature(stats));
            data.put("Invocations" , FlameGraphViewer.INVOCATION_COUNT_FORMAT.format( stats.getInvocationCount() ) );
            data.put("Total time" , FlameGraphViewer.millisToString( stats.getTotalTimeMillis() ) +" ("+FlameGraphViewer.PERCENTAGE_FORMAT.format( 100*stats.getPercentageOfParentTime() )+" % of parent)" );
            data.put("Own time" , FlameGraphViewer.millisToString( stats.getOwnTimeMillis() ) );

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
                zoom = first == null ? null : first.stats.getParent();
                zoomChanged=true;
            }
            else if ( e.getButton() == MouseEvent.BUTTON1 ) // zoom in
            {
                final RectangularRegion<MethodStats> region = graph.getRegion( e.getX() ,  e.getY() );
                if ( e.getClickCount() == 2 ) 
                {
                    if ( region != null ) {
                        zoom = region.stats;
                        zoomChanged=true;
                    }
                } 
                else 
                {
                    if ( region != null && ( currentSelection == null || ! currentSelection.matches( region ) ) ) 
                    {
                        currentSelection = region;
                        selectionChanged( currentSelection == null ? null : currentSelection.stats );
                        repaint();
                    } else {
                        currentSelection = null;
                        selectionChanged( null );
                        repaint();
                    }
                }
            }

            if ( zoomChanged )
            {
                currentSelection = null;
                forcedRepaint();
            }
        }
    };

    private Preferences preferences;
    
    public void addListener( IMethodStatSelectionListener l ) {
        this.listeners.add(l);
    }
    
    private void selectionChanged(MethodStats newSelection) 
    {
        listeners.forEach( l -> l.selectionChanged(newSelection,resolver) );
    }
    
    public void viewChanged(java.util.Optional<ProfileData> data,boolean triggeredFromComboBox) 
    {
        if ( data.isPresent() ) 
        {
            resolver = new MethodStatsHelper( data.get() );
            dataProvider = new MethodDataProvider( data.get().getSelectedProfile().get() , resolver );
            
        } else {
            dataProvider = null;
            resolver = MethodStatsHelper.NOP_INSTANCE;
        }
        renderer = new FlameGraphRenderer<MethodStats>( dataProvider , preferences.getDefaultColorScheme() );
        forcedRepaint();
    }

    public FlameGraphPanel(Preferences preferences)
    {
        this.preferences = preferences;
        preferences.addListener( prefs -> 
        {
            renderer.setColorScheme( prefs.getDefaultColorScheme() );
            forcedRepaint();
        });
        
        addMouseListener( mouseListener );
        addMouseMotionListener( mouseListener);
    }

    private void forcedRepaint() {
        graph = null;
        repaint();
    }

    public BufferedImage renderCustomImage(int w,int h) 
    {
        return renderGraph( w , h ).getImage();
    }

    public FlameGraph<MethodStats> renderGraph(int w,int h) 
    {
        if ( zoom != null ) {
            return renderer.render( zoom , w  , h );
        } 
        return renderer.render( w  , h );
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        if ( dataProvider == null || renderer == null ) 
        {
            setBackground(Color.WHITE);
            super.paintComponent(g);
            Font oldFont = g.getFont();
            final Font newFont = oldFont.deriveFont( Font.BOLD ,  32f );
            g.setFont( newFont );
            FlameGraphRenderer.drawCentered("< no data loaded >", new Rectangle(0,0,getWidth(),getHeight() ), (Graphics2D) g);
            g.setFont( oldFont );
            return;
        }
        
        if ( graph == null || getWidth() != w || getHeight() != h )
        {
            w = getWidth();
            h = getHeight();

            graph = renderGraph(w,h);
            if ( currentSelection != null ) {
                currentSelection = graph.find( currentSelection.stats );
                selectionChanged( currentSelection == null ? null : currentSelection.stats );
            }
            if ( highlight != null ) {
                highlight = graph.find( highlight.stats );
            }
        }
        g.drawImage( graph.getImage() , 0 , 0 , null );

        final RectangularRegion<MethodStats> toHighlight = currentSelection != null ? currentSelection : highlight;
        if ( toHighlight != null )
        {
            g.setColor( Color.BLUE );
            ((Graphics2D) g).draw( toHighlight );
        }
    }
}