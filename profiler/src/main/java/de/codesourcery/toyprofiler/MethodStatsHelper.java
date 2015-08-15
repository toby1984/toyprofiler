package de.codesourcery.toyprofiler;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.objectweb.asm.Type;

import de.codesourcery.toyprofiler.Profile.MethodStats;

public class MethodStatsHelper 
{
    private final IRawMethodNameProvider resolver;
    
    public static final MethodStatsHelper NOP_INSTANCE = new MethodStatsHelper( stats -> null );
    
    public MethodStatsHelper(IRawMethodNameProvider resolver) {
        this.resolver = resolver;
    }
    
    public String getMethodName(MethodStats stats) {
        return getRawMethodName(stats).split("\\|")[1].replace("()","");
    }

    public String getClassName(MethodStats stats) {
        return getRawClassName(stats).replace('/','.');
    }
    
    public String getRawClassName(MethodStats stats) {
        return getRawMethodName(stats).split("\\|")[0];
    }

    public String getSimpleClassName(MethodStats stats)
    {
        final String[] parts = getRawClassName(stats).split("/");
        return parts[ parts.length -1 ];
    }
    
    public String getMethodSignature(MethodStats stats) 
    {
        return Arrays.stream( Type.getArgumentTypes( getRawMethodSignature(stats) ) ).map( this::toSimpleClassName ).collect(Collectors.joining(","));
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
        return getRawMethodName(stats).split("\\|")[2];
    }
    
    private String toText(MethodStats stats)
    {
        final float avgTime = stats.getTotalTimeMillis() / stats.getInvocationCount();
        final String sAvgTime = ""+( avgTime < 1 ? avgTime*1000000f+" ns" : avgTime+" ms" );
        final String sTotalTime = ""+( stats.getTotalTimeMillis() < 1 ? stats.getTotalTimeMillis() *1000000f+" ns" : stats.getTotalTimeMillis()+" ms" );
        return getRawMethodName(stats)+" | invocations: "+stats.getInvocationCount()+" | avg. time: "+sAvgTime+" | total time: "+sTotalTime;
    }
    
    public String toString(MethodStats stats) {
        final StringBuilder buffer = new StringBuilder();
        print( stats, buffer );
        return buffer.toString();
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
    
    public String getRawMethodName(MethodStats stats) 
    {
        final String name =  resolver.getRawMethodName( stats );
        return name == null ? "<unknown class>|<unknown method>|()>" : name;
    }       
}