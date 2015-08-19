package de.codesourcery.toyprofiler;

import java.util.NoSuchElementException;
import java.util.function.Consumer;

import de.codesourcery.toyprofiler.Profile.MethodIdentifier;

public interface IClassMethodsContainer
{
    /**
     *
     * @param stats
     * @return method name or <code>NULL</code> if no method name is available
     */
    public MethodIdentifier getRawMethodName(int methodId);

    public int getMethodId(MethodIdentifier methodId) throws NoSuchElementException;

	public void visitMethods(Consumer<MethodIdentifier> visitor);

    public boolean isOverloadedMethod(MethodIdentifier methodId);
}