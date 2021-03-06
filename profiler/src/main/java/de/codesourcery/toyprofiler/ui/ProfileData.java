package de.codesourcery.toyprofiler.ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import de.codesourcery.toyprofiler.IClassMethodsContainer;
import de.codesourcery.toyprofiler.Profile;
import de.codesourcery.toyprofiler.Profile.MethodIdentifier;
import de.codesourcery.toyprofiler.ProfileContainer;
import de.codesourcery.toyprofiler.util.IProfileIOAdapter;
import de.codesourcery.toyprofiler.util.ParameterMap;

public final class ProfileData implements IClassMethodsContainer
{
    private File sourceFile;
    private final ProfileContainer container;
    private final List<Profile> profiles;
    private Profile selectedProfile;
    private boolean isDirty = false;

    public ProfileData(File sourceFile,ProfileContainer container,Optional<Profile> selectedProfile)
    {
        this.sourceFile = sourceFile;
        this.container = container;
        this.profiles = new ArrayList<>( container.getProfiles() );
        this.selectedProfile = selectedProfile.isPresent() ? selectedProfile.get() : null ;
    }

    public List<Profile> getProfiles() {
        return profiles;
    }

    public boolean isDirty() {
        return isDirty;
    }

    public ProfileContainer getProfileContainer() {
		return container;
	}

    public void setFile( File sourceFile ) {
        this.sourceFile = sourceFile;
    }

    public void save(File file,IProfileIOAdapter serializer) throws IOException
    {
       try ( FileOutputStream out = new FileOutputStream(file) )
        {
            serializer.save( container.getMethodContainer() , container.getProfiles() , out );
        }
       this.sourceFile = file;
       this.isDirty = false;
    }

    public Optional<ZonedDateTime> getTimestamp()
    {
        ZonedDateTime earliest = null;
        for ( Profile pr : profiles )
        {
            if ( pr.getCreationTime().isPresent() ) {
                if ( earliest == null || pr.getCreationTime().get().isBefore( earliest ) ) {
                    earliest = pr.getCreationTime().get();
                }
            }
        }
        return Optional.ofNullable( earliest );
    }

    public Optional<String> getDescription()
    {
        if ( getSelectedProfile().isPresent() )
        {
            final ParameterMap dataMap = getSelectedProfile().get().getMetaDataMap();
            if ( dataMap.hasKey("description") ) {
                return Optional.of( dataMap.get("description" ) );
            }
        }
        return Optional.empty();
    }

    public void setDescription(String desc)
    {
        final ParameterMap map = new ParameterMap();
        map.put("description",desc);
        if ( getSelectedProfile().isPresent() )
        {
            getSelectedProfile().get().mergeMetaData( map );
            isDirty = true;
        }
    }

    public boolean hasFile() {
        return sourceFile != null;
    }

    public Optional<File> getSourceFile() {
        return Optional.ofNullable( sourceFile );
    }

    @Override
    public String toString()
    {
        String file = hasFile() ? getSourceFile().get().getAbsolutePath() : "<no file>";
        final DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssZZZ");
        final String ts = getTimestamp().map( tz -> format.format( tz ) ).orElse("<no timestamp");
        return file + " @ "+ts;
    }

    public Optional<Profile> getSelectedProfile() {
        return Optional.ofNullable( selectedProfile );
    }

    public void setSelectedProfile(Profile selectedProfile)
    {
        this.selectedProfile = selectedProfile;
    }

    public Optional<String> getSelectedThreadName() {
        return selectedProfile != null ? Optional.of( selectedProfile.getThreadName() ) : Optional.empty();
    }

    @Override
    public MethodIdentifier getRawMethodName(int methodId) {
        return container.getRawMethodName( methodId );
    }

    @Override
    public int getMethodId(MethodIdentifier rawMethodName) {
        return container.getMethodId( rawMethodName );
    }

    public Optional<Profile> getProfileByThreadName(String threadName)
    {
        return profiles.stream().filter( p -> threadName.equals( p.getThreadName() ) ).findFirst();
    }

	@Override
	public boolean isOverloadedMethod(MethodIdentifier identifier) {
		return container.isOverloadedMethod( identifier );
	}

	@Override
	public void visitMethods(Consumer<MethodIdentifier> visitor) {
		container.visitMethods( visitor );
	}
}