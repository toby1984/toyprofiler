package de.codesourcery.toyprofiler;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import de.codesourcery.toyprofiler.util.ParameterMap;
import de.codesourcery.toyprofiler.util.XMLSerializer;
import net.openhft.koloboke.collect.map.hash.HashIntObjMap;
import net.openhft.koloboke.collect.map.hash.HashIntObjMaps;

public final class Profile
{
    protected static final ConcurrentHashMap<Thread, Profile> PROFILES_BY_THREAD = new ConcurrentHashMap<>();

    protected static volatile boolean profilingEnabled;

    protected static final ClassMethodsContainer CLASS_METHOD_CONTAINER = new ClassMethodsContainer();

    protected static final boolean DONT_GUESS_STACKTRACE = true;

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

    public static final class MethodIdentifier {

    	public final int id;
        public final String className;
        public final String methodName;
        public final String methodSignature;
        public final int lineNumber;

        public MethodIdentifier(int id,String className,String methodName,String methodSignature)
        {
            if ( className == null || methodName == null || methodSignature == null ) {
                throw new IllegalArgumentException();
            }
            if ( ! methodSignature.startsWith("(" ) ) {
                throw new IllegalArgumentException("Invalid method signature: "+methodSignature);
            }
            this.id = id;
            this.className = className;
            this.methodName = methodName;
            this.methodSignature = methodSignature;
            this.lineNumber = -1;
        }

        public MethodIdentifier(int id,String className,String methodName,String methodSignature,int lineNumber)
        {
            if ( className == null || methodName == null || methodSignature == null ) {
                throw new IllegalArgumentException();
            }
            if ( ! methodSignature.startsWith("(" ) ) {
                throw new IllegalArgumentException("Invalid method signature: "+methodSignature);
            }
            if ( lineNumber < 1 ) {
                throw new IllegalArgumentException();
            }
            this.id = id;
            this.className = className;
            this.methodName = methodName;
            this.methodSignature = methodSignature;
            this.lineNumber = lineNumber;
        }

        public boolean hasLineNumber() {
            return lineNumber != -1;
        }

        public boolean matches(String className,String methodName) {
            return this.className.equals( className ) && this.methodName.equals( methodName );
        }

        public boolean matches(MethodIdentifier other)
        {
            if ( other != null ) {
                return this.className.equals( other.className ) &&
                        this.methodName.equals( other.methodName ) &&
                        this.methodSignature.equals( other.methodSignature ) &&
                        this.lineNumber == other.lineNumber;
            }
            return false;
        }

        public boolean matchesIgnoringLineNumber(MethodIdentifier other)
        {
            if ( other != null ) {
                return this.className.equals( other.className ) &&
                        this.methodName.equals( other.methodName ) &&
                        this.methodSignature.equals( other.methodSignature );
            }
            return false;
        }

        @Override
        public boolean equals(Object obj) {
            throw new UnsupportedOperationException("equals()");
        }

        @Override
        public int hashCode() {
            throw new UnsupportedOperationException("hashCode()");
        }

        @Override
        public String toString()
        {
            if ( lineNumber == -1 ) {
                return className+"|"+methodName+"|"+methodSignature;
            }
            return className+"|"+methodName+"|"+methodSignature+"|"+lineNumber;
        }

        public static MethodIdentifier fromString(int id,String s) {
            final String[] parts = s.split("\\|");
            if ( parts.length == 3 ) {
                return new MethodIdentifier( id , parts[0] , parts[1] , parts[2] );
            }
            return new MethodIdentifier( id , parts[0] , parts[1] , parts[2] , Integer.parseInt( parts[3] ) );
        }
    }

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

        public int[] getPathFromRoot()
        {
            final List<Integer> path = new ArrayList<>();
            addPathToRoot(path);
            Collections.reverse( path );
            return path.stream().mapToInt( v -> v.intValue() ).toArray();
        }

