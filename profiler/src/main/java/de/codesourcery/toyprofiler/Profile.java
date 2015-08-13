package de.codesourcery.toyprofiler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.carrotsearch.hppc.cursors.ObjectCursor;
import com.carrotsearch.hppc.predicates.IntPredicate;

public class Profile
{
    private static final ConcurrentHashMap<Thread, Profile> PROFILES_BY_THREAD = new ConcurrentHashMap<>();

    private static final IntObjectHashMap<String> ID_TO_METHOD_NAME = new IntObjectHashMap<>(2000);

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

    public static final class MethodStats
    {
        public long invocationCount;
        public float totalTimeMillis;

        public final int method;
        private final IntObjectHashMap<MethodStats> callees = new IntObjectHashMap<>(2000);
        public MethodStats parent;

        public long time; // transient

        public MethodStats(int method)
        {
            this.method = method;
        }

        public MethodStats(int method,MethodStats parent)
        {
            this.method = method;
            this.parent = parent;
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
			for ( ObjectCursor<MethodStats> s : callees.values() )
			{
				s.value.save( writer );
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

        @Override
        public String toString() {
            final StringBuilder buffer = new StringBuilder();
            print( this, buffer );
            return buffer.toString();
        }

        public void print(MethodStats node,StringBuilder buffer) {
            print("", node , true,buffer);
        }

        private int getChildCount() {
            return callees.size();
        }

        private boolean hasChildren() {
            return getChildCount() != 0;
        }

        private String toText()
        {
            final float avgTime = totalTimeMillis / invocationCount;
            final String sAvgTime = ""+( avgTime < 1 ? avgTime*1000000f+" ns" : avgTime+" ms" );
            final String sTotalTime = ""+( totalTimeMillis < 1 ? totalTimeMillis *1000000f+" ns" : totalTimeMillis+" ms" );
            return getRawMethodName()+" | invocations: "+invocationCount+" | avg. time: "+sAvgTime+" | total time: "+sTotalTime;
        }

        public String getRawMethodName() {
        	final String name =  ID_TO_METHOD_NAME.get( this.method );
        	return name == null ? "<unknown method>" : name;
        }

        private MethodStats child(int index)
        {
        	final List<Integer> keys = new ArrayList<>();
        	final IntPredicate pred = new IntPredicate() {

				@Override
				public boolean apply(int value)
				{
					keys.add( value );
					return true;
				}
        	};
        	callees.keys().forEach( pred );
        	Collections.sort( keys );
            final int key = keys.get( index );
            return callees.get(key);
        }

        private void print(String prefix, MethodStats node, boolean isTail,StringBuilder buffer)
        {
            buffer.append(prefix + (isTail ? "└── " : "├── ") + node.toText() ).append("\n");
            for (int i = 0; i < node.getChildCount() - 1; i++) {
                print(prefix + (isTail ? "    " : "│   "), node.child(i) , false,buffer);
            }
            if ( node.hasChildren() )
            {
                print(prefix + (isTail ?"    " : "│   "), node.child( node.getChildCount() - 1), true,buffer);
            }
        }
    }

    private final String currentThread;

    private MethodStats topLevelMethod;
    private MethodStats currentMethod;

    protected static final String readAttribute(String attrName,XMLStreamReader reader)
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
    		throw new RuntimeException("Internal error, <"+reader.getLocalName()+"/> tag lacks '"+attrName+"' attribute");
    	}
    	return value;
    }

    public Profile(XMLStreamReader reader) throws XMLStreamException
    {
    	this.currentThread=readAttribute("threadName",reader);
    	System.out.println("Loading profile '"+currentThread+"'");
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
        this.currentThread = currentThread.getName();
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
            return "Thread[ "+currentThread+"]\n<no top-level method>";
        }
        return "Thread[ "+currentThread+"]\n"+topLevelMethod.toString();
    }

    public static List<Profile> load(InputStream in) throws IOException
    {
    	final List<Profile> result = new ArrayList<>();
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
//						System.out.println("<"+reader.getLocalName()+">");
						if ( "methodName".equals( reader.getLocalName() ) )
						{
							final int id = Integer.parseInt( readAttribute( "id" , reader ) );
							final String name = readAttribute( "name" , reader );
							ID_TO_METHOD_NAME.put(id,name);
						}
						else if ( "profile".equals( reader.getLocalName() ) )
						{
							result.add( new Profile( reader ) );
						}
						break;
					case XMLStreamReader.END_ELEMENT:
//						System.out.println("</"+reader.getLocalName()+">");
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
    	return result;
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
			for ( IntCursor entry : ID_TO_METHOD_NAME.keys() )
			{
				final int id = entry.value;
				final String name = ID_TO_METHOD_NAME.get( id );
				writer.writeStartElement("methodName"); // <methodName...>
				writer.writeAttribute( "id" , Integer.toString(id) );
				writer.writeAttribute( "name" , name );
				writer.writeEndElement(); // </methodName>
			}
			writer.writeEndElement(); // </methodNames>

			writer.writeStartElement("profiles"); // <profiles>

			for ( Profile p : PROFILES_BY_THREAD.values() )
			{
				System.out.println("Writing profile "+p.currentThread+" ...");
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
		writer.writeAttribute("threadName" , currentThread );
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
		return currentThread;
	}
}