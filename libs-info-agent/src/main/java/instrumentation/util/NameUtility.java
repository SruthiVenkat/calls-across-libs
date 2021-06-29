package instrumentation.util;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtPrimitiveType;
import javassist.NotFoundException;
import javassist.bytecode.analysis.Type;

/**
 * Utilities.
 * @author jens dietrich - java.lang.reflect types
 * adapted to javassist types - @author sruthi
 */
public class NameUtility {
    public static String getDescriptor(CtMethod method) {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        try {
			for (CtClass paramType : method.getParameterTypes()) {
			    sb.append(getJVMInternalName(paramType));
			}
	        sb.append(')');
	        sb.append(getJVMInternalName(method.getReturnType()));
	        return sb.toString();
        } catch (NotFoundException e) {
			e.printStackTrace();
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
}