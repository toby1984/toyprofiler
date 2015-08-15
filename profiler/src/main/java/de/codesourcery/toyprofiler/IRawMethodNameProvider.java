package de.codesourcery.toyprofiler;

public interface IRawMethodNameProvider
{
    /**
     * 
     * @param stats
     * @return method name or <code>NULL</code> if no method name is available
     */    
    public String getRawMethodName(int methodId);
    
    public int getMethodId(String rawMethodName);
}
