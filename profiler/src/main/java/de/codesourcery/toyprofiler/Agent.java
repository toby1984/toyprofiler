package de.codesourcery.toyprofiler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.management.ManagementFactory;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.TraceClassVisitor;

import de.codesourcery.toyprofiler.Profile.MethodIdentifier;
import de.codesourcery.toyprofiler.util.ClassMatcher;
import de.codesourcery.toyprofiler.util.ParameterMap;

public class Agent
{
	protected static boolean DEBUG_TRANSFORM = false;
	protected static boolean DEBUG_PRINT_TRANSFORMED = false;
	protected static boolean DEBUG_DUMP_STATISTICS = false;

	protected static boolean INSERT_DIRECT_JUMP_TO_PROFILER = false;

	protected static long stringIndex;
	protected static ClassMatcher[] includedClasses = new ClassMatcher[0];
	protected static ClassMatcher[] excludedClasses = new ClassMatcher[0];

	protected static File outputFile;

	protected static enum InstrumentationMode { ON_STARTUP , ON_REQUEST };

	protected static final Set<String> classesToTransform = new HashSet<>();

	protected static AtomicInteger uniqueID = new AtomicInteger(0);

	private static InstrumentationMode mode;

	private static Instrumentation instrumentation;

	private static ScanningTransformer scanningTransformer;

	protected static final class ScanningTransformer implements ClassFileTransformer
	{
		@Override
		public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException
		{
			if ( needsTransform( className ) ) {
				classesToTransform.add( className );
			}
			return classfileBuffer;
		}
	}

	protected static final class RedefineTransformer implements ClassFileTransformer
	{
		@Override
		public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException
		{
			if ( classesToTransform.contains( className ) )
			{
				return instrumentClass( className , classfileBuffer );
			}
			return classfileBuffer;
		}
	};

	protected static final class OnStartupTransformer implements ClassFileTransformer
	{
		@Override
		public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException
		{
			if ( needsTransform( className ) )
			{
				return instrumentClass( className , classfileBuffer );
			}
			return classfileBuffer;
		}
	};

	public static void premain(String agentArgs, Instrumentation inst)
	{
		Agent.instrumentation = inst;

		System.out.println("Profiling agent loaded");

		parseArguments(agentArgs);

		Runtime.getRuntime().addShutdownHook( new Thread( () ->
		{
		    if ( DEBUG_DUMP_STATISTICS )
		    {
		        System.out.println( Profile.printAll() );
		    }
		    if ( outputFile != null && mode == InstrumentationMode.ON_STARTUP )
		    {
		        saveProfile();
		    }
		}) );

		if ( mode == InstrumentationMode.ON_STARTUP )
		{
			Profile.startProfiling();
			inst.addTransformer( new OnStartupTransformer() , false );
		}
		else
		{
			scanningTransformer = new ScanningTransformer();
			inst.addTransformer( scanningTransformer, false );

			final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
			try {
				final ObjectName name = new ObjectName("de.codesourcery.toyprofiler:type=AgentRemoteControlMBean");
				mbs.registerMBean(new AgentRemoteControl(), name);
			}
			catch (Exception e )
			{
				throw new RuntimeException("Failed to register MBean",e);
			}
		}
	}

	private static void saveProfile()
	{
        System.out.println("Saving profiling results to "+outputFile.getAbsolutePath());
        try {
            Profile.save( new FileOutputStream( outputFile ) );
        } catch(Exception e) {
            e.printStackTrace();
        }
	}

	protected static boolean needsTransform(String className)
	{
		for ( int i = 0 , len = excludedClasses.length ; i < len ; i++ )
		{
			if ( excludedClasses[i].matches( className ) ) {
				return false;
			}
		}
		for ( int i = 0 , len = includedClasses.length ; i < len ; i++ )
		{
			if ( includedClasses[i].matches( className ) )
			{
				return true;
			}
		}
		return false;
	}

