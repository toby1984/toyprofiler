package de.codesourcery.toyprofiler.ui;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FlameGraphRenderer<T>
{
    private int maxDepth;

    private BufferedImage image;
    private Graphics2D graphics;

    private int height;
    private int heightPerRow;

    private ColorScheme colorScheme;

    private boolean doRender;

    private final IDataProvider<T> dataProvider;

    private List<RectangularRegion<T>> regions = new ArrayList<>(1000);

    public interface IVisitor<T>
    {
        public void visit(T item,int depth);
    }
    public interface IDataProvider<T>
    {
        public T getRoot();

        public double getPercentageValue(T node);

        public boolean isShowDifferences();

        public double getPreviousPercentageValue(T node);

        public List<T> getChildren(T node);

        public void visitSubtree(T startNode,IVisitor<T> visitor);

        public String getLabel(T node,Graphics2D graphics,int maxWidth);
    }

    public void setColorScheme(ColorScheme scheme) {
        this.colorScheme = scheme;
    }

    public static final class RectangularRegion<T> extends Rectangle
    {
        public final T stats;

        public RectangularRegion(Rectangle r,T stats) {
            super(r);
            this.stats = stats;
        }

        public boolean matches(RectangularRegion<T> other)
        {
            return other != null && this.equals( other ) && this.stats == other.stats;
        }
    }

    public static final class FlameGraph<T>
    {
        private final BufferedImage image;
        private final List<RectangularRegion<T>> regions;

        public FlameGraph(BufferedImage image,List<RectangularRegion<T>> rects) {
            this.image = image;
            this.regions = new ArrayList<>(rects);
        }

        public BufferedImage getImage() {
            return image;
        }

        public RectangularRegion<T> getFirst()
        {
            return regions.isEmpty() ? null : regions.get(0);
        }

        public RectangularRegion<T> getRegion(int x,int y)
        {
            for (int i = 0,len = regions.size() ; i < len ; i++)
            {
                final RectangularRegion<T> r = regions.get(i);
                if ( r.contains(x,y) ) {
                    return r;
                }
            }
            return null;
        }

        public RectangularRegion<T> find(T stats) 
        {
            for (int i = 0,len = regions.size() ; i < len ; i++)
            {
                final RectangularRegion<T> r = regions.get(i);
                if ( r.stats == stats ) {
                    return r; 
                }
            }
            return null;
        }
    }

    public FlameGraphRenderer(IDataProvider<T> provider,ColorScheme colorScheme)
    {
        this.dataProvider = provider;
        this.colorScheme = colorScheme;
    }

    private int calcMaxDepth(T root)
    {
        final int[] md = {0};
        if ( root != null )
        {
            dataProvider.visitSubtree(root , (node,depth) -> {
                if ( depth > md[0] ) {
                    md[0]=depth;
                }
            });
        }
        return md[0];
    }

    private void reset(T root,int width,int height)
    {
        this.regions.clear();
        this.doRender = true;
        this.maxDepth = calcMaxDepth( root );
        this.height = height;
        this.regions = new ArrayList<>(1000);
        this.image = new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB );
        if ( this.graphics != null )
        {
            this.graphics.dispose();
        }
        this.graphics = image.createGraphics();
    }

    public FlameGraph<T> render(int width,int height)
    {
        return render( dataProvider.getRoot() , width , height );
    }

    public FlameGraph<T> render(T root,int width,int height)
    {
        // first rendering pass
        reset(root,width,height);
        doRender = false; // do not render, just calculate layout
        onePass(root,width,height);

        // determine visible height by discarding all
        // regions that are too tiny to see at the
        // current zoom level
        final Map<Integer,List<RectangularRegion<T>>> childAtDepth = new HashMap<>();

        dataProvider.visitSubtree(root ,  (child,depth ) -> {

            final RectangularRegion<T> region = regions.stream().filter( r -> r.stats == child ).findFirst().orElse( null );
            if ( region != null && isVisible(region) )
            {
                List<RectangularRegion<T>> list = childAtDepth.get( depth );
                if ( list == null ) {
                    list = new ArrayList<>();
                    childAtDepth.put(depth,list);
                }
                list.add( region );
            }
        });

        final int visibleDepth = childAtDepth.keySet().stream().mapToInt( k -> k.intValue() ).max().orElse(0);

        // second rendering pass
        reset(root,width,height);
        maxDepth = visibleDepth;
        onePass(root,width,height);

        final FlameGraph<T> result = new FlameGraph<T>(image,regions);

        // release memory just in case someone rendered a super-big graph
        cleanup();
        return result;
    }

    private void cleanup() 
    {
        this.regions.clear();
        this.image = null;
        this.graphics.dispose();
        this.graphics = null;
    }

    private boolean isVisible(RectangularRegion<T> r)
    {
        return r.width > 0;
    }

    private void onePass(T root,int width,int height)
    {
        if ( doRender ) {
            graphics.setColor( Color.WHITE );
            graphics.fillRect( 0 , 0 ,width , height );
        }

        if ( root != null )
        {
            heightPerRow = height / (maxDepth+1);

            final Rectangle currentBox = new Rectangle(0,height-heightPerRow,width,heightPerRow);
            processPath( 0 , 1, root , currentBox , 1 );
        }
    }

    private void processPath(int currentColor,int colorIndexIncrement,T parent,Rectangle rect,int currentDepth)
    {
        regions.add( new RectangularRegion<T>(rect, parent) );

        if ( doRender ) 
        {
            render( currentColor , parent , rect );
        }

        currentColor += colorIndexIncrement;
        if ( currentColor == colorScheme.getColorCount() ) 
        {
            currentColor -= 2;
            colorIndexIncrement = -1;
        }
        else if ( currentColor == -1 ) 
        {
            currentColor += 2;
            colorIndexIncrement = 1;
        }

        final List<T> sorted = dataProvider.getChildren( parent ) ; 

        int x = rect.x;
        final int y = height-(currentDepth+1)*heightPerRow;

        for ( T child : sorted )
        {
            final double percentageThisNode= dataProvider.getPercentageValue( child);
            final int w = (int) ( rect.width * percentageThisNode);

            final Rectangle currentBox = new Rectangle( x  , y , w ,heightPerRow);
            processPath( currentColor , colorIndexIncrement , child , currentBox , currentDepth+1 );

            currentColor += colorIndexIncrement;
            if ( currentColor == colorScheme.getColorCount() ) 
            {
                currentColor -= 2;
                colorIndexIncrement = -1;
            }
            else if ( currentColor == -1 ) 
            {
                currentColor += 2;
                colorIndexIncrement = 1;
            }

            x += w;
        }
    }

    private void render(int currentColorIndex , T node,Rectangle r) {

        graphics.setColor( colorScheme.color( currentColorIndex ) );

        graphics.fillRect( r.x , r.y , r.width , r.height );

        if ( dataProvider.isShowDifferences() ) 
        {
            final double delta = dataProvider.getPercentageValue( node ) - dataProvider.getPreviousPercentageValue( node );
            final Color color = delta > 0 ? colorScheme.getBadDifferenceColor() : colorScheme.getGoodDifferenceColor();
            final int w = (int) (r.width * Math.abs(delta));
            graphics.setColor( color );
            graphics.fillRect( r.x+r.width-w , r.y , w , r.height );
        }

        graphics.setColor( Color.WHITE );

        final String label = dataProvider.getLabel( node , graphics , r.width );
        drawCentered( label , r );
    }

    private void drawCentered(String text,Rectangle rectangle)
    {
        drawCentered(text,rectangle,graphics);
    }

    public static void drawCentered(String text,Rectangle rectangle,Graphics2D graphics)
    {
        final FontMetrics fm = graphics.getFontMetrics();
        final int x = rectangle.x + (rectangle.width - fm.stringWidth(text)) / 2;
        final int y = rectangle.y + fm.getAscent() + ( rectangle.height - fm.getHeight() )/ 2;

        final Shape oldClip = graphics.getClip();
        graphics.setClip( rectangle );
        graphics.drawString(text, x,y);
        graphics.setClip( oldClip );
    }
}