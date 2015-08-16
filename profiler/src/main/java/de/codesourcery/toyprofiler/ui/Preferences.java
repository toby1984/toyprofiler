package de.codesourcery.toyprofiler.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import de.codesourcery.toyprofiler.ProfileContainer;
import de.codesourcery.toyprofiler.util.IProfileIOAdapter;

public class Preferences 
{
    private static final String FILE_NAME = ".toyprofiler";
    
    private static final String KEY_DEFAULT_COLORSCHENME_NAME = "default.colorscheme";
    private static final String KEY_DEFAULT_COMPARE_COLORSCHENME_NAME = "default.compare.colorscheme";
    
    private static final String KEY_HISTORY_ENTRY = "history";
    private static final String KEY_HISTORY_ENTRY_CURRENT_THREAD = "history.currentthread";
    
    private static final String KEY_LAST_LOAD_DIRECTORY = "last.load.directory";

    private final Map<String,String> properties = new HashMap<>();
    
    private final List<IPrefChangeListener> listeners = new ArrayList<>();

    public interface IPrefChangeListener {
        
        public void preferencesChanged(Preferences newPreferences);
    }
    
    public Preferences() 
    {
        getDefaultColorScheme();
        getDefaultCompareColorScheme();
    }
    
    public void addListener(IPrefChangeListener l) {
        listeners.add(l);
    }
    
    public void removeListener(IPrefChangeListener l) {
        listeners.remove(l);
    }
    
    public void notifyChange() 
    {
        listeners.forEach( l -> l.preferencesChanged( this ) );
    }
    
    public Preferences(Preferences other) 
    {
        populateFrom(other);
    }
    
    public void populateFrom(Preferences other) {
        this.properties.putAll( other.properties );
    }
    
    public Preferences createCopy() {
        return new Preferences(this);
    }
    
    private void setProperties(Properties properties) 
    {
        this.properties.clear();
        for ( Object key : properties.keySet() ) 
        {
            String value = properties.getProperty( (String) key );
            this.properties.put( (String) key, value );
        }
    }
    
    private File getConfigFile() 
    {
        final String homeDir = System.getProperty( "user.home" );
        if ( homeDir != null ) {
            return new File(homeDir,FILE_NAME);
        }
        return null;
    }

    public void load() throws IOException 
    {
        final File file = getConfigFile();
        if ( file != null && file.exists() && file.isFile() && file.canRead() ) {
            System.out.println("Loading preferences from "+file.getAbsolutePath()+"...");
            load( new FileInputStream( file ) );
        } 
        else 
        {
            InputStream in = getClass().getResourceAsStream("/preferences.properties");
            if ( in != null ) {
                System.out.println("Loading default preferences from classpath");
                load( in ); 
            }
        }
    }
    
    public void save() throws IOException 
    {
        final File file = getConfigFile();
        if ( file != null && (!file.exists() || ( file.exists() && file.isFile() && file.canWrite() ) ) ) 
        {
            System.out.println("Saving preferences to "+file.getAbsolutePath()+"...");
            save( new FileOutputStream( file ) );
        }
    }    
    
    public void setLastLoadDirectory(File file) {
        
        if ( ! file.isDirectory() ) {
            file = file.getParentFile();
        }
        this.properties.put( KEY_LAST_LOAD_DIRECTORY , file.getAbsolutePath() );
    }
    
    public Optional<File> getLastLoadDirectory() 
    {
        final String value = properties.get( KEY_LAST_LOAD_DIRECTORY );
        if ( value != null ) 
        {
            final File file = new File( value );
            if ( file.exists() && file.isDirectory() ) {
                return Optional.of(file);
            }
        }
        return Optional.empty();
    }
    
    public void load(InputStream in) throws IOException 
    {
        boolean success = false;
        try 
        {
            final Properties props = new Properties();
            props.load( in );
            
            setProperties(props);
            
            getDefaultColorScheme();
            getDefaultCompareColorScheme();
            
            success = true;
        } finally {
            try {
                in.close();
            } 
            catch (IOException e) 
            {
                if ( success ) {
                    throw e; 
                }
            }
        }
    }

    public void save(OutputStream out) throws IOException 
    {
        boolean success = false;
        try {
            final Properties props = new Properties();
            properties.forEach( (key,value) -> props.setProperty(key, value) );
            props.store(out, "AUTOMATICALLY GENERATED,WILL BE OVERWRITTEN");
            success = true;
        } 
        finally 
        {
            try {
                out.close();
            } 
            catch (IOException e) 
            {
                if ( success ) {
                    throw e; 
                }
            }
        }
    }

