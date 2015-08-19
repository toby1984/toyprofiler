package de.codesourcery.toyprofiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import de.codesourcery.toyprofiler.Profile.MethodIdentifier;
import net.openhft.koloboke.collect.map.hash.HashIntObjMap;
import net.openhft.koloboke.collect.map.hash.HashIntObjMaps;
import net.openhft.koloboke.collect.map.hash.HashObjObjMap;
import net.openhft.koloboke.collect.map.hash.HashObjObjMaps;

public final class ClassMethodsContainer implements IClassMethodsContainer
{
    private final HashObjObjMap<String,Map<String,List<MethodIdentifier>>> CLASS_TO_METHODS_MAP = HashObjObjMaps.newMutableMap( 2000 );

    private final HashIntObjMap<MethodIdentifier> ID_TO_METHOD_NAME = HashIntObjMaps.newMutableMap( 2000 );

    public ClassMethodsContainer() {
    }

    public ClassMethodsContainer(Map<Integer,MethodIdentifier> map) {

    	for ( Entry<Integer, MethodIdentifier> i : map.entrySet() )
    	{
    		registerMethod( i.getValue() );
    	}
    }

    public void registerMethod(MethodIdentifier name)
    {
    	ID_TO_METHOD_NAME.put( name.id , name );

   		Map<String, List<MethodIdentifier>> map = CLASS_TO_METHODS_MAP.get( name.className );
   		if ( map == null ) {
   			map = HashObjObjMaps.newMutableMap( 100 );
   			CLASS_TO_METHODS_MAP.put( name.className , map );
   		}
   		List<MethodIdentifier> list = map.get( name.methodName );
   		if ( list == null ) {
   			list = new ArrayList<>( 5 );
   			map.put( name.methodName , list );
   		}
   		list.add( name );
    }

    public void clear() {
    	CLASS_TO_METHODS_MAP.clear();
    	ID_TO_METHOD_NAME.clear();
    }

	@Override
	public boolean isOverloadedMethod(MethodIdentifier identifier)
	{
		return ! isNotOverloadedMethod( identifier );
	}

	public boolean isNotOverloadedMethod(MethodIdentifier identifier)
	{
		final Map<String, List<MethodIdentifier>> map = CLASS_TO_METHODS_MAP.get( identifier.className );
		if ( map == null ) {
			throw new NoSuchElementException("Unknown class "+identifier.className+"' ??");
		}
		final List<MethodIdentifier> methodList = map.get( identifier.methodName );
		if ( methodList == null || methodList.isEmpty() ) {
			throw new NoSuchElementException("Unknown method "+identifier.className+"' for class '"+identifier.className+" ??");
		}
		return methodList.size() == 1;
	}

	@Override
	public MethodIdentifier getRawMethodName(int methodId) {
		return ID_TO_METHOD_NAME.get( methodId );
	}

	@Override
	public int getMethodId(MethodIdentifier rawMethodName) throws NoSuchElementException
	{
        for ( final Entry<Integer, MethodIdentifier> entry : ID_TO_METHOD_NAME.entrySet() )
        {
        	final MethodIdentifier candidate = entry.getValue();
			if ( candidate.className.equals( rawMethodName.className ) )
        	 {
        		 if ( candidate.methodSignature.equals( rawMethodName.methodSignature ) || isNotOverloadedMethod( candidate) )
        		 {
        			return entry.getKey();
        		 }
        	}
        }
        throw new NoSuchElementException("Failed to resolve raw method name '"+rawMethodName+"'");
	}

	@Override
	public void visitMethods(Consumer<MethodIdentifier> visitor)
	{
		ID_TO_METHOD_NAME.values().forEach( visitor:: accept);
	}

	public Integer getRegisteredMethod(StackTraceElement element)
	{
		final String method = element.getMethodName();
		final String clazz = element.getClassName().replace(".","/");
		final String prefix = clazz+"|"+method;
		final List<Integer> candidates = new ArrayList<>();
		System.out.println("["+Thread.currentThread().getName()+"] Looking for '"+prefix+"'");
		for ( Entry<Integer, MethodIdentifier> existing : ID_TO_METHOD_NAME.entrySet() )
		{
			if ( existing.getValue().matches( clazz , method ) ) {
				candidates.add( existing.getKey() );
			}
		}
		switch( candidates.size() ) {
			case 0:
				return null;
			case 1:
				return candidates.get(0);
			default:
				for ( Integer key : candidates )
				{
					final MethodIdentifier parts = ID_TO_METHOD_NAME.get(key.intValue());
					if ( parts.hasLineNumber() )
					{
						if ( parts.lineNumber == element.getLineNumber() ) {
							return key;
						}
					}
				}
				System.err.println("WARN: Found candidates ("+candidates.stream().map( ID_TO_METHOD_NAME::get ).map( s -> s.toString() ).collect(Collectors.joining(","))+") "
						+ "but none matched the linenumber from the JVM StackTraceElement ("+element.getLineNumber()+")");
				return null;
		}
	}
}
