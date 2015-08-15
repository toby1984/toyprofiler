package de.codesourcery.toyprofiler.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class ParameterMap 
{
    private static final String VALID_ARGS_KEY_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789_-";
    
    private final Map<String,String> parameters = new HashMap<>();

    public ParameterMap() {
    }
    
    public ParameterMap(Map<String,String> map) 
    {
        map.forEach( (key,value) -> {
            put(key,value);
        });
    }
    
    public ParameterMap(Optional<String> paramString)
    {
        this( paramString.isPresent() ? paramString.get() : null );
    }
    
    public ParameterMap(String paramString)
    {
        if ( paramString != null )
        {
            boolean inKey = true;
            final StringBuilder key = new StringBuilder();
            final StringBuilder value = new StringBuilder();
            for ( int i = 0 ; i < paramString.length() ; i++ )
            {
                final char c = paramString.charAt( i );
                
                if ( inKey ) 
                {
                    if ( c == '=' ) 
                    {
                        inKey = false;
                        continue;
                    }
                    key.append( c );
                }
                else
                {
                    if ( c == '=' )
                    {
                        inKey = true;
                        int j = value.length()-1;
                        for ( ; j >= 0 && VALID_ARGS_KEY_CHARS.indexOf( value.charAt(j)  ) != -1; j-- )
                        {
                        }
                        if ( j > 0 )
                        {
                            final String realValue = value.substring( 0 , j );
                            parameters.put( key.toString(), realValue );
                            int delta = value.length()-1 - j;
                            j = delta+1;
                        } else {
                            parameters.put( value.toString(), "" );
                        }
                        i -= j;
                        key.setLength(0);
                        value.setLength(0);
                    } else {
                        value.append( c );
                    }
                }
            }
            if ( key.length() > 0 )
            {
                parameters.put( key.toString(), value.toString() );
            }
        }
    }
    
    public boolean getBoolean(String key,boolean defaultValue) 
    {
        String value = get(key,null);
        return value != null ? Boolean.parseBoolean( value ) : defaultValue;
    }

    public Set<String> keys() {
        return parameters.keySet();
    }

    public boolean hasKey(String key) {
    	return get(key,null) != null;
    }

    public String get(String key) {
        final String result = get(key,null);
    	if ( result == null ) {
    		throw new RuntimeException("Missing value for mandatory configuration parameter '"+key+"'");
    	}
    	return result;
    }

    public String get(String key,String defaultValue)
    {
        final String value = parameters.get(key);
        if ( ( value == null || value.trim().length() == 0 ) )
        {
        	return defaultValue;
        }
        return value;
    }
    
    public void putAll(Map<String,String> map) 
    {
        map.forEach( (key,value) -> put(key,value ) );
    }
    
    public void put(String key,String value) {
        if ( key == null || key.trim().length() == 0 ) {
            throw new IllegalArgumentException("Key must not be NULL/blank");
        }
        if ( value == null ) {
            throw new IllegalArgumentException("Value must not be NULL");
        }
        if ( key.contains("=" ) ) {
            throw new IllegalArgumentException("Key must not contain '='");
        }
        if ( value.contains("=" ) ) {
            throw new IllegalArgumentException("Value must not contain '='");
        }
        parameters.put( key , value );
    }
    
    public String get(String key,boolean failIfMissing)
    {
        final String value = parameters.get(key);
        if ( ( value == null || value.trim().length() == 0 ) )
        {
        	if ( failIfMissing ) {
        		throw new RuntimeException("Missing value for mandatory configuration parameter '"+key+"'");
        	}
        }
        return value;
    }

    @Override
    public String toString() {
        return parameters.entrySet().stream().map( entry -> entry.getKey()+"="+entry.getValue() ).collect( Collectors.joining("," ) );
    }
    
    public static void main(String[] cmd) 
    {
        final Map<String,String> map1 = new HashMap<>(); 
        map1.put("a","1");
        map1.put("b","2");
        
        final ParameterMap args1 = new ParameterMap();
        args1.putAll(map1);
        
        System.out.println("GOT: "+args1);

        if ( ! map1.equals(args1.toMap() ) ) {
            throw new RuntimeException("Mismatch");
        }
        
        final ParameterMap args2 = new ParameterMap( args1.toString() );
        if ( ! map1.equals( args2.toMap() ) ) {
            throw new RuntimeException("Mismatch.\n Expected: \n"+map1+"\n\nbut got\n\n"+args2.toMap());
        }
    }
    
    public Map<String,String> toMap() {
        return new HashMap<>(this.parameters);
    }
}