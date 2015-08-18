package de.codesourcery.toyprofiler;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Optional;

import de.codesourcery.toyprofiler.Profile.MethodIdentifier;

public class ProfileContainer implements IRawMethodNameProvider , Iterable<Profile>
{
    private final List<Profile> profiles;
    private final Map<Integer,MethodIdentifier> methodNames;

    public ProfileContainer(List<Profile> profiles,Map<Integer,MethodIdentifier> methodNames) 
    {
        this.profiles = profiles;
        this.methodNames = methodNames;
    }
    
    public Map<Integer, MethodIdentifier> getMethodNamesMap() {
        return methodNames;
    }
    
    public List<Profile> getProfiles() {
        return profiles;
    }
    
    public int size() {
        return profiles.size();
    }
    
    public boolean isEmpty() {
        return profiles.isEmpty();
    }
    
    public Optional<Profile> getProfileForThread(String threadName) 
    {
        return profiles.stream().filter( s -> s.getThreadName().equals( threadName ) ).findFirst();
    }
    
    @Override
    public MethodIdentifier getRawMethodName(int methodId) 
    {
        return methodNames.get( methodId );
    }

    @Override
    public Iterator<Profile> iterator() 
    {
        final Iterator<Profile> it = profiles.iterator();
        return new Iterator<Profile>() // prevent caller from using Iterator#remove() and corrupting our internal state 
        {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public Profile next() {
                return it.next();
            }
        };
    }

    @Override
    public int getMethodId(MethodIdentifier rawMethodName,boolean ignoreLineNumber) 
    {
        for ( final Entry<Integer, MethodIdentifier> entry : methodNames.entrySet() ) 
        {
            if ( ignoreLineNumber ) 
            {
                if ( entry.getValue().matchesIgnoringLineNumber( rawMethodName ) ) {
                    return entry.getKey();
                }
            } else if ( entry.getValue().matches( rawMethodName ) ) {
                return entry.getKey();
            }
        }
        throw new NoSuchElementException("Failed to resolve raw method name '"+rawMethodName+"'");
    }

    @Override
    public Map<Integer, MethodIdentifier> getMethodMap() {
        return methodNames;
    }
}