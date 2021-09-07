package instrumentation.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.CtPrimitiveType;
import javassist.NotFoundException;

/**
 * Utilities.
 * @author jens dietrich - java.lang.reflect types
 * adapted to javassist types - @author sruthi
 */
public class NameUtility {
    public static String getDescriptor(CtMethod method) {
        try {
        	StringBuilder sb = new StringBuilder();
            sb.append('(');
			for (CtClass paramType : method.getParameterTypes()) {
			    sb.append(getJVMInternalName(paramType));
			}
	        sb.append(')');
	        sb.append(getJVMInternalName(method.getReturnType()));
	        return sb.toString();
        } catch (NotFoundException e) {
		}
        return "";
    }

    public static String getDescriptor(CtConstructor constructor) {
    	try {
	        StringBuilder sb = new StringBuilder();
	        sb.append('(');
	        for (CtClass paramType : constructor.getParameterTypes()) {
	            sb.append(getJVMInternalName(paramType));
	        }
	        sb.append(')');
	        sb.append('V');
	        return sb.toString();
	    	} catch (NotFoundException e) {
			}
        return "";
    }

    public static String getJVMInternalName(CtClass cl) throws NotFoundException {
		if (cl.isPrimitive()) {
			CtPrimitiveType primCl = (CtPrimitiveType)cl;
			if (primCl==CtClass.voidType)
	            return "V";
	        else if (primCl==CtClass.intType)
	            return "I";
	        else if (primCl==CtClass.byteType)
	            return "B";
	        else if (primCl==CtClass.longType)
	            return "J";
	        else if (primCl==CtClass.doubleType)
	            return "D";
	        else if (primCl==CtClass.floatType)
	            return "F";
	        else if (primCl== CtClass.charType)
	            return "C";
	        else if (primCl==CtClass.shortType)
	            return "S";
	        else if (primCl==CtClass.booleanType)
	            return "Z";
		}
		else if (cl.isArray()) {
            return "[" + getJVMInternalName(cl.getComponentType());
        }
       	return "L" + cl.getName().replace('.','/') + ";";
	}
    
    public static String getDescriptor(Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (Class paramType : method.getParameterTypes()) {
            sb.append(getJVMInternalName(paramType));
        }
        sb.append(')');
        sb.append(getJVMInternalName(method.getReturnType()));
        return sb.toString();
    }

    public static String getDescriptor(Constructor constructor) {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (Class paramType : constructor.getParameterTypes()) {
            sb.append(getJVMInternalName(paramType));
        }
        sb.append(')');
        sb.append('V');
        return sb.toString();
    }
    public static String getJVMInternalName(Class cl) {
        if (cl==Void.TYPE)
            return "V";
        else if (cl==Integer.TYPE)
            return "I";
        else if (cl==Byte.TYPE)
            return "B";
        else if (cl==Long.TYPE)
            return "J";
        else if (cl==Double.TYPE)
            return "D";
        else if (cl==Float.TYPE)
            return "F";
        else if (cl== Character.TYPE)
            return "C";
        else if (cl==Short.TYPE)
            return "S";
        else if (cl==Boolean.TYPE)
            return "Z";
        else if (cl.isArray()) {
            return "[" + getJVMInternalName(cl.getComponentType());
        }
        else return "L" + cl.getName().replace('.','/') + ";";
    }
}