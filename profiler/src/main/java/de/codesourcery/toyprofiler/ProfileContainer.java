package de.codesourcery.toyprofiler;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.codesourcery.toyprofiler.Profile.MethodStats;

public class ProfileContainer implements IRawMethodNameProvider , Iterable<Profile>
{
    private final List<Profile> profiles;
    private final Map<Integer,String> methodNames;

    public ProfileContainer(List<Profile> profiles,Map<Integer,String> methodNames) 
    {
        this.profiles = profiles;
        this.methodNames = methodNames;
    }
    
    public Map<Integer, String> getMethodNamesMap() {
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
    public String getRawMethodName(MethodStats stats) 
    {
        return methodNames.get( stats.getMethodId() );
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
}