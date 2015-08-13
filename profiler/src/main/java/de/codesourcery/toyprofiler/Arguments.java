package de.codesourcery.toyprofiler;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class Arguments {

    private final Map<String,String> parameters = new HashMap<>();

    Arguments(String agentArgs)
    {
        if ( agentArgs != null )
        {
            boolean inKey = true;
            final StringBuilder key = new StringBuilder();
            final StringBuilder value = new StringBuilder();
            for ( int i = 0 ; i < agentArgs.length() ; i++ )
            {
                final char c = agentArgs.charAt( i );
                if ( inKey ) {
                    if ( c == '=' ) {
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
                        for ( ; j >= 0 && Agent.VALID_ARGS_KEY_CHARS.indexOf( value.charAt(j)  ) != -1; j-- )
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
        return parameters.toString();
    }
}