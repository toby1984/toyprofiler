package de.codesourcery.toyprofiler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.TraceClassVisitor;

import de.codesourcery.toyprofiler.util.ClassMatcher;
import de.codesourcery.toyprofiler.util.ParameterMap;

public class Agent
{
	protected static boolean DEBUG_TRANSFORM = false;
	protected static boolean DEBUG_PRINT_TRANSFORMED = false;
	protected static boolean DEBUG_DUMP_STATISTICS = false;
	
	protected static boolean INSERT_DIRECT_JUMP_TO_PROFILER = false;

	protected static long stringIndex;
	private static ClassMatcher[] includedClasses = new ClassMatcher[0];
	private static ClassMatcher[] excludedClasses = new ClassMatcher[0];
	
	private static File outputFile;

	protected static AtomicInteger uniqueID = new AtomicInteger(0);

	public static void premain(String agentArgs, Instrumentation inst)
	{
		System.out.println("Profiling agent loaded");
		
		parseArguments(agentArgs);

		Runtime.getRuntime().addShutdownHook( new Thread( () -> 
		{ 
		    if ( DEBUG_DUMP_STATISTICS ) {
		        System.out.println( Profile.printAllThreads() );
		    }
		    if ( outputFile != null ) 
		    {
		        System.out.println("Saving profiling results to "+outputFile.getAbsolutePath());
		        try {
		            Profile.save( new FileOutputStream( outputFile ) );
		        } catch(Exception e) {
		            e.printStackTrace();
		        }
		    }
		}) );

		inst.addTransformer( new ClassFileTransformer()
		{
			@Override
			public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException
			{
				for ( int i = 0 , len = excludedClasses.length ; i < len ; i++ )
				{
					if ( excludedClasses[i].matches( className ) ) {
						return classfileBuffer;
					}
				}
				for ( int i = 0 , len = includedClasses.length ; i < len ; i++ )
				{
					if ( includedClasses[i].matches( className ) )
					{
						final byte[] instrumented = doInstrument( className , classfileBuffer );

						if ( DEBUG_PRINT_TRANSFORMED )
						{
							final TraceClassVisitor trace = new TraceClassVisitor( new PrintWriter(System.out) );
							new ClassReader( instrumented).accept( new ClassVisitor(Opcodes.ASM5 , trace ) {
								@Override
								public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
									final MethodVisitor result = super.visitMethod(access, className, desc, signature, exceptions);
									return new MethodVisitor(Opcodes.ASM5, result )
									{
										@Override
										public void visitLineNumber(int line, Label start) {
										}
									};
								};
							} , 0 );
						}
						return instrumented;
					}
				}
				return classfileBuffer;
			}

			private byte[] doInstrument(String className,byte[] clazz)
			{
				try
				{
					if ( DEBUG_TRANSFORM ) {
						System.out.println("Instrumenting class "+className);
					}

					final ClassReader classReader=new ClassReader(clazz);
					final ClassWriter wrappedWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
					final MyWriter writer = new MyWriter(Opcodes.ASM5,wrappedWriter);
					classReader.accept( writer , 0 );
					final byte[] result = wrappedWriter.toByteArray();
					if ( DEBUG_TRANSFORM ) {
					    System.out.println( clazz.length+" bytes in , "+result.length+" bytes out");
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
		});
	}

    private static void parseArguments(String agentArgs) {
        final ParameterMap arguments = new ParameterMap(agentArgs);
		
		if ( arguments.hasKey("file" ) ) {
		    outputFile = new File( arguments.get("file" ) );
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

			final String methodName = currentClassName()+"|"+name+"|"+desc;
			final int methodId = uniqueID.incrementAndGet();
			Profile.registerMethod( methodName , methodId );
			return new MethodVisitor(Opcodes.ASM5,visitor)
			{
				private Label start;
				private Label end;
				private Label successfulReturn;
				private int returnOpcode;

				@Override
				public void visitCode() {

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
}