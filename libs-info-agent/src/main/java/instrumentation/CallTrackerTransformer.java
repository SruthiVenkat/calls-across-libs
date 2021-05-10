package instrumentation;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
//import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import db.DatabaseConnector;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

public class CallTrackerTransformer implements ClassFileTransformer {
	public static Map<String, List<String>> libsToClasses = new HashMap<String, List<String>>();
	public static List<String> visitedCallerMethods = new ArrayList<String>();
	public static List<String> visitedClasses = new ArrayList<String>();

	public static String codeToAdd = "";
	public static DatabaseConnector connector;

	public CallTrackerTransformer() {
		connector = DatabaseConnector.buildDatabaseConnector();
		connector.connect();
		libsToClasses = connector.getLibsToClasses();
	}

	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
		byte[] byteCode = classfileBuffer;
		try {
			ClassPool classPool = ClassPool.getDefault();
			CtClass ctClass = classPool.makeClass(new ByteArrayInputStream(
					classfileBuffer));
			String methodCallerClassName = ctClass.getName();
			if (methodCallerClassName.startsWith("java.") || methodCallerClassName.startsWith("javax.")
					|| methodCallerClassName.startsWith("sun.") || methodCallerClassName.startsWith("jdk.")) {
				// ignore Java internal classes
				return null;
			}

			System.out.println("------>"+methodCallerClassName);
			visitedClasses.add(methodCallerClassName);
			CtMethod[] methods = ctClass.getDeclaredMethods();
			for (CtMethod method : methods) {
				if (!isNative(method) && !isAbstract(method) && !visitedCallerMethods.contains(method.getLongName())) {
					visitedCallerMethods.add(method.getLongName());
					System.out.println("Checking method - "+method.getLongName());
					codeToAdd = "";
					Entry<String, List<String>> unknownEntry = new AbstractMap.SimpleEntry<String, List<String>>("unknownLib", new ArrayList<String>());
					String callingMethodName = method.getName();
					String callingMethodLibNameTmp = "";
					try {
						callingMethodLibNameTmp = libsToClasses.entrySet().stream()
						.filter(map -> map.getValue().contains(methodCallerClassName))
						.findAny().orElse(unknownEntry).getKey();
					} catch (NullPointerException e) {
						e.printStackTrace();
					}

					final String callingMethodLibName = callingMethodLibNameTmp;
					method.instrument(
					        new ExprEditor() {
					            public void edit(MethodCall m) throws CannotCompileException
					            {
									String calledMethodName = m.getMethodName();
					            	String methodCalledClassName = m.getClassName();
									String calledMethodLibName = "";
									
									if (!methodCalledClassName.startsWith("java.") && !methodCalledClassName.startsWith("javax.")
											&& !methodCalledClassName.startsWith("sun.")) // ignore Java internal classes
									{
						                //System.out.println(callingMethodName+" -- "+calledMethodName);
										
										try {
											calledMethodLibName = libsToClasses.entrySet().stream()
											.filter(map -> map.getValue().contains(methodCalledClassName))
											.findAny().orElse(unknownEntry).getKey();
										} catch (NullPointerException e) {
											e.printStackTrace();
										}
										
										try {	
											String instrMethod = m.getMethod().getLongName();
											if (!callingMethodLibName.equals(unknownEntry.getKey()) && !callingMethodLibName.equals(calledMethodLibName)) {
												// static
												connector.updateCountInCallerCalleeCountTable(methodCallerClassName+"::"+callingMethodName, callingMethodLibName,
														methodCalledClassName+"::"+calledMethodName, calledMethodLibName, 1, 0);
												
												// dynamic
												codeToAdd += "instrumentation.CallTrackerTransformer.connector.updateCountInCallerCalleeCountTable(\""+methodCallerClassName+"::"+callingMethodName+"\",\""+
													callingMethodLibName+"\", \""+methodCalledClassName+"::"+calledMethodName+"\", \""+
													calledMethodLibName+"\", 0, 1);";
											}
										} catch (Exception e) {
											System.out.println(e);
										}
									}
					            }
					        });
					//System.out.println(codeToAdd);
					method.insertBefore(codeToAdd);	
				}
			}
			byteCode = ctClass.toBytecode();
			ctClass.detach();
		} catch (Throwable ex) {
			System.out.println("Exception: " + ex);
			ex.printStackTrace();
		}
		return byteCode;
	}
	
	public static boolean isNative(CtMethod method) {
	    return Modifier.isNative(method.getModifiers());
	}
	
	public static boolean isAbstract(CtMethod method) {
		return Modifier.isAbstract(method.getModifiers());
	}

}