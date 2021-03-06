package de.codesourcery.toyprofiler.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Stack;
import java.util.function.Consumer;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import de.codesourcery.toyprofiler.ClassMethodsContainer;
import de.codesourcery.toyprofiler.Profile;
import de.codesourcery.toyprofiler.Profile.MethodIdentifier;
import de.codesourcery.toyprofiler.Profile.MethodStats;
import de.codesourcery.toyprofiler.ProfileContainer;
import net.openhft.koloboke.collect.map.hash.HashIntObjMap;
import net.openhft.koloboke.collect.map.hash.HashIntObjMaps;

public class XMLSerializer implements IProfileIOAdapter
{
    private MethodStats readMethodStats(XMLStreamReader reader) throws XMLStreamException
    {
        final int method = Integer.parseInt( readAttribute( "methodNameId" , reader ) );
        final MethodStats stats = new MethodStats(method);

        stats.setInvocationCount( Long.parseLong( readAttribute("invocations",reader ) ) );
        stats.setTotalTimeMillis( Float.parseFloat( readAttribute("totalTime",reader ) ) );
        return stats;
    }

    private void save(MethodStats stats,XMLStreamWriter writer) throws XMLStreamException
    {
        writer.writeStartElement("invocation");
        writer.writeAttribute( "methodNameId" , Integer.toString( stats.getMethodId() ) );
        writer.writeAttribute( "invocations" , Long.toString( stats.getInvocationCount() ) );
        writer.writeAttribute( "totalTime" , Float.toString( stats.getTotalTimeMillisRaw() ) );
        for ( MethodStats s : stats.getCallees().values() )
        {
            save( s , writer );
        }
        writer.writeEndElement();
    }

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

    private Profile readProfile(XMLStreamReader reader) throws XMLStreamException
    {
        final String threadName=readAttribute("threadName",reader);
        final Profile profile = new Profile( threadName );

        profile.setCreationTime( Long.parseLong( readAttribute("creationTime" , "0" , reader ) ) );

        System.out.println("Loading profile '"+threadName+"'");
        final Stack<MethodStats> stack = new Stack<>();
        while ( reader.hasNext() )
        {
            switch( reader.next() )
            {
                case XMLStreamReader.START_ELEMENT:
                    if ( "invocation".equals( reader.getLocalName() ) )
                    {
                        final MethodStats stats = readMethodStats( reader );
                        if ( profile.getTopLevelMethod() == null ) {
                            profile.setTopLevelMethod( stats );
                        }
                        if ( ! stack.isEmpty() )
                        {
                            stats.setParent( stack.peek() );
                            stack.peek().getCallees().put( stats.getMethodId() , stats );
                        }
                        stack.push( stats );
                    }
                    break;
                case XMLStreamReader.END_ELEMENT:
                    if ( "profile".equals( reader.getLocalName() ) )
                    {
                        return profile;
                    }
                    else if ( "invocation".equals( reader.getLocalName() ) )
                    {
                        stack.pop();
                    }
                    break;
            }
        }
        return profile;
    }

    /* (non-Javadoc)
     * @see de.codesourcery.toyprofiler.util.IProfileIOAdapter#load(java.io.InputStream)
     */
    @Override
    public ProfileContainer load(InputStream in) throws IOException
    {
        final List<Profile> result = new ArrayList<>();

        final HashIntObjMap<MethodIdentifier> methodNameMap = HashIntObjMaps.newMutableMap( 2000 );

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
                            methodNameMap.put(id, MethodIdentifier.fromString( id , name ) );
                        }
                        else if ( "profile".equals( reader.getLocalName() ) )
                        {
                            result.add( readProfile( reader ) );
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
        return new ProfileContainer( result , new ClassMethodsContainer( methodNameMap ) );
    }

    /* (non-Javadoc)
     * @see de.codesourcery.toyprofiler.util.IProfileIOAdapter#save(java.util.Map, java.util.Collection, java.io.OutputStream)
     */
    @Override
    public void save(ClassMethodsContainer methodContainer , Collection<Profile> profiles, OutputStream out) throws IOException
    {
        boolean success = false;
        XMLStreamWriter writer = null;
        try
        {
            final XMLOutputFactory factory = XMLOutputFactory.newFactory();
            writer = factory.createXMLStreamWriter( out );
            writer.writeStartDocument("UTF-8", "1.0" );

            writer.writeStartElement("profilingResults");  // <profilingResults>

            writer.writeStartElement("methodNames"); // <methodNames>

            final XMLStreamWriter finalWriter = writer;
            final Consumer<MethodIdentifier> visitor = name ->
            {
                try
                {
                    finalWriter.writeStartElement("methodName"); // </methodName>
	                finalWriter.writeAttribute( "id" , Integer.toString( name.id ) );
	                finalWriter.writeAttribute( "name" , name.toString() );
	                finalWriter.writeEndElement(); // </methodName>
				} catch (XMLStreamException e) {
					throw new RuntimeException(e);
				}

            };

            try {
            	methodContainer.visitMethods( visitor );
            }
            catch(RuntimeException e)
            {
            	if ( e.getCause() instanceof XMLStreamException)
            	{
            		throw (XMLStreamException) e.getCause();
            	}
            	throw e;
            }

            writer.writeEndElement(); // </methodNames>

            writer.writeStartElement("profiles"); // <profiles>

            for ( Profile p : profiles )
            {
                System.out.println("Writing profile "+p.getThreadName()+" ...");
                save( p , writer);
            }

            writer.writeEndElement(); // </profiles>

            writer.writeEndElement(); // </profilingResults>

            writer.writeEndDocument();
            writer.close();
            success = true;
        }
        catch (XMLStreamException e)
        {
            throw new IOException(e);
        }
        finally
        {
            if ( writer != null )
            {
                try {
                    writer.close();
                }
                catch(XMLStreamException e)
                {
                    if ( success )
                    {
                        throw new IOException(e);
                    }
                }
            }
            else
            {
                try {
                    out.close();
                }
                catch(IOException e)
                {
                    if ( success )
                    {
                        throw new IOException(e);
                    }
                }
            }
        }
    }

    private void save(Profile p , XMLStreamWriter writer) throws XMLStreamException
    {
        writer.writeStartElement("profile");
        writer.writeAttribute("threadName" , p.getThreadName() );
        writer.writeAttribute("creationTime" , Long.toString( p.getCreationTimeMillis() ) );

        if ( p.getTopLevelMethod() != null )
        {
            save( p.getTopLevelMethod() , writer );
        }
        writer.writeEndElement();
    }
}