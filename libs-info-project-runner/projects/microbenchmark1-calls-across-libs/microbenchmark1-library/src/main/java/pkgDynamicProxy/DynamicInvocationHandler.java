package pkgDynamicProxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class DynamicInvocationHandler implements InvocationHandler {

    private static Logger LOGGER = Logger.getLogger(
      DynamicInvocationHandler.class.getName());

    private final Map<String, Method> methods = new HashMap<>();

    private Object target;

    public DynamicInvocationHandler(Object target) {
        this.target = target;

        for(Method method: target.getClass().getDeclaredMethods()) {
            this.methods.put(method.getName(), method);
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) 
      throws Throwable {
        LOGGER.info("Invoked method: {}"+ method.getName()+target.getClass()+proxy.getClass()+methods.get(method.getName()).toString()+target.toString());

        Object result = methods.get(method.getName()).invoke(target, args);
        LOGGER.info(result.toString());
        return result;
    }
}