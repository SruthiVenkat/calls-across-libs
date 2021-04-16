package instrumentation;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
//import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.Modifier;

public class CallTrackerTransformer implements ClassFileTransformer {
	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
		byte[] byteCode = classfileBuffer;
		try {
			ClassPool classPool = ClassPool.getDefault();
			CtClass ctClass = classPool.makeClass(new ByteArrayInputStream(
					classfileBuffer));
			CtMethod[] methods = ctClass.getDeclaredMethods();
			for (CtMethod method : methods) {
				if (!isNative(method)) {
					System.out.println(method.getName());
					//method.insertBefore("abc();");
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
	
	public void abc() {
		System.out.println("hi there");
	}
	
	public static boolean isNative(CtMethod method) {
	    return Modifier.isNative(method.getModifiers());
	}

}
