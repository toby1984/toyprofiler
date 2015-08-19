package de.codesourcery.toyprofiler;

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.objectweb.asm.Type;

import de.codesourcery.toyprofiler.Profile.MethodIdentifier;
import de.codesourcery.toyprofiler.Profile.MethodStats;

public class MethodStatsHelper
{
    private final IClassMethodsContainer resolver;

    public static final MethodStatsHelper NOP_INSTANCE = new MethodStatsHelper( new IClassMethodsContainer() {

        @Override
        public MethodIdentifier getRawMethodName(int methodId) {
            return null;
        }

        @Override
        public int getMethodId(MethodIdentifier rawMethodName ) {
            throw new NoSuchElementException("Failed to resolve raw method name '"+rawMethodName+"'");
        }

		@Override
		public boolean isOverloadedMethod(MethodIdentifier identifier) {
			throw new UnsupportedOperationException("isOverloaded() not implemented yet");
		}

		@Override
		public void visitMethods(Consumer<MethodIdentifier> visitor) {
		}
    });

    public MethodStatsHelper(IClassMethodsContainer resolver) {
        this.resolver = resolver;
    }

    public MethodIdentifier[] resolveMethodIds(int[] methodIds) throws NoSuchElementException
    {
        final MethodIdentifier[] result = new MethodIdentifier[ methodIds.length ];
        for ( int i = 0,len=result.length ; i < len ; i++ )
        {
            final MethodIdentifier rawMethodName = resolver.getRawMethodName( methodIds[i] );
            if ( rawMethodName == null ) {
                throw new NoSuchElementException("Failed to resolve method name for methodId "+methodIds[i]);
            }
            result[i] = rawMethodName;
        }
        return result;
    }

    public int[] resolveMethodNames(MethodIdentifier[] rawMethodNames) throws NoSuchElementException
    {
        final int[] result = new int[ rawMethodNames.length ];
        for ( int i = 0,len=result.length ; i < len ; i++ )
        {
            try {
                result[i] = resolver.getMethodId( rawMethodNames[i] );
            }
            catch(NoSuchElementException e)
            {
//                resolver.getMethodMap().values().stream().map( s -> s.toString() ).sorted().forEach( s -> System.out.println("GOT: "+s) );
                final String resolved = Arrays.stream( rawMethodNames ).limit( i ).map(s ->s.toString()).collect( Collectors.joining(" <-> " ) );
                final String unresolved = Arrays.stream( rawMethodNames ).skip( i ).map( s -> s.toString() ).collect( Collectors.joining(" <-> " ) );
                System.out.flush();
                System.err.flush();
                System.out.println("\nRESOLVED  : "+resolved);
                System.out.println("\nUNRESOLVED: "+unresolved);
                System.out.flush();
                System.err.flush();
                throw e;
            }
        }
        return result;
    }

    public String getMethodName(MethodStats stats) {
        return getRawMethodName(stats).methodName;
    }

    public String getClassName(MethodStats stats) {
        return getRawClassName(stats).replace('/','.');
    }

    public String getRawClassName(MethodStats stats) {
        return getRawMethodName(stats).className;
    }

    public String getSimpleClassName(MethodStats stats)
    {
        final String[] parts = getRawClassName(stats).split("/");
        return parts[ parts.length -1 ];
    }

    public String getMethodSignature(MethodStats stats)
    {
        try {
            return Arrays.stream( Type.getArgumentTypes( getRawMethodSignature(stats) ) ).map( this::toSimpleClassName ).collect(Collectors.joining(","));
        } catch(ArrayIndexOutOfBoundsException e)
        {
            System.out.println("signature: "+getRawMethodSignature( stats ) );
            throw e;
        }
    }

    private String toSimpleClassName(Type t)
    {
        final String result = t.getClassName();
        for ( int i = result.length()-1 ; i >= 0 ; i-- )
        {
            if ( result.charAt(i) == '.' ) {
                return result.substring( i+1 , result.length() );
            }
        }
        return result;
    }

    public String getRawMethodSignature(MethodStats stats) {
        return getRawMethodName(stats).methodSignature;
    }

    private String toText(MethodStats stats)
    {
        final float avgTime = stats.getTotalTimeMillis() / stats.getInvocationCount();
        final String sAvgTime = ""+( avgTime < 1 ?  avgTime*1000000f+" ns" : avgTime+" ms" );
        final String sTotalTime = ""+( stats.getTotalTimeMillis() < 1 ? stats.getTotalTimeMillis() *1000000f+" ns" : stats.getTotalTimeMillis()+" ms" );
        return getRawMethodName(stats)+" | invocations: "+stats.getInvocationCount()+" | avg. time: "+sAvgTime+" | total time: "+sTotalTime;
    }

    public String toString(MethodStats stats) {
        final StringBuilder buffer = new StringBuilder();
        print( stats, buffer );
        return buffer.toString();
    }

    public String print(Profile profile)
    {
        if ( profile.getTopLevelMethod() == null ) {
            return profile.toString();
        }
        return profile.toString()+"\n"+toString( profile.getTopLevelMethod() );
    }

    private void print(MethodStats node,StringBuilder buffer) {
        print("", node , true,buffer);
    }

    private void print(String prefix, MethodStats node, boolean isTail,StringBuilder buffer)
    {
        buffer.append(prefix + (isTail ? "└── " : "├── ") + toText(node) ).append("\n");
        for (int i = 0; i < node.getChildCount() - 1; i++) {
            print(prefix + (isTail ? "    " : "│   "), node.child(i) , false,buffer);
        }
        if ( node.hasChildren() )
        {
            print(prefix + (isTail ?"    " : "│   "), node.child( node.getChildCount() - 1), true,buffer);
        }
    }

    public MethodIdentifier getRawMethodName(MethodStats stats)
    {
        return resolver.getRawMethodName( stats.getMethodId() );
    }
}