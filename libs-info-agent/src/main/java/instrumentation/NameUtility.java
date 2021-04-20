package instrumentation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Utilities.
 * @author jens dietrich
 */
public class NameUtility {
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