package de.codesourcery.toyprofiler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import de.codesourcery.toyprofiler.Profile.MethodStats;
import de.codesourcery.toyprofiler.util.ParameterMap;
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

        public MethodStats(XMLStreamReader reader) throws XMLStreamException
        {
        	this.method = Integer.parseInt( readAttribute( "methodNameId" , reader ) );
        	this.invocationCount = Long.parseLong( readAttribute("invocations",reader ) );
        	this.totalTimeMillis = Float.parseFloat( readAttribute("totalTime",reader ) );
		}

		public void save(XMLStreamWriter writer) throws XMLStreamException
		{
			writer.writeStartElement("invocation");
			writer.writeAttribute( "methodNameId" , Integer.toString( method ) );
			writer.writeAttribute( "invocations" , Long.toString( invocationCount ) );
			writer.writeAttribute( "totalTime" , Float.toString( totalTimeMillis ) );
			for ( MethodStats s : callees.values() )
			{
				s.save( writer );
			}
			writer.writeEndElement();
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

    protected static final String readAttribute(String attrName,XMLStreamReader reader)
    {
        final String result = readAttribute( attrName , null , reader );
        if ( result == null ) {
            throw new RuntimeException("Internal error, <"+reader.getLocalName()+"/> tag lacks '"+attrName+"' attribute");
        }
        return result;
    }
    
    protected static final String readAttribute(String attrName,String defaultValue,XMLStreamReader reader)
    {
    	String value=null;
    	for ( int i = 0 , len = reader.getAttributeCount() ; i < len ; i++) {
    		final QName name = reader.getAttributeName( i );
    		if ( attrName.equals( name.getLocalPart() ) ) {
    			value= reader.getAttributeValue( i );
    			break;
    		}
    	}
    	if ( value == null || value.trim().length() == 0 )
    	{
    	    return defaultValue;
    	}
    	return value;
    }

    public Profile(XMLStreamReader reader) throws XMLStreamException
    {
    	this.threadName=readAttribute("threadName",reader);
    	this.creationTime= Long.parseLong( readAttribute("creationTime" , "0" , reader ) );
        this.metaData = readAttribute("metaData",null,reader);
        
    	System.out.println("Loading profile '"+threadName+"'");
    	final Stack<MethodStats> stack = new Stack<>();
    	while ( reader.hasNext() )
    	{
    		switch( reader.next() )
    		{
    			case XMLStreamReader.START_ELEMENT:
    				if ( "invocation".equals( reader.getLocalName() ) )
    				{
    					final MethodStats stats = new MethodStats( reader );
    					if ( topLevelMethod == null ) {
    						topLevelMethod = stats;
    					}
    					if ( ! stack.isEmpty() )
    					{
    						stats.parent = stack.peek();
    						stack.peek().callees.put( stats.method , stats );
    					}
    					stack.push( stats );
    				}
    				break;
    			case XMLStreamReader.END_ELEMENT:
    				if ( "profile".equals( reader.getLocalName() ) )
    				{
    					return;
    				}
    				else if ( "invocation".equals( reader.getLocalName() ) )
    				{
    					stack.pop();
    				}
    				break;
    		}
    	}
    }
    
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

    public static ProfileContainer load(InputStream in) throws IOException
    {
    	final List<Profile> result = new ArrayList<>();
    	
    	final HashIntObjMap<String> methodNameMap = HashIntObjMaps.newMutableMap( 2000 );
    	
    	XMLStreamReader reader = null;
    	try
    	{
    		final XMLInputFactory factory = XMLInputFactory.newFactory();
			reader = factory.createXMLStreamReader( in );

			while ( reader.hasNext() )
			{
				switch( reader.next() )
				{
					case XMLStreamReader.START_ELEMENT:
						if ( "methodName".equals( reader.getLocalName() ) )
						{
							final int id = Integer.parseInt( readAttribute( "id" , reader ) );
							final String name = readAttribute( "name" , reader );
							methodNameMap.put(id,name);
						}
						else if ( "profile".equals( reader.getLocalName() ) )
						{
							result.add( new Profile( reader ) );
						}
						break;
					case XMLStreamReader.END_ELEMENT:
						break;
				}
			}
		}
    	catch (XMLStreamException e)
    	{
			throw new IOException(e);
		}
    	finally
    	{
			if ( reader != null )
			{
				try { reader.close(); } catch(XMLStreamException e) { /* ok */ }
			}
		}
    	return new ProfileContainer( result , methodNameMap );
    }

    public static void save(OutputStream out) throws IOException
    {
    	XMLStreamWriter writer = null;
    	try
    	{
    		final XMLOutputFactory factory = XMLOutputFactory.newFactory();
			writer = factory.createXMLStreamWriter( out );
			writer.writeStartDocument("UTF-8", "1.0" );

			writer.writeStartElement("profilingResults");  // <profilingResults>

			writer.writeStartElement("methodNames"); // <methodNames>
			for ( Integer id : ID_TO_METHOD_NAME.keySet() )
			{
				final String name = ID_TO_METHOD_NAME.get( id.intValue() );
				writer.writeStartElement("methodName"); // <methodName...>
				writer.writeAttribute( "id" , Integer.toString(id) );
				writer.writeAttribute( "name" , name );
				writer.writeEndElement(); // </methodName>
			}
			writer.writeEndElement(); // </methodNames>

			writer.writeStartElement("profiles"); // <profiles>

			for ( Profile p : PROFILES_BY_THREAD.values() )
			{
				System.out.println("Writing profile "+p.threadName+" ...");
				p.save(writer);
			}

			writer.writeEndElement(); // </profiles>

			writer.writeEndElement(); // </profilingResults>

			writer.writeEndDocument();
			writer.close();
		}
    	catch (XMLStreamException e)
    	{
			throw new IOException(e);
		}
    	finally
    	{
			if ( writer != null )
			{
				try { writer.close(); } catch(XMLStreamException e) { /* ok */ }
			}
		}
    }

	private void save(XMLStreamWriter writer) throws XMLStreamException
	{
		writer.writeStartElement("profile");
		writer.writeAttribute("threadName" , threadName );
		writer.writeAttribute("creationTime" , Long.toString( creationTime ) );
		if ( metaData != null ) {
		    writer.writeAttribute("metaData" , metaData );
		}
		
		if ( topLevelMethod != null )
		{
			topLevelMethod.save( writer );
		}
		writer.writeEndElement();
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
}