package instrumentation;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import db.DatabaseConnector;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

class InterLibraryCounts {
	String callerMethodString;
	String callerMethodLibString;
	String calleeMethodString;
	String calleeMethodLibString;
	int staticCount;
	int dynamicCount;
	InterLibraryCounts(String callerMethodString, String callerMethodLibString, String calleeMethodString, 
			String calleeMethodLibString, int staticCount, int dynamicCount) {
		this.callerMethodString = callerMethodString;
		this.callerMethodLibString = callerMethodLibString;
		this.calleeMethodString = calleeMethodString;
		this.calleeMethodLibString = calleeMethodLibString;
		this.staticCount = staticCount;
		this.dynamicCount = dynamicCount;
	}
}

public class CallTrackerTransformer implements ClassFileTransformer {
	public static Map<String, List<String>> libsToClasses = new HashMap<String, List<String>>();
	public static List<String> visitedCallerMethods = new ArrayList<String>();
	public static List<String> visitedClasses = new ArrayList<String>();

	public static DatabaseConnector connector;
	public static List<InterLibraryCounts> interLibraryCounts = new ArrayList<InterLibraryCounts>();

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
			if(!libsToClasses.entrySet().stream().anyMatch(map -> map.getValue().contains(methodCallerClassName))) {
				// ignore Java internal classes, unknown libraries
				return null;
			}

			System.out.println("------>"+methodCallerClassName);
			visitedClasses.add(methodCallerClassName);
			CtMethod[] methods = ctClass.getDeclaredMethods();
			for (CtMethod method : methods) {
				if (!isNative(method) && !isAbstract(method) && !visitedCallerMethods.contains(method.getLongName())
						&& !isUnitTestMethod(method)) {
					visitedCallerMethods.add(method.getLongName());
					Entry<String, List<String>> unknownEntry = new AbstractMap.SimpleEntry<String, List<String>>("unknownLib", new ArrayList<String>());
					String callingMethodName = method.getLongName();
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
					            	try {
					            		if (isUnitTestMethod(m.getMethod()) || method == m.getMethod())
					            			return;
					            	} catch (Exception e) {}

					            	String calledMethodName; 
					            	try {
					            		calledMethodName = m.getMethod().getLongName();
					            	} catch (Exception e) {
										calledMethodName = m.getMethodName();
									}
					            	String methodCalledClassName = m.getClassName();
									String calledMethodLibName = "";

									if(libsToClasses.entrySet().stream().anyMatch(map -> map.getValue().contains(methodCallerClassName))) { 
										// ignore Java internal classes, unknown libraries									
										try {
											calledMethodLibName = libsToClasses.entrySet().stream()
											.filter(map -> map.getValue().contains(methodCalledClassName))
											.findAny().orElse(unknownEntry).getKey();
										} catch (NullPointerException e) {
											e.printStackTrace();
										}

										try {	
											if (!callingMethodLibName.equals(unknownEntry.getKey())) {
												if (!calledMethodLibName.equals(unknownEntry.getKey()) && !callingMethodLibName.equals(calledMethodLibName)) {
												// static
												updateInterLibraryCounts(methodCallerClassName+"::"+callingMethodName, callingMethodLibName,
														methodCalledClassName+"::"+calledMethodName, calledMethodLibName, 1, 0);
												}
												// dynamic
												m.replace("{if (instrumentation.CallTrackerTransformer.isStatic(\""+methodCalledClassName+"\", \""+m.getMethodName()+"\")) {"
												+ "if (instrumentation.CallTrackerTransformer.checkAddToDBCondition(\""+callingMethodLibName+"\", \""+calledMethodLibName+"\")) {"
												+ "instrumentation.CallTrackerTransformer.updateInterLibraryCounts(\"" + 
												 methodCallerClassName+"::"+callingMethodName+"\",\"" + 
												 callingMethodLibName+"\", \""+methodCalledClassName+"::"+calledMethodName+"\", \"" + 
												 calledMethodLibName+"\", 0, 1);}}"
												+ " else { "
												+ "if ($0 != null)"
												+ "instrumentation.CallTrackerTransformer.addRuntimeTypeToDB(\""+methodCallerClassName+"\", \""+callingMethodName+
												"\", \""+callingMethodLibName+"\", $0.getClass().getName(), \""+calledMethodName+"\");"
												+ "} $_ = $proceed($$);}");
											}
											
										} catch (Exception e) {
											System.out.println(e);
										}
									}
					            }
					        });
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
	
	public static boolean isUnitTestMethod(CtMethod method) {
		Object[] methodAnnotations = method.getAvailableAnnotations();
		return Arrays.stream(methodAnnotations).map(Object::toString).anyMatch("@org.junit.Test"::equals);
	}
	
	public static boolean isStatic(String methodCalledClassName, String calledMethodName) {
		try { 
			Class<?> c = Class.forName(methodCalledClassName);
			Method me = c.getMethod(calledMethodName, null); 
			if (java.lang.reflect.Modifier.isStatic(me.getModifiers()))
				return true;
			else 
				return false;
		} catch(Exception e) {
		}
		return true;
	}
	
	public static boolean checkAddToDBCondition(String callingMethodLibName, String calledMethodLibName) {
		return !callingMethodLibName.equals("unknownLib") && !calledMethodLibName.equals("unknownLib")
				&& !callingMethodLibName.equals(calledMethodLibName);
	}
	
	public static void addRuntimeTypeToDB(String methodCallerClassName, String callingMethodName, String callingMethodLibName,
			String methodCalledClassName, String calledMethodName) {
		String calledMethodLibName = "";
		Entry<String, List<String>> unknownEntry = new AbstractMap.SimpleEntry<String, List<String>>("unknownLib", new ArrayList<String>());
		try {
			calledMethodLibName = libsToClasses.entrySet().stream()
			.filter(map -> map.getValue().contains(methodCalledClassName))
			.findAny().orElse(unknownEntry).getKey();
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
		if (!callingMethodLibName.equals(unknownEntry.getKey()) && !calledMethodLibName.equals(unknownEntry.getKey())
				&& !callingMethodLibName.equals(calledMethodLibName)) {
			updateInterLibraryCounts(methodCallerClassName+"::"+callingMethodName, callingMethodLibName,
					methodCalledClassName+"::"+calledMethodName, calledMethodLibName, 0, 1);
		}
	}
	
	public static void updateInterLibraryCounts(String callerMethod, String callerLibrary, String calleeMethod, String calleeLibrary,
			int static_count, int dynamic_count) {
		boolean isPresent = false;
		for (InterLibraryCounts iLibraryCounts : interLibraryCounts) {
			if (iLibraryCounts.callerMethodString.equals(callerMethod) && iLibraryCounts.calleeMethodString.equals(calleeMethod)
					&& iLibraryCounts.callerMethodLibString.equals(callerLibrary) && iLibraryCounts.calleeMethodLibString.equals(calleeLibrary)) {
				iLibraryCounts.staticCount += static_count;
				iLibraryCounts.dynamicCount += dynamic_count;
				isPresent = true;
			}
		}
		if (!isPresent)
			interLibraryCounts.add(new InterLibraryCounts(callerMethod, callerLibrary, calleeMethod, calleeLibrary, static_count, dynamic_count));
	}
}
