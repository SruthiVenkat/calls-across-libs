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
	public static DatabaseConnector connector;
	public static boolean addedConnectorFieldToClass;

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
			addedConnectorFieldToClass = false;
			System.out.println("------>"+ctClass.getName());
			CtMethod[] methods = ctClass.getDeclaredMethods();
			for (CtMethod method : methods) {
				if (!isNative(method) && !isAbstract(method)) {
					System.out.println("Checking method - "+method.getName());
					Entry<String, List<String>> unknownEntry = new AbstractMap.SimpleEntry<String, List<String>>("unknownLib", new ArrayList<String>());
					String callingMethodName = method.getName();
					String methodCallerClassName = method.getDeclaringClass().getName();
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
									
					                System.out.println(callingMethodName+" -- "+calledMethodName);

					                //m.replace("System.out.println(\"oooolalalalalala"+calledMethodName+"\"); $_ = $proceed($$);");
									
									try {
										calledMethodLibName = libsToClasses.entrySet().stream()
										.filter(map -> map.getValue().contains(methodCalledClassName))
										.findAny().orElse(unknownEntry).getKey();
									} catch (NullPointerException e) {
										e.printStackTrace();
									}

									if (!callingMethodLibName.equals(calledMethodLibName)) {
										/*if (!addedConnectorFieldToClass) {
											ctClass.addField(CtField.make("public static db.DatabaseConnector connector;", ctClass));
											for (CtConstructor constructor : ctClass.getConstructors()) {
												System.out.println("constructor "+constructor.getName());
												//constructor.insertAfter("connector = db.DatabaseConnector.buildDatabaseConnector();\n" + 
													//	"		connector.connect();");
											}
											addedConnectorFieldToClass = true;
										}*/

										m.replace("instrumentation.CallTrackerTransformer.connector.updateCountInCallerCalleeCountTable(\""+methodCallerClassName+"::"+callingMethodName+"\",\""+
												callingMethodLibName+"\", \""+methodCalledClassName+"::"+calledMethodName+"\", \""+
												calledMethodLibName+"\", 1); $_ = $proceed($$);");
									}
					            }
					        });
				}
			}
			byteCode = ctClass.toBytecode();
			ctClass.detach();
			System.out.println("Instrumentation complete.");
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