    public void setHistory(ViewingHistory history) 
    {
        properties.entrySet().removeIf( entry -> entry.getKey().startsWith( KEY_HISTORY_ENTRY ) );
        final List<ProfileData> items = history.getItems().stream().filter( p -> p.hasFile() ).collect( Collectors.toList() );
        for ( int i = 0 , len = items.size() ; i < len ; i++ ) 
        {
            properties.put( KEY_HISTORY_ENTRY+"."+i , items.get(i).getSourceFile().get().getAbsolutePath() );
            final String currentThreadKey = KEY_HISTORY_ENTRY_CURRENT_THREAD+"."+i;
            items.get(i).getSelectedThreadName().ifPresent( threadName -> properties.put( currentThreadKey  , threadName ) );
        }
    }

    public ViewingHistory getHistory(IProfileIOAdapter adapter) throws FileNotFoundException, IOException 
    {
        final ViewingHistory result = new ViewingHistory();
        final Pattern pattern = Pattern.compile( "^"+Pattern.quote( KEY_HISTORY_ENTRY )+"\\.([0-9]+)$" );
        final TreeMap<Integer,String> map = new TreeMap<>();
        properties.entrySet().stream().filter( e -> e.getKey().startsWith( KEY_HISTORY_ENTRY ) ).forEach( entry -> 
        {
            final String key = entry.getKey();
            final Matcher m = pattern.matcher( key );
            if ( m.matches() ) {
                map.put( Integer.parseInt( m.group(1) ) , entry.getValue() );
            }
        });

        if ( map.isEmpty() ) 
        {
            return result;
        }
        for ( Entry<Integer, String> entry : map.entrySet() )
        {
            final Integer id = entry.getKey();
            final File file = new File( entry.getValue() );
            if ( file.exists() && file.isFile() && file.canRead() ) 
            {
                try ( FileInputStream profileIn = new FileInputStream(file) ) 
                {
                    final ProfileContainer container = adapter.load( profileIn );
                    result.add( file , container);
                    final String currentThreadKey = KEY_HISTORY_ENTRY_CURRENT_THREAD+"."+id;
                    final String selectedThread = properties.get( currentThreadKey );
                    if ( selectedThread != null ) {
                        container.getProfiles().stream().filter( p -> selectedThread.equals( p.getThreadName() ) ).findFirst().ifPresent( profile -> 
                        {
                            result.setCurrentProfile( profile , false );
                        });
                    }
                }
            }
        }
        return result;
    }
    
    public List<ColorScheme> getColorSchemes() 
    {
        return new ArrayList<>( ColorScheme.load( this.properties ).values() );
    }
    
    public void setColorSchemes(Collection<ColorScheme> schemes) 
    {
        ColorScheme.save( schemes  , this.properties );
    }
    
    public void setDefaultColorScheme(ColorScheme scheme) {
        this.properties.put( KEY_DEFAULT_COLORSCHENME_NAME , scheme.getName() );
    }
    
    public void setDefaultCompareColorScheme(ColorScheme scheme) {
        this.properties.put( KEY_DEFAULT_COMPARE_COLORSCHENME_NAME , scheme.getName() );
    }    
    
    public ColorScheme getDefaultColorScheme() 
    {
        return getColorScheme( getDefaultColorSchemeName() ).get();
    }
    
    public ColorScheme getDefaultCompareColorScheme() 
    {
        return getColorScheme( getDefaultCompareColorSchemeName() ).get();
    }
 
    private String getDefaultColorSchemeName() 
    {
        return getOrUpdate( KEY_DEFAULT_COLORSCHENME_NAME , ColorScheme.getDefault().getName() );
    }
    
    private String getDefaultCompareColorSchemeName() 
    {
        return getOrUpdate( KEY_DEFAULT_COMPARE_COLORSCHENME_NAME , ColorScheme.getDefaultCompare().getName() );
    }
    
    private String getOrUpdate(String key,String defaultValue) 
    {
        final String value = properties.get( key );
        if ( value == null ) {
            System.out.println("Adding missing config key: '"+key+"' = "+defaultValue);
            properties.put( key , defaultValue );
            return defaultValue;
        }
        return value;
    }
    
    public Optional<ColorScheme> getColorScheme(String name) {
        
        final List<ColorScheme> schemes = getColorSchemes();
        Optional<ColorScheme> result = schemes.stream().filter( s -> s.getName().equals(name ) ).findFirst();
        if ( ! result.isPresent() ) 
        {
            final ColorScheme copy;
            if ( name.equals( getDefaultColorSchemeName() ) ) 
            {
                copy = ColorScheme.getDefault();
                System.out.println("Using default color scheme");
            } 
            else if ( name.equals( getDefaultCompareColorSchemeName() ) ) 
            {
                copy = ColorScheme.getDefaultCompare();
                System.out.println("Using default compare color scheme");
            } else {
                return result;
            }
            copy.setName( name );
            schemes.add( copy  );
            setColorSchemes(schemes);            
            return Optional.of(copy);
        }
        return result;
    }
}