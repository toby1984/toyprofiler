package de.codesourcery.toyprofiler;

import java.util.Map;
import java.util.NoSuchElementException;

import de.codesourcery.toyprofiler.Profile.MethodIdentifier;

public interface IRawMethodNameProvider
{
    /**
     * 
     * @param stats
     * @return method name or <code>NULL</code> if no method name is available
     */    
    public MethodIdentifier getRawMethodName(int methodId);
    
    public int getMethodId(MethodIdentifier rawMethodName,boolean ignoreLineNumber) throws NoSuchElementException;
    
    public Map<Integer,MethodIdentifier> getMethodMap();
}