        private void addPathToRoot(List<Integer> path) {
            path.add( Integer.valueOf( method ) );
            if ( parent != null ) {
                parent.addPathToRoot( path );
            }
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

        public double getPercentageOfParentTime()
        {
            if ( parent == null ) {
                return 1f;
            }
            return ( getTotalTimeMillis() / (double) parent.getTotalTimeMillis());
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
    	CLASS_METHOD_CONTAINER.clear();
    	PROFILES_BY_THREAD.values().forEach( Profile::clear );
    }

    protected void clear()
    {
    	topLevelMethod = null;
    	currentMethod = null;
    }

    protected void onEnter(int method)
    {
    	MethodStats callee;
    	final MethodStats currentMethod = this.currentMethod;
        if ( currentMethod == null )
        {
        	callee = new MethodStats(method);
        	if (DONT_GUESS_STACKTRACE ) {
        	    topLevelMethod = callee;
        	} else {
        	    // TODO: FIXME !!! The following code works when running in 'request' mode but breaks stuff when profiling an app from the very beginning...
        	    topLevelMethod = inspectStack(method,callee);
        	}
        }
        else
        {
        	callee = currentMethod.callees.get( method );
        	if ( callee == null ) {
        		callee = new MethodStats(method,currentMethod);
        		currentMethod.callees.put( method , callee );
        	}
        }
        this.currentMethod = callee;
        callee.onEnter();
    }

    protected void onExit()
    {
        currentMethod.onExit();
        currentMethod = currentMethod.parent;
    }

    private MethodStats inspectStack(int method, MethodStats calledMethod)
    {
    	final String threadName = Thread.currentThread().getName();
    	System.out.println("["+threadName+"] Trying to resolve top-level method from stack trace involving "+CLASS_METHOD_CONTAINER.getRawMethodName(calledMethod.method));
    	MethodStats result = calledMethod;
    	final StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    	Arrays.stream( stackTrace ).forEach( System.out::println );
		for (int j = 4 ; j < stackTrace.length; j++)
		{
			final StackTraceElement element = stackTrace[j];
			if ( element.getMethodName() != null && ! element.getMethodName().contains( "ambda" ) && ! element.getClassName().contains(("ambda") ) )
			{
				final Integer methodId = CLASS_METHOD_CONTAINER.getRegisteredMethod( element );
				System.out.println("["+threadName+"] getRegisteredMethod( "+element.getClassName()+", "+element.getMethodName()+" , "+element.getLineNumber()+" => "+methodId);
				if ( methodId != null )
				{
					final MethodStats parent = new MethodStats( methodId.intValue() );
					result.setParent( parent );
					parent.callees.put( calledMethod.method , result );
					result = parent;
				}
			}
		}
    	System.out.println("["+threadName+"] Start of stack: "+CLASS_METHOD_CONTAINER.getRawMethodName( method ) );
		return result;
	}

    public static void registerMethod(MethodIdentifier name)
    {
   		CLASS_METHOD_CONTAINER.registerMethod(name);
    }

    public static void methodEntered(int method)
    {
    	if ( profilingEnabled ) {
    		INSTANCE.get().onEnter(method);
    	}
    }

    public static void methodLeft()
    {
    	if ( profilingEnabled ) {
    		INSTANCE.get().onExit();
    	}
    }

    public static void startProfiling() {
    	profilingEnabled = true;
    }

    public static void stopProfiling()
    {
    	profilingEnabled = false;
    }

    public static boolean isProfilingEnabled() {
    	return profilingEnabled;
    }

    @Override
    public String toString()
    {
        return "Thread[ "+threadName+"]";
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

    public static void save(OutputStream outputStream) throws IOException {
        new XMLSerializer().save(CLASS_METHOD_CONTAINER,PROFILES_BY_THREAD.values() , outputStream );
    }

    public static String printAll()
    {
        final StringBuilder buffer = new StringBuilder();
        final MethodStatsHelper helper = new MethodStatsHelper( CLASS_METHOD_CONTAINER );
        PROFILES_BY_THREAD.values().stream().map( helper::print ).collect( Collectors.joining("\n") );
        return PROFILES_BY_THREAD.size()+" profiles.\n\n"+buffer.toString();
    }

    public MethodStats lookupByPath(int[] methodIds) throws IllegalStateException,NoSuchElementException
    {
        if ( topLevelMethod == null ) {
            throw new IllegalStateException("lookupByPath() called on profile without top-level method");
        }
        if (methodIds.length == 0 ) {
            throw new IllegalArgumentException("path too short");
        }
        MethodStats current = topLevelMethod;
        if ( current.getMethodId() != methodIds[0] )
        {
            throw new NoSuchElementException("Path mismatch @ 0 , "+current.getMethodId()+" <-> "+methodIds[0] );
        }

        for ( int i = 1 , len = methodIds.length ; i < len ; i++ )
        {
            MethodStats next = current.getCallees().get( methodIds[i] );
            if ( next == null ) {
                throw new NoSuchElementException("Path mismatch @ "+i+" , node has no successor with methodId "+methodIds[i]);
            }
            current = next;
        }
        return current;
    }
}