	private static byte[] instrumentClass(String className,byte[] clazz)
	{
		try
		{
			System.out.println("Instrumenting class "+className);

			final ClassReader classReader=new ClassReader(clazz);
			final ClassWriter wrappedWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
			final MyWriter writer = new MyWriter(Opcodes.ASM5,wrappedWriter);
			classReader.accept( writer , 0 );
			final byte[] result = wrappedWriter.toByteArray();
			if ( DEBUG_TRANSFORM ) {
			    System.out.println( clazz.length+" bytes in , "+result.length+" bytes out");
			}
			if ( DEBUG_PRINT_TRANSFORMED )
			{
				final TraceClassVisitor trace = new TraceClassVisitor( new PrintWriter(System.out) );
				new ClassReader( result ).accept( trace , 0 );
			}
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

    private static void parseArguments(String agentArgs)
    {
        final ParameterMap arguments = new ParameterMap(agentArgs);

        System.out.println("Arguments: "+arguments);

		if ( arguments.hasKey("file" ) ) {
		    outputFile = new File( arguments.get("file" ) );
		}

		mode = InstrumentationMode.ON_STARTUP;
		if ( arguments.hasKey( "mode" ) )
		{
			switch( arguments.get("mode").toLowerCase() ) {
				case "startup": break;
				case "request": mode = InstrumentationMode.ON_REQUEST; break;
				default:
					throw new RuntimeException("Invalid value '"+arguments.get("mode")+" for 'mode' command-line parameter (valid are: startup,request)");
			}
		}

		if ( mode == InstrumentationMode.ON_STARTUP ) {
			System.out.println("Instrumenting classes on startup");
		} else {
			System.out.println("Instrumenting classes on request");
		}

		DEBUG_DUMP_STATISTICS = arguments.getBoolean("print",false);
		DEBUG_TRANSFORM = arguments.getBoolean("debug",false);

		final String[] patterns = arguments.get("include").split(",");
		includedClasses = Arrays.stream( patterns ).map( ClassMatcher::new ).collect( Collectors.toList() ).toArray( new ClassMatcher[0] );

		if ( arguments.hasKey( "exclude" ) ) {
			final String[] tmp = arguments.get("exclude").split(",");
			excludedClasses = Arrays.stream( tmp ).map( ClassMatcher::new ).collect( Collectors.toList() ).toArray( new ClassMatcher[0] );
		}

		System.out.println("Matching "+includedClasses.length+" class include patterns:");
		Arrays.stream( includedClasses ).forEach( System.out::println );

		System.out.println("Matching "+excludedClasses.length+" exclude patterns:");
		Arrays.stream( excludedClasses ).forEach( System.out::println );
    }

	protected static final class MyWriter extends ClassVisitor
	{
		private boolean instrumentMethods=true;

		private final Stack<String> classNameStack = new Stack<>();

		public MyWriter(int api,ClassWriter writer)
		{
			super(api,writer);
		}

		private void pushClassname(String name) {
			if ( classNameStack.isEmpty() ) {
				classNameStack.push(name);
			}
			classNameStack.push(name);
		}

		private String currentClassName() {
			return classNameStack.peek();
		}

		private void popClassname() {
			classNameStack.pop();
		}

		@Override
		public void visitInnerClass(String name, String outerName,String innerName, int access)
		{
			pushClassname( outerName+"$"+innerName );
			final boolean oldValue = instrumentMethods;
			instrumentMethods = outerName == null || ! outerName.equals("java/lang/invoke/MethodHandles");

			if ( DEBUG_TRANSFORM ) {
				System.out.println("Visiting inner class: "+currentClassName()+" [ instrument methods: "+instrumentMethods+"]");
			}
			super.visitInnerClass(name, outerName, innerName, access);

			popClassname();
			instrumentMethods = oldValue;
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
		{
			if ( DEBUG_TRANSFORM ) {
				System.out.println("Visiting top-level class "+name);
			}
			pushClassname( name );
			super.visit(version, access, name, signature, superName, interfaces);
			popClassname();
		}

		@Override
		public void visitOuterClass(String owner, String name, String desc)
		{
			pushClassname( name );
			instrumentMethods = true;

			if ( DEBUG_TRANSFORM ) {
				System.out.println("Visiting outer class: "+currentClassName());
			}
			super.visitOuterClass(owner, name, desc);

			instrumentMethods = false;
			popClassname();
		}

		@Override
		public void visitAttribute(Attribute attr)
		{
			System.out.println("ATTRIBUTE: "+attr.type);
			super.visitAttribute(attr);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String desc,String signature, String[] exceptions)
		{
			final boolean isSynthetic = (access & Opcodes.ACC_SYNTHETIC) != 0 || (access & Opcodes.ACC_BRIDGE) != 0;
			final boolean isConstructor = "<init>".equals(name) || "<clinit>".equals(name);
			final MethodVisitor visitor = super.visitMethod(access, name, desc, signature, exceptions);
			if ( isConstructor || isSynthetic || ! instrumentMethods || name.contains("lambda" ) )
			{
				return visitor;
			}
			if ( DEBUG_TRANSFORM )
			{
				System.out.println("Instrumenting method: "+currentClassName()+" - "+name+desc);
			}

			final int methodId = uniqueID.incrementAndGet();
			return new MethodVisitor(Opcodes.ASM5,visitor)
			{
			    private Label methodStart=new Label();
			    private int lineNumber = -1;
				private Label start;
				private Label end;
				private Label successfulReturn;
				private int returnOpcode;

				@Override
				public void visitLineNumber(int line, Label start)
				{
				    if ( lineNumber == -1 ) {
				        lineNumber = line;
				        super.visitLineNumber(line, methodStart );
				    } else {
				        super.visitLineNumber(line, start);
				    }
				}

				@Override
				public void visitCode()
				{
				    visitLabel( methodStart );

					super.visitCode();

					start = new Label();
					end = new Label();
					successfulReturn = new Label();

					mv.visitTryCatchBlock(start , end , end , null);
					mv.visitLdcInsn( methodId );
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, "de/codesourcery/toyprofiler/Profile", "methodEntered", "(I)V", false);

					mv.visitLabel( start );
				}

				@Override
				public void visitInsn(int opcode)
				{
					switch( opcode )
					{
						case Opcodes.RETURN:
						case Opcodes.IRETURN:
						case Opcodes.LRETURN:
						case Opcodes.FRETURN:
						case Opcodes.DRETURN:
						case Opcodes.ARETURN:
							returnOpcode = opcode;
							if ( INSERT_DIRECT_JUMP_TO_PROFILER )
							{
				                 mv.visitMethodInsn(Opcodes.INVOKESTATIC, "de/codesourcery/toyprofiler/Profile", "methodLeft", "()V", false);
							    super.visitInsn( opcode );
							} else {
							    visitJumpInsn(Opcodes.GOTO, successfulReturn );
							}
							return;
					}
					super.visitInsn( opcode );
				}

				@Override
				public void visitMaxs(int maxStack, int maxLocals)
				{
                    final MethodIdentifier methodName;
                    if ( lineNumber == -1 ) {
                        methodName = new MethodIdentifier(methodId,currentClassName() , name , desc );
                    } else {
                        methodName =  new MethodIdentifier(methodId,currentClassName() , name , desc , lineNumber );
                    }
                    Profile.registerMethod( methodName );

					mv.visitJumpInsn(Opcodes.GOTO, successfulReturn);

					mv.visitLabel(end);
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, "de/codesourcery/toyprofiler/Profile", "methodLeft", "()V", false);
					mv.visitInsn(Opcodes.ATHROW);

					mv.visitLabel(successfulReturn);
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, "de/codesourcery/toyprofiler/Profile", "methodLeft", "()V", false);
					mv.visitInsn(returnOpcode);
					super.visitMaxs(maxStack, maxLocals);
				}
			};
		}
	}

