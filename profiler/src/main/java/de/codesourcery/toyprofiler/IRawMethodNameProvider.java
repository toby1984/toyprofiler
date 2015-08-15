package de.codesourcery.toyprofiler;

import de.codesourcery.toyprofiler.Profile.MethodStats;

@FunctionalInterface
public interface IRawMethodNameProvider
{
    /**
     * 
     * @param stats
     * @return method name or <code>NULL</code> if no method name is available
     */
    public String getRawMethodName(MethodStats stats);
}
