package instrumentation;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
//import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;

import db.DatabaseConnector;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

public class CallTrackerTransformer implements ClassFileTransformer {
	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
		DatabaseConnector connector = DatabaseConnector.buildDatabaseConnector();
		connector.connect();
		//libsToPkgs = connector.getLibsToPkgs();

		byte[] byteCode = classfileBuffer;
		try {
			ClassPool classPool = ClassPool.getDefault();
			CtClass ctClass = classPool.makeClass(new ByteArrayInputStream(
					classfileBuffer));
			CtMethod[] methods = ctClass.getDeclaredMethods();
			for (CtMethod method : methods) {
				if (!isNative(method) && !isAbstract(method)) {
					System.out.println("Checking method - "+method.getName());
					method.instrument(
					        new ExprEditor() {
					            public void edit(MethodCall m)
					                          throws CannotCompileException
					            {
					                System.out.println(m.getClassName() + "." + m.getMethodName() + " " + m.getSignature());
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
