package de.codesourcery.toyprofiler.ui;

import static java.lang.Integer.parseInt;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorScheme 
{
    private static final String KEY_COLOR_SCHEME = "colorscheme.";

    public static final ColorScheme DEFAULT = new ColorScheme("default",Color.RED.darker() , Color.ORANGE.brighter() , 4 , Color.GREEN , Color.RED );
    public static final ColorScheme DEFAULT_COMPARE = new ColorScheme("default (compare)",new Color( 0 , 0 , 120 ), new Color( 0, 0 , 230 ) , 4 , Color.GREEN , Color.RED );
    
    private String name;
    
    private Color[] gradient;
    private int colorCount;
    
    private Color start;
    private Color end;
    
    private Color goodDifferenceColor;
    private Color badDifferenceColor;
    
    public ColorScheme(ColorScheme other) 
    {
        populateFrom( other );
    }
    
    public void setName(String name) 
    {
        if ( name == null || name.trim().length() == 0 ) {
            throw new IllegalArgumentException("name must not be NULL/blank");
        }
        this.name = name;
    }
    
    public void populateFrom(ColorScheme other) {
        this.name = other.name;
        if ( other.gradient != null ) {
            this.gradient = new Color[ other.gradient.length ];
            System.arraycopy(other.gradient , 0 , this.gradient , 0 , other.gradient.length);
        } 
        this.colorCount = other.colorCount;
        this.start = other.start;
        this.end = other.end;
        this.goodDifferenceColor = other.goodDifferenceColor;
        this.badDifferenceColor = other.badDifferenceColor;
    }
    
    public ColorScheme(String name) 
    {
        setName(name);
    }

    @Override
    public boolean equals(Object obj) {
        if ( obj instanceof ColorScheme) {
            return this.name.equals( ((ColorScheme) obj).name );
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        return this.name.hashCode();
    }
    
    public ColorScheme(String name,Color src,Color dst,int colorCount,Color goodDifferenceColor,Color badDifferenceColor) {
        setName(name);
        this.start = src;
        this.end = dst;
        this.colorCount = colorCount;
        this.goodDifferenceColor = goodDifferenceColor;
        this.badDifferenceColor = badDifferenceColor;
        setup();
    }
    
    public Color getGoodDifferenceColor() {
        return goodDifferenceColor;
    }
    
    public Color getBadDifferenceColor() {
        return badDifferenceColor;
    }
    
    public void setGoodDifferenceColor(Color goodDifferenceColor) {
        this.goodDifferenceColor = goodDifferenceColor;
    }
    
    public void setBadDifferenceColor(Color badDifferenceColor) {
        this.badDifferenceColor = badDifferenceColor;
    }
    
    public String getName() {
        return name;
    }
    
    public void setStart(Color start) {
        this.start = start;
    }
    
    public Color getStart() {
        return start;
    }
    
    public void setEnd(Color end) {
        this.end = end;
    }
    
    public Color getEnd() {
        return end;
    }
    
    public void setup() 
    {
        this.gradient = createGradient( start , end , colorCount ); 
    }
    
    public Color color(int index) {
        return gradient[index];
    }
    
    public static Color[] createGradient(final Color src ,final Color dst,int colorCount) 
    {
        final Color[] gradient = new Color[colorCount];

        float dr = (dst.getRed()/255f - src.getRed()/255f)/colorCount;
        float dg = (dst.getGreen()/255f - src.getGreen()/255f)/colorCount;
        float db = (dst.getBlue()/255f - src.getBlue()/255f)/colorCount;

        float r = src.getRed()/255f;
        float g = src.getGreen()/255f;
        float b = src.getBlue()/255f;

        for ( int i = 0 ; i < colorCount ; i++)
        {
            gradient[i] = new Color( r , g , b );
            r += dr;
            g += dg;
            b += db;
        }
        return gradient;
    }    
    
    public static void save(Collection<ColorScheme> schemes, Map<String,String> map) {
        
        map.entrySet().removeIf( entry -> entry.getKey().startsWith( KEY_COLOR_SCHEME ) );
        for ( ColorScheme scheme : schemes ) 
        {
            final String key = KEY_COLOR_SCHEME+scheme.name;
            map.put( key+".start" , toString( scheme.start ) );
            map.put( key+".end" , toString( scheme.end) );
            map.put( key+".gooddiff" , toString( scheme.goodDifferenceColor ) );
            map.put( key+".baddiff" , toString( scheme.badDifferenceColor ) );
            map.put( key+".colorcount" , Integer.toString( scheme.colorCount ) );
        }
    }
    
    public static Map<String,ColorScheme> load(Map<String,String> map) {
        
        final Map<String,Map<String,String>> tmpMap = new HashMap<>();
        final Pattern pattern = Pattern.compile("^"+Pattern.quote( KEY_COLOR_SCHEME )+"(.*?)(\\..*){0,1}$");
        map.entrySet().stream().filter( entry -> entry.getKey().startsWith( KEY_COLOR_SCHEME ) ).forEach( entry -> 
        {
            final Matcher m = pattern.matcher( entry.getKey() );
            if ( ! m.matches() ) {
                throw new RuntimeException("Failed to match '"+entry.getKey()+"'");
            }
            final String name = m.group(1);
            Map<String,String> tmp = tmpMap.get( name );
            if ( tmp == null ) 
            {
                tmp = new HashMap<>();
                tmpMap.put( name , tmp );
            }
            tmp.put( entry.getKey() , entry.getValue() );
        });
        
        final Map<String,ColorScheme>  result = new HashMap<>();
        for ( final String name : tmpMap.keySet() ) 
        {
            final Map<String, String> keys = tmpMap.get(name);
            final Function<String,String> func = property -> 
            {
                final String key = KEY_COLOR_SCHEME+name+"."+property;
                if ( ! keys.containsKey(key) ) {
                    throw new RuntimeException("Missing key '"+key+"'");
                }
                return keys.get( key );
            };
            final ColorScheme scheme = new ColorScheme( name );

            scheme.setStart( fromString( func.apply( "start" ) ) );
            scheme.setEnd( fromString( func.apply( "end" ) ) );
            
            scheme.setGoodDifferenceColor( fromString( func.apply( "gooddiff" ) ) );
            scheme.setBadDifferenceColor( fromString( func.apply( "baddiff" ) ) );
            
            scheme.setColorCount( parseInt( func.apply( "colorcount") ) );
            scheme.setup();
            result.put( scheme.getName() , scheme );
        }
        return result;
    }
    
    public void setColorCount(int count) {
        this.colorCount = count;
    }
    
    public int getColorCount() {
        return colorCount;
    }

    private static String toString(Color color) {
        return color.getRed()+","+color.getGreen()+","+color.getBlue();
    }
    
    private static Color fromString(String s) {
        final String[] parts = s.split(",");
        return new Color( parseInt( parts[0] ) , parseInt( parts[1] ) ,parseInt( parts[2] ) );
    }
    @Override
    public String toString() {
        return "ColorScheme [name=" + name + ", gradient="
                + Arrays.toString(gradient) + ", colorCount=" + colorCount
                + ", start=" + start + ", end=" + end + ", goodDifferenceColor="
                + goodDifferenceColor + ", badDifferenceColor="
                + badDifferenceColor + "]";
    }
    public ColorScheme createCopy() {
        return new ColorScheme(this);
    }
}