	public static void startProfiling()
	{
		if ( Profile.isProfilingEnabled() ) {
			throw new IllegalStateException("Already profiling");
		}

		if ( outputFile == null ) {
			throw new IllegalStateException("Agent started without 'file' argument, don't know where to write profiling results to");
		}

		if ( classesToTransform.isEmpty() )
		{
			throw new IllegalStateException("Found no matching classes to transform ?");
		}

		instrumentation.removeTransformer( scanningTransformer );

		final RedefineTransformer redefineTransformer = new RedefineTransformer();
		instrumentation.addTransformer( redefineTransformer , true );
		try
		{
			System.out.println("Profiling enabled remotely...");
			final Class<?>[] classes = new Class<?>[ classesToTransform.size() ];
			int i = 0;
			for ( String clazz : classesToTransform )
			{
				try {
					classes[i++] = Class.forName( clazz.replace("/",".") );
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
					throw new RuntimeException(e);
				}
			}

			Profile.startProfiling();
			boolean success = false;
			try
			{
				instrumentation.retransformClasses( classes );
				success = true;
			}
			catch (UnmodifiableClassException e)
			{
				final RuntimeException ex = new RuntimeException("Failed to retransform classes: "+e.getMessage(),e);
				ex.printStackTrace();
				throw ex;
			}
			finally
			{
				if ( ! success ) {
					Profile.stopProfiling();
				}
			}
		}
		finally
		{
			instrumentation.removeTransformer( redefineTransformer );
		}
	}

	public static void stopProfiling()
	{
		if ( ! Profile.isProfilingEnabled() ) {
			throw new IllegalStateException("Not profiling ?");
		}

		Profile.stopProfiling();
		// wait some time so any pending threads finish their work
		try { Thread.sleep( 500 ); } catch (InterruptedException e) { /* nop */ }
		saveProfile();
		Profile.reset();
	}

	public static boolean isProfiling() {
		return Profile.isProfilingEnabled();
	}
}