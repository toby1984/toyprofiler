package de.codesourcery.toyprofiler.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import de.codesourcery.toyprofiler.Profile;
import de.codesourcery.toyprofiler.ProfileContainer;
import de.codesourcery.toyprofiler.util.IProfileIOAdapter;
import de.codesourcery.toyprofiler.util.XMLSerializer;

public final class ViewingHistory 
{
    private final List<IViewChangeListener> listeners = new ArrayList<>();
    private final List<ProfileData> history = new ArrayList<>();
    private int ptr;

    public interface IViewChangeListener 
    {
        public void viewChanged(Optional<ProfileData> data,boolean triggeredFromComboBox);
    }

    public void addListener(IViewChangeListener l) {
        this.listeners.add(l);
    }
    
    public void removeListener(IViewChangeListener l) {
        this.listeners.remove(l);
    }

    public Optional<File> currentFile() {
        return current().filter( ProfileData::hasFile ).flatMap( ProfileData::getSourceFile );
    }        

    public int size() {
        return history.size();
    }

    public List<ProfileData> getItems() 
    {
        return new ArrayList<>( history );
    }

    public void add(File file,ProfileContainer container) 
    {
        Optional<Profile> newSelection = findSameProfile( container );
        if ( ! newSelection.isPresent() && container.size() > 0 ) 
        {
            newSelection = Optional.of( container.getProfiles().get(0) );
        }
        history.add( new ProfileData( file ,  container ,  newSelection ) );
        ptr = history.size()-1;
        notifyListeners( current() );
    }

    private Optional<Profile> findSameProfile(ProfileContainer container) 
    {
        final Optional<String> currentThread = current().flatMap( s -> s.getSelectedProfile() ).map( p -> p.getThreadName() );
        if ( currentThread.isPresent() ) {
            return container.getProfileForThread( currentThread.get() );
        }
        return Optional.empty();
    }

    public void reloadCurrent() throws FileNotFoundException, IOException 
    {
        final Optional<File> currentFile = current().filter( ProfileData::hasFile ).flatMap( ProfileData::getSourceFile );
        if ( currentFile.isPresent() ) 
        {
            final File file = currentFile.get();

            try ( FileInputStream in =new FileInputStream( file ) )
            {
                final ProfileContainer profiles = new XMLSerializer().load( in );
                final Optional<Profile> newSelection = findSameProfile( profiles );
                history.set( ptr , new ProfileData(file,profiles, newSelection ) );
                notifyListeners( current() );
            } 
        }
    }

    public void setCurrentProfile(Profile profile,boolean triggeredFromComboBox) 
    {
        current().get().setSelectedProfile( profile );
        notifyListeners( current() , triggeredFromComboBox );
    }
    
    public void jumpTo(ProfileData data) 
    {
        final int idx = history.indexOf( data );
        if ( idx == -1 ) {
            throw new IllegalArgumentException("Unknown ProfileData ??");
        }
        ptr = idx;
        notifyListeners( current() );
    }

    public void clear() 
    {
        history.clear();
        ptr=0;
        notifyListeners(Optional.empty());
    }
    
    public void historyChanged() {
        notifyListeners( current() );
    }

    private void notifyListeners(Optional<ProfileData> data) {
        notifyListeners(data,false);
    }

    private void notifyListeners(Optional<ProfileData> data,boolean triggeredFromComboBox) {
        listeners.forEach( l -> l.viewChanged( data , triggeredFromComboBox) );
    }

    public Optional<ProfileData> latest() 
    {
        final Comparator<ProfileData> comp = new Comparator<ProfileData>() 
        {
            @Override
            public int compare(ProfileData p1, ProfileData p2) 
            {
                final Optional<ZonedDateTime> a = p1.getTimestamp();
                final Optional<ZonedDateTime> b = p2.getTimestamp();

                if ( a.isPresent() && b.isPresent() ) {
                    return a.get().compareTo( b.get() );
                }
                if ( a.isPresent() ) {
                    return -1;
                }
                if ( b.isPresent() ) {
                    return 1;
                }
                return 0;
            }
        };

        return history.stream().sorted( comp.reversed() ).findFirst();
    }
    
    public void remove(ProfileData data) 
    {
        remove(data,true);
    }

    private boolean remove(ProfileData data,boolean notify) 
    {
        int idx = history.indexOf( data );
        if (idx == -1 ) {
            return false;
        }
        if ( idx > ptr ) // element is after current ptr, ptr stays the same 
        {
            history.remove( idx );
        } else if ( idx == ptr ) { // element is at the current ptr, stays the same
            history.remove( idx );
            if ( ptr >= history.size() ) {
                while ( ptr > 0 && ptr >= history.size() ) {
                    ptr--;
                }
                if ( notify ) {
                    notifyListeners( current() );
                }
                return true;
            }
        } 
        else 
        {
            history.remove( idx );
            if ( ptr > 0 ) {
                ptr--; 
                if ( notify ) 
                {
                    notifyListeners( current() );
                }
                return true;
            }
        }
        return false;
    }

    public Optional<ProfileData> current() 
    {
        return ptr < history.size() ? Optional.of( history.get( ptr ) ) : Optional.empty();
    }
    
    public Optional<ProfileData> previous() 
    {
        int prev = ptr-1;
        if ( prev >= 0 && prev < history.size() ) 
        {
            return Optional.of( history.get(prev) );
        }
        return Optional.empty();
    }

    public boolean jumpToPrevious() 
    {
        if ( ptr > 0 ) {
            ptr--;
            notifyListeners( current() );                
            return true;
        }
        return false;
    }

    public boolean jumpToNext() 
    {
        if ( (ptr+1) < history.size() ) {
            ptr++;
            notifyListeners( current() );
            return true;
        }
        return false;
    }

    public void unload(List<ProfileData> toUnload) 
    {
        boolean notify = false;
        for (ProfileData profileData : toUnload) 
        {
            System.out.println("Unloading "+profileData);
            notify |= remove( profileData , false );
        }
        if ( notify ) {
            notifyListeners( current() );
        }
    }
    
    public void saveCurrent(File file,IProfileIOAdapter ioAdapter) throws IOException 
    {
        if ( current().isPresent() ) 
        {
            final ProfileData profileData = current().get();
            ioAdapter.save( profileData.getMethodMap() , profileData.getProfiles() , new FileOutputStream( file ) );
            profileData.setFile( file );
        }
    }
}