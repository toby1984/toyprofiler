package de.codesourcery.toyprofiler.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Map;

import de.codesourcery.toyprofiler.Profile;
import de.codesourcery.toyprofiler.Profile.MethodIdentifier;
import de.codesourcery.toyprofiler.ProfileContainer;

public interface IProfileIOAdapter {

    ProfileContainer load(InputStream in) throws IOException;

    void save(Map<Integer, MethodIdentifier> methodNameMap, Collection<Profile> profiles,
            OutputStream out) throws IOException;

}