package de.codesourcery.toyprofiler;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import de.codesourcery.toyprofiler.Profile.MethodIdentifier;

public class ProfileContainer implements IClassMethodsContainer , Iterable<Profile>
{
    private final List<Profile> profiles;
    private final ClassMethodsContainer methodContainer;

    public ProfileContainer(List<Profile> profiles,ClassMethodsContainer methodContainer)
    {
        this.profiles = profiles;
        this.methodContainer = methodContainer;
    }

    public ClassMethodsContainer getMethodContainer() {
		return methodContainer;
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
        return methodContainer.getRawMethodName( methodId );
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
    public int getMethodId(MethodIdentifier rawMethodName)
    {
    	return this.methodContainer.getMethodId( rawMethodName );
    }

	@Override
	public boolean isOverloadedMethod(MethodIdentifier identifier) {
		return methodContainer.isOverloadedMethod( identifier );
	}

	@Override
	public void visitMethods(Consumer<MethodIdentifier> visitor) {
		methodContainer.visitMethods( visitor );
	}
}