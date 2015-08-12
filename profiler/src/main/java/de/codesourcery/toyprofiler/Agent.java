package de.codesourcery.toyprofiler;

import java.io.PrintWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.TraceClassVisitor;

public class Agent 
{
    protected static final boolean DEBUG_TRANSFORM = true;

    protected static final String VALID_ARGS_KEY_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789_-";
    protected static final String VALID_CLASSNAME1 = "[a-zA-Z]+[a-zA-Z0-9]*";
    protected static final Pattern VALID_CLASSNAME = Pattern.compile("^"+VALID_CLASSNAME1+"([\\.\\$]{1}"+VALID_CLASSNAME1+")*$");

    protected static final class ClassMatcher 
    {
        private final String pattern;
        private final boolean isWildcard;

        public ClassMatcher(String inputPattern) 
        {
            String pattern = inputPattern.trim();
            if ( pattern.endsWith("*" ) ) {
                isWildcard = true;
                pattern = pattern.substring(0, pattern.length()-1 );
            } else {
                isWildcard = false;
            }
            while ( pattern.endsWith("." ) ) {
                pattern = pattern.substring(0, pattern.length()-1 );
            }
            if ( ! VALID_CLASSNAME.matcher( pattern ).matches() ) {
                throw new IllegalArgumentException("Invalid class name pattern: '"+inputPattern+"'");
            }
            this.pattern = pattern.replaceAll("\\.","/");
        }

        public boolean matches(String className) 
        {
            if ( isWildcard ) {
                return className.startsWith( pattern );
            }
            return className.equals( pattern );
        }

        @Override
        public String toString() {
            return isWildcard ? pattern+" <wildcard>" : pattern;
        }
    }

    protected static final class Arguments {

        private final Map<String,String> parameters = new HashMap<>();

        private Arguments(String agentArgs) 
        {
            if ( agentArgs != null ) 
            {
                boolean inKey = true;
                final StringBuilder key = new StringBuilder();
                final StringBuilder value = new StringBuilder();
                for ( int i = 0 ; i < agentArgs.length() ; i++ ) 
                {
                    final char c = agentArgs.charAt( i );
                    if ( inKey ) {
                        if ( c == '=' ) {
                            inKey = false;
                            continue;
                        } 
                        key.append( c );
                    } 
                    else 
                    {
                        if ( c == '=' ) 
                        {
                            inKey = true;
                            int j = value.length()-1; 
                            for ( ; j >= 0 && VALID_ARGS_KEY_CHARS.indexOf( value.charAt(j)  ) != -1; j-- ) 
                            {
                            }
                            if ( j > 0 ) 
                            {
                                final String realValue = value.substring( 0 , j );
                                parameters.put( key.toString(), realValue );
                                int delta = value.length()-1 - j;
                                j = delta+1;
                            } else {
                                parameters.put( value.toString(), "" );
                            }
                            i -= j;
                            key.setLength(0);
                            value.setLength(0);
                        } else {
                            value.append( c );
                        }
                    }
                }
                if ( key.length() > 0 ) 
                {
                    parameters.put( key.toString(), value.toString() );
                }
            }
        }

        public Set<String> keys() {
            return parameters.keySet();
        }

        public String get(String key) {
            return get(key,true);
        }

        public String get(String key,boolean failIfMissing) 
        {
            final String value = parameters.get(key);
            if ( failIfMissing && ( value == null || value.trim().length() == 0 ) ) {
                throw new RuntimeException("Missing value for mandatory configuration parameter '"+key+"'");
            }
            return value;
        }

        @Override
        public String toString() {
            return parameters.toString();
        }
    }    

    protected static long stringIndex;
    private static ClassMatcher[] matchers;

    public static void premain(String agentArgs, Instrumentation inst) 
    {
        System.out.println("Profiling agent V1 loaded");
        final Arguments arguments = new Arguments(agentArgs);

        final String[] patterns = arguments.get("pattern").split(",");
        matchers = Arrays.stream( patterns ).map( ClassMatcher::new ).collect( Collectors.toList() ).toArray( new ClassMatcher[0] );

        System.out.println("Matching "+matchers.length+" class patterns:");
        Arrays.stream( matchers ).forEach( System.out::println );

        inst.addTransformer( new ClassFileTransformer() 
        {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException 
            {
                if ( className.contains("codesourcery") ) {
                    System.out.println("CLASS: "+className);
                }
                for ( int i = 0 , len = matchers.length ; i < len ; i++ ) 
                {
                    if ( matchers[i].matches( className ) ) 
                    {
                        return doInstrument( className , classfileBuffer );
                    }
                }
                return classfileBuffer;
            }

            private byte[] doInstrument(String className,byte[] clazz) 
            {
                try 
                {
                    if ( DEBUG_TRANSFORM ) {
                        System.out.println("Instrumenting xxx class "+className);
                    }
                    
                    final ClassReader classReader=new ClassReader(clazz);
                    final ClassWriter wrappedWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
                    final MyWriter writer = new MyWriter(Opcodes.ASM5,wrappedWriter);
                    TraceClassVisitor tracer = new TraceClassVisitor(  writer , new PrintWriter(System.out) );
//                    classReader.accept( writer , 0 );
                    classReader.accept( tracer , 0 );
                    final byte[] result = wrappedWriter.toByteArray();
                    System.out.println( clazz.length+" bytes in , "+result.length+" bytes out");
                    return result;
                } 
                catch(Throwable t) 
                {
                    t.printStackTrace();
                    if ( t instanceof RuntimeException ) {
                        throw (RuntimeException) t;
                    }
                    if ( t instanceof Error ) {
                        throw (Error) t;
                    }
                    throw new RuntimeException(t);
                } 
            }
        });
    }    

    protected static final class MyWriter extends ClassVisitor 
    {
        private String currentClassName;
        private boolean instrumentMethods=true;
        private final ClassWriter writer;
        
        private final Map<String,String> newStringConstants = new HashMap<>();

        public MyWriter(int api,ClassWriter writer) 
        {
            super(api,writer);
            this.writer = writer;
        }
        
        private int newString(String value) 
        {
            return writer.newConst( value );
        }

        @Override
        public void visitEnd() 
        {
            if ( DEBUG_TRANSFORM ) {
                System.out.println("VISIT END");
            }
            super.visitEnd();
        }
        
        @Override
        public void visitInnerClass(String name, String outerName,String innerName, int access) 
        {
            currentClassName = outerName+"$"+innerName;
            final boolean oldValue = instrumentMethods;
            instrumentMethods = ! outerName.equals("java/lang/invoke/MethodHandles");
            if ( DEBUG_TRANSFORM ) {
                System.out.println("Visiting inner class: "+currentClassName+" [ instrument methods: "+instrumentMethods+"]");
            }
            super.visitInnerClass(name, outerName, innerName, access);
            
            instrumentMethods = oldValue;
        }
        
        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) 
        {
            if ( DEBUG_TRANSFORM ) {
                System.out.println("Visiting top-level class "+name);
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public void visitOuterClass(String owner, String name, String desc) 
        {
            currentClassName = name;
            instrumentMethods = true;
            if ( DEBUG_TRANSFORM ) {
                System.out.println("Visiting outer class: "+currentClassName);
            }
            super.visitOuterClass(owner, name, desc);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc,String signature, String[] exceptions) 
        {
            if ( DEBUG_TRANSFORM ) 
            {
                System.out.println("VISIT: "+currentClassName+" - "+desc);
            }
            return super.visitMethod(access, name, desc, signature, exceptions);
        }
    }    
}