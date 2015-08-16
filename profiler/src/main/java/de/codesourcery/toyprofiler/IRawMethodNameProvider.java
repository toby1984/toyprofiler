package de.codesourcery.toyprofiler;

import java.util.Map;
import java.util.NoSuchElementException;

public interface IRawMethodNameProvider
{
    /**
     * 
     * @param stats
     * @return method name or <code>NULL</code> if no method name is available
     */    
    public String getRawMethodName(int methodId);
    
    public int getMethodId(String rawMethodName) throws NoSuchElementException;
    
    public Map<Integer,String> getMethodMap();
}
