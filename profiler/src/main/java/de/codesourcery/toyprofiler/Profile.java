package de.codesourcery.toyprofiler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Profile 
{
    private static final ConcurrentHashMap<Thread, Profile> PROFILES_BY_THREAD = new ConcurrentHashMap<>();
    
    public static final ThreadLocal<Profile> INSTANCE = new ThreadLocal<Profile>() 
    {
        protected Profile initialValue() 
        {
            final Profile profile = new Profile(Thread.currentThread());
            PROFILES_BY_THREAD.put( Thread.currentThread() , profile );
            return profile;
        }
    };
    
    public static final class MethodStats 
    {
        public final String method;
        public MethodStats parent;
        private final Map<String,MethodStats> callees = new HashMap<>();
        
        public long invocationCount;
        public long totalTime;
        
        public long time;
        
        public MethodStats(String method) 
        {
            this.method = method;
        }
        
        public MethodStats(String method,MethodStats parent) 
        {
            this.method = method;
            this.parent = parent;
        }
        
        public void onEnter() 
        {
            invocationCount++;
            time = System.currentTimeMillis();
        }
        
        public void onExit() 
        {
            totalTime += (System.currentTimeMillis() - time ); 
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
            final float avgTime = totalTime / (float) invocationCount;
            return method+" | invocations: "+invocationCount+" | avg. time: "+avgTime+" ms | total time: "+totalTime;
        }
        
        private MethodStats child(int index) 
        {
            final String key = callees.keySet().stream().sorted().skip(index).findFirst().orElseThrow( () -> new IllegalArgumentException("Invalid index "+index) );
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
    
    private final Thread currentThread;
    private MethodStats topLevelMethod;
    private MethodStats currentMethod;
    
    public Profile(Thread currentThread) {
        this.currentThread = currentThread;
    }
    
    private void onEnter(String method) 
    {
        if ( currentMethod == null ) 
        {
            topLevelMethod = currentMethod = new MethodStats(method);
            currentMethod.onEnter();
            return;
        }
        
        MethodStats callee = currentMethod.callees.get( method );
        if ( callee == null ) {
            callee = new MethodStats(method,currentMethod);
            currentMethod.callees.put( method , callee );
        }
        currentMethod = callee;
        callee.onEnter();
    }
    
    private void onExit() 
    {
        currentMethod.onExit();
        currentMethod = currentMethod.parent;
    }
    
    public static void methodEntered(String method) 
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
            return "Thread[ "+currentThread.getName()+"]\n<no top-level method>";
        }
        return "Thread[ "+currentThread.getName()+"]\n"+topLevelMethod.toString();
    }
}