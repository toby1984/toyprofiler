package de.codesourcery.toyprofiler.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import de.codesourcery.toyprofiler.ClassMethodsContainer;
import de.codesourcery.toyprofiler.Profile;
import de.codesourcery.toyprofiler.ProfileContainer;

public interface IProfileIOAdapter {

    public ProfileContainer load(InputStream in) throws IOException;

    public void save(ClassMethodsContainer methodContainer, Collection<Profile> profiles,OutputStream out) throws IOException;
}