package de.codesourcery.toyprofiler;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import de.codesourcery.toyprofiler.util.ParameterMap;
import de.codesourcery.toyprofiler.util.XMLSerializer;
import net.openhft.koloboke.collect.map.hash.HashIntObjMap;
import net.openhft.koloboke.collect.map.hash.HashIntObjMaps;

public class Profile
{
    private static final ConcurrentHashMap<Thread, Profile> PROFILES_BY_THREAD = new ConcurrentHashMap<>();

    private static final HashIntObjMap<String> ID_TO_METHOD_NAME = HashIntObjMaps.newMutableMap( 2000 );

    public static final ThreadLocal<Profile> INSTANCE = new ThreadLocal<Profile>()
    {
        @Override
		protected Profile initialValue()
        {
            final Profile profile = new Profile(Thread.currentThread());
            PROFILES_BY_THREAD.put( Thread.currentThread() , profile );
            return profile;
        }
    };

    public interface IMethodStatsVisitor
    {
    	public void visit(MethodStats stats,int depth);
    }

    public static final class MethodStats
    {
        private long invocationCount;
        private long time; // transient
        private float totalTimeMillis;

        private final int method;
        private final HashIntObjMap<MethodStats> callees = HashIntObjMaps.newMutableMap( 100 );
        private MethodStats parent;

        public MethodStats(int method)
        {
            this.method = method;
        }
        
        public MethodStats(int method,MethodStats parent)
        {
            this.method = method;
            this.parent = parent;
        }        
        
        public void setInvocationCount(long invocationCount) {
            this.invocationCount = invocationCount;
        }
        
        public void setTotalTimeMillis(float totalTimeMillis) {
            this.totalTimeMillis = totalTimeMillis;
        }
        
        public int getMethodId() {
            return method;
        }
        
        public MethodStats getParent() {
            return parent;
        }
        
        public HashIntObjMap<MethodStats> getCallees() {
            return callees;
        }
   
        public void visit(IMethodStatsVisitor visitor)
        {
        	visit( visitor , 0 );
        }

        private void visit(IMethodStatsVisitor visitor,int depth)
        {
        	visitor.visit( this , depth );
        	callees.values().forEach( value -> value.visit( visitor , depth+1 ) );
        }

        public float getPercentageOfParentTime()
        {
            if ( parent == null ) {
                return 100f;
            }
            final float parentTime = parent.getTotalTimeMillis();
            return 100f*( totalTimeMillis / parentTime );
        }

		public void onEnter()
        {
            invocationCount++;
            time = System.nanoTime();
        }

        public void onExit()
        {
        	totalTimeMillis += (System.nanoTime() - time ) / 1000_000f;
        }

        public int getChildCount() {
            return callees.size();
        }

        public boolean hasChildren() {
            return getChildCount() != 0;
        }

        public MethodStats child(int index)
        {
            final List<Integer> keys = new ArrayList<>( callees.keySet() );
        	Collections.sort( keys );
            final int key = keys.get( index );
            return callees.get(key);
        }
        
        public long getInvocationCount() {
            return invocationCount;
        }

        public float getOwnTimeMillis()
        {
            if ( invocationCount == 0 ) {
                return getTotalOwnTimeMillis();
            }
            return getTotalOwnTimeMillis()/invocationCount;
        }

        public float getTotalTimeMillis()
        {
            if ( Math.abs( totalTimeMillis ) < 0.00001 )
            {
                return getSumTotalChildTimeMillis();
            }
            return totalTimeMillis;
        }
        
        public float getTotalTimeMillisRaw() {
            return totalTimeMillis;
        }

        public float getTotalOwnTimeMillis()
        {
            return getTotalTimeMillis() - getSumTotalChildTimeMillis();
        }

        public float getSumTotalChildTimeMillis()
        {
            float result = 0;
            for ( MethodStats i : callees.values() )
            {
                result += i.totalTimeMillis;
            }
            return result;
        }

        public void setParent(MethodStats parent) {
            this.parent = parent;
        }
    }

    private final String threadName;
    private long creationTime = System.currentTimeMillis();
    private String metaData;
    
    private MethodStats topLevelMethod;
    
    private MethodStats currentMethod;

    public Profile(Thread currentThread) {
        this( currentThread.getName() );
    }
    
    public Profile(String threadName) {
        this.threadName = threadName;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }
    
    public static void reset()
    {
  		ID_TO_METHOD_NAME.clear();
    	PROFILES_BY_THREAD.values().forEach( Profile::clear );
    }

    public void clear() {
    	topLevelMethod = null;
    	currentMethod = null;
    }

    private void onEnter(int method)
    {
    	MethodStats callee;
        if ( currentMethod == null )
        {
            callee = topLevelMethod = new MethodStats(method);
        }
        else
        {
        	callee = currentMethod.callees.get( method );
        	if ( callee == null ) {
        		callee = new MethodStats(method,currentMethod);
        		currentMethod.callees.put( method , callee );
        	}
        }
        currentMethod = callee;
        callee.onEnter();
    }

    private void onExit()
    {
        currentMethod.onExit();
        currentMethod = currentMethod.parent;
    }

    public static void registerMethod(String name,int id)
    {
   		ID_TO_METHOD_NAME.put( id , name );
    }

    public static void methodEntered(int method)
    {
        INSTANCE.get().onEnter(method);
    }

    public static void methodLeft()
    {
        INSTANCE.get().onExit();
    }

    public static String printThisThread() {
        return INSTANCE.get().toString();
    }

    public static String printAllThreads()
    {
        return PROFILES_BY_THREAD.values().stream().map( profile -> profile.toString() ).collect( Collectors.joining("\n" ) );
    }

    @Override
    public String toString()
    {
        if ( topLevelMethod == null ) {
            return "Thread[ "+threadName+"]\n<no top-level method>";
        }
        return "Thread[ "+threadName+"]\n"+topLevelMethod.toString();
    }

	public MethodStats getTopLevelMethod()
	{
		return topLevelMethod;
	}

	public String getThreadName() {
		return threadName;
	}
	
	public Optional<ZonedDateTime> getCreationTime() 
	{
	    if ( creationTime == 0 ) {
	        return Optional.empty();
	    }
        return Optional.of( ZonedDateTime.ofInstant( Instant.ofEpochMilli( creationTime ) , ZoneId.systemDefault() ) );
    }
	
	public Optional<String> getMetaData() 
	{
        return Optional.ofNullable( metaData );
    }
	
	public ParameterMap getMetaDataMap() 
	{
	    return new ParameterMap( this.metaData );
	}
	
	public void mergeMetaData(ParameterMap toMerge) {
	    
	    Map<String,String> current = getMetaDataMap().toMap();
	    current.putAll( toMerge.toMap() );
	    if ( current.isEmpty() ) {
	        setMetaData( (String) null );
	    } else {
	        setMetaData( new ParameterMap( current ).toString() );
	    }
	}
	
    public void setMetaData(ParameterMap map) 
    {
        if ( map == null ) {
            setMetaData( (String) null );
        } else {
            setMetaData( map.toString() );
        }
    }	
	
	public void setMetaData(String metaData) {
        this.metaData = metaData;
    }
	
	public void setTopLevelMethod(MethodStats topLevelMethod) {
        this.topLevelMethod = topLevelMethod;
    }

    public long getCreationTimeMillis() {
        return creationTime;
    }

    public static void save(FileOutputStream fileOutputStream) throws IOException {
        new XMLSerializer().save(ID_TO_METHOD_NAME,PROFILES_BY_THREAD.values() , fileOutputStream );
    }
}