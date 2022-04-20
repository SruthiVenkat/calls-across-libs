package instrumentation.apisurfaceanalysis;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import instrumentation.util.NameUtility;
import instrumentation.util.TrieUtil;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.Descriptor;
import javassist.bytecode.SignatureAttribute;
import javassist.expr.Cast;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import javassist.expr.MethodCall;
import javassist.expr.NewExpr;
import keys.InterLibraryAnnotationsKey;
import keys.InterLibraryCallsKey;
import keys.InterLibraryClassUsageKey;
import keys.InterLibraryFieldsKey;
import keys.InterLibrarySubtypingKey;
import keys.SPIInfoKey;
import keys.ServicesInfoKey;
import keys.ServicesInfoValue;
import keys.SetAccessibleCallsKey;

public class CallTrackerTransformer implements ClassFileTransformer {
        static Map<String, TrieUtil> libsToClasses = new HashMap<String, TrieUtil>();
        static String runningLibrary;
        static Entry<String, TrieUtil> unknownEntry = new AbstractMap.SimpleEntry<String, TrieUtil>("unknownLib", new TrieUtil());
        static Map<String, String> classesToLibs = new HashMap<String, String>();
        static ConcurrentHashMap<Long, String> reflectiveCaller = new ConcurrentHashMap<Long, String>();
        static ConcurrentHashMap<Long, String> dynProxyCaller = new ConcurrentHashMap<Long, String>();
        static ConcurrentHashMap<Object, String> trackedObjs = new ConcurrentHashMap<Object, String>();
        static ClassLoader classLoader;
        static ClassPool classPool;
        static boolean servicesInfoInitiated = false;
        
        // info that is eventually stored in TSVs
        public static Map<String, HashSet<String>> libsToMethods = new HashMap<String, HashSet<String>>();
        public static ConcurrentHashMap<InterLibraryCallsKey, Integer> interLibraryCalls = new ConcurrentHashMap<InterLibraryCallsKey, Integer>();
        public static ConcurrentHashMap<InterLibraryFieldsKey, Integer> interLibraryFields = new ConcurrentHashMap<InterLibraryFieldsKey, Integer>();
        public static ConcurrentHashMap<InterLibrarySubtypingKey, Integer> interLibrarySubtyping = new ConcurrentHashMap<InterLibrarySubtypingKey, Integer>();
        public static ConcurrentHashMap<InterLibraryAnnotationsKey, Integer> interLibraryAnnotations = new ConcurrentHashMap<InterLibraryAnnotationsKey, Integer>();
        public static Set<InterLibraryClassUsageKey> interLibraryClassUsage = new HashSet<InterLibraryClassUsageKey>();
        public static Set<SetAccessibleCallsKey> setAccessibleCallsInfo = new HashSet<SetAccessibleCallsKey>();
        public static ConcurrentHashMap<ServicesInfoKey, ServicesInfoValue> servicesInfo = new ConcurrentHashMap<ServicesInfoKey, ServicesInfoValue>();
        public static Set<SPIInfoKey> spiInfo = new HashSet<SPIInfoKey>();
        public static Map<String, List<String>> superToSubClasses = new HashMap<String, List<String>>();
       
        // labels for edges
        public static enum Label { CLIENTTOLIB, LIBTOCLIENT, LIBTOLIB, INTRALIB };
        public static EnumMap<Label, String> labelMap = new EnumMap<Label, String>(Label.class);

        public CallTrackerTransformer() {
        		// initialize map for labels
	        	labelMap.put(Label.CLIENTTOLIB, "ClientToLib");
	        	labelMap.put(Label.LIBTOCLIENT, "LibToClient");
	        	labelMap.put(Label.LIBTOLIB, "LibToLib");
	        	labelMap.put(Label.INTRALIB, "IntraLib");
        	
        		// get config file path
                Path configPath = Paths.get(new File(".").getAbsolutePath());
                while (!configPath.endsWith("calls-across-libs"))
                        configPath = configPath.getParent();
                String configPathName = configPath.toString()+"/src/main/resources/config.properties";
                // read mapping of libraries to classes and services info
                try (FileReader input = new FileReader(configPathName)) {
                        Properties prop = new Properties();
                        prop.load(input); 
                        String libsInfoPath = prop.getProperty("libsInfoPath");
                        runningLibrary = prop.getProperty("runningLibrary");
                        String row;
                        BufferedReader reader = new BufferedReader(new FileReader(libsInfoPath));
                        while ((row = reader.readLine()) != null) {
                            String[] data = row.split("\t");
                            if (data.length >= 4) {
                                TrieUtil t = new TrieUtil();
                                for (String str : data[4].split(":")) {
                                	classesToLibs.put(str, data[0]);
                                	t.insert(str);
                                }
                                libsToClasses.put(data[0], t);
                            }
                        }
                        reader.close();
                        String servicesInfoPath = prop.getProperty("servicesInfoPath");
                        reader = new BufferedReader(new FileReader(servicesInfoPath));

                        while ((row = reader.readLine()) != null) {
                                String[] data = row.split("\t");
                                if (CallTrackerTransformer.findLibrary(data[0]).equals(unknownEntry.getKey())) {
                                        String packageName = "";
                                        try {
                                                Class<?> klass = Class.forName(data[0]);
                                                packageName = klass.getPackage().getName();
                                                
                                        } catch (Exception e) {
                                                packageName = data[0].lastIndexOf(".") > 0 ? data[0].substring(0, data[0].lastIndexOf(".")) : data[0];
                                        }
                                        libsToClasses.put(packageName, new TrieUtil());
                                }
                                
                                ServicesInfoKey servicesInfoKey = new ServicesInfoKey(data[0], findLibrary(data[0]));
                                servicesInfo.putIfAbsent(servicesInfoKey, 
                                		new ServicesInfoValue(new HashMap<String, String>(), new HashMap<String, HashSet<CtMethod>>()));
               
                                String[] impls = data[1].split(";");
                                String implName = "", impllib = "";
                                if (impls.length>1) {
                                        implName = impls[0];
                                        impllib = impls[1];
                                }
                                
                                servicesInfo.get(servicesInfoKey).implLibs.put(implName, impllib);
                                servicesInfo.get(servicesInfoKey).implMethodsNotInInterface.put(implName, new HashSet<CtMethod>());            
                        }
                        reader.close();                        
                } catch (IOException ex) {
                    System.out.println(ex);
                }
        }
        
        public static void initiateServicesInfo(ClassPool classPool) {
                if (servicesInfoInitiated) return;
                for (ServicesInfoKey sik: servicesInfo.keySet()) {
                        ServicesInfoValue siv = servicesInfo.get(sik);
                        for (String impl: siv.implMethodsNotInInterface.keySet()) {
                                try {
                                        siv.implMethodsNotInInterface.get(impl)
                                                        .addAll(Arrays.asList(classPool.get(impl).getMethods()));
                                        siv.implMethodsNotInInterface.get(impl)
                                                        .removeAll(Arrays.asList(classPool.get(sik.interfaceName).getMethods()));
                                } catch (Exception e) {
                                }
                        }
                }
                servicesInfoInitiated = true;
        }
        
        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                        ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
                byte[] byteCode = classfileBuffer;
                classLoader = loader;
                try {
                        classPool = ClassPool.getDefault();
                        classPool.appendClassPath(new LoaderClassPath(loader));
                        initiateServicesInfo(classPool);
                        CtClass ctClass = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
                        dynamicAnalysis(ctClass);
                        byteCode = ctClass.toBytecode();
                        ctClass.detach();

                } catch (ClassNotFoundException cnfe) {
                } catch (Throwable ex) {
                        ex.printStackTrace();
                        System.out.println(ex);
                }
                return byteCode;
        }
        
        public static void dynamicAnalysis(CtClass ctClass) throws ClassNotFoundException {
            String methodCallerClassName = ctClass.getName();                       
            final String callingMethodLibName = findLibrary(methodCallerClassName);

            if(callingMethodLibName.equals(unknownEntry.getKey())) {
                    return;
            }

            CtMethod[] methods = ctClass.getDeclaredMethods();
            try {
	            for (CtMethod method : methods) {
	            	handleMethodInvocationsDynamic(method, false, methodCallerClassName, callingMethodLibName);
	            }
	            CtConstructor[] constructors = ctClass.getDeclaredConstructors();
	            for (CtConstructor constructor : constructors) {
	            	handleMethodInvocationsDynamic(constructor, true, methodCallerClassName, callingMethodLibName);
	            }
            } catch (CannotCompileException e) {
			}
		}
        
        public static boolean isNative(CtBehavior method) {
            return Modifier.isNative(method.getModifiers());
        }
        
        public static boolean isAbstract(CtBehavior method) {
                return Modifier.isAbstract(method.getModifiers());
        }
        
        public static void handleMethodInvocationsDynamic(CtBehavior method, boolean isConstructor, String methodCallerClassName, 
                String callingMethodLibName) throws CannotCompileException, ClassNotFoundException {
	        if (!isNative(method) && !isAbstract(method)) {
	            String callingMethodName = method.getName();
	            String callingDescriptorName = isConstructor ? NameUtility.getDescriptor((CtConstructor)method) : NameUtility.getDescriptor((CtMethod)method);
	            int mods = method.getModifiers();
	            String methodVisibility = javassist.Modifier.isPublic(mods) ? "public" : (javassist.Modifier.isPrivate(mods) ? "private" : (javassist.Modifier.isProtected(mods) ? "protected" : "default"));
	            int callerClassMods = method.getDeclaringClass().getModifiers();
	            String callerClassVisibility = javassist.Modifier.isPublic(callerClassMods) ? "public" : (javassist.Modifier.isPrivate(callerClassMods) ? "private" : (javassist.Modifier.isProtected(callerClassMods) ? "protected" : "default"));
	                
	            // reflective calls
	            method.insertBefore("{instrumentation.apisurfaceanalysis.CallTrackerTransformer.getStackTrace(\""+methodCallerClassName+"::"+callingMethodName+callingDescriptorName+"\", \""+callingMethodLibName+"\", \""+methodVisibility+"\", \""+callerClassVisibility+"\");}");
	
	             method.instrument(
	                new ExprEditor() {
	                	// 1. Method Invocations
	                    public void edit(MethodCall m) throws CannotCompileException {
	                        try {
	                                if (method == m.getMethod() || isNative(m.getMethod()))
	                                        return;
	                               
	                                String calledMethodName = m.getMethodName();
	                                String methodCalledClassName = m.getClassName();
	                                String calledDescriptorName = NameUtility.getDescriptor(m.getMethod());
	                                String calledMethodLibName = findLibrary(methodCalledClassName);
	
	                                // ignore methods that are not part of the API
	            	                try {
	            	                	CtClass superCls = method.getDeclaringClass().getSuperclass();
	            	                	CtMethod overriddenMethod = null;
	            						if (superCls == null) {
	            							overriddenMethod = classPool.get("java.lang.Object").getDeclaredMethod(calledMethodName);
	            						} else {
	            							try {
	            								overriddenMethod = superCls.getDeclaredMethod(calledMethodName);
	            							} catch (NotFoundException e) {
	            								// superclass does not have the method
											}
	            						}
	            						if (overriddenMethod!=null && overriddenMethod.getDeclaringClass().getName().startsWith("java.")) {
	            							return;
	            						}
	            					} catch (NotFoundException e1) { }
	                               
	            	                
	            	                // reflection and dynamic proxies
	                                if (calledMethodName.equals("invoke") && methodCalledClassName.equals("java.lang.reflect.Method")) {
	                                        boolean invocationHandler = Arrays.stream(method.getDeclaringClass().getInterfaces()).anyMatch(impl -> impl.getName().equals("java.lang.reflect.InvocationHandler"));
	                                        if (invocationHandler) {
	                                                // dynamic proxy
	                                                m.replace("{if ($0 != null) instrumentation.apisurfaceanalysis.CallTrackerTransformer.updateDynamicProxyCaller($0); $_ = $proceed($$);}");
	                                                return;
	                                        } else {
	                                                // reflective call
	                                                m.replace("{if ($0 != null) instrumentation.apisurfaceanalysis.CallTrackerTransformer.updateReflectiveCaller(Thread.currentThread().getId(), \""+callingMethodName+callingDescriptorName+"\", \""+methodCallerClassName+"\", $0); $_ = $proceed($$);}");
	                                        }
	                                }
	                
	                                // reflective field access
	                                if (methodCalledClassName.equals("java.lang.reflect.Field")) {
	                                        if (calledMethodName.equals("set")) {
	                                                m.replace("{if ($0 != null && $1 != null) instrumentation.apisurfaceanalysis.CallTrackerTransformer.handleFieldSetGet($0, $1, \""+callingMethodLibName+"\", \""+methodCallerClassName+"\"); $_ = $proceed($$);}");
	                                        }
	                                        else if (calledMethodName.equals("get"))
	                                                m.replace("{if ($0 != null && $1 != null) instrumentation.apisurfaceanalysis.CallTrackerTransformer.handleFieldSetGet($0, $1, \""+callingMethodLibName+"\", \""+methodCallerClassName+"\"); $_ = $proceed($$);}");
	                                }
	                                
	                                // setAccessible
	                                if (calledMethodName.equals("setAccessible") && methodCalledClassName.startsWith("java.lang.reflect")) {
	                                        m.replace("{if ($0 != null) instrumentation.apisurfaceanalysis.CallTrackerTransformer.handleSetAccessible($0, $1, \""+callingMethodLibName+"\", \""+methodCallerClassName+"::"+callingMethodName+callingDescriptorName+"\"); $_ = $proceed($$);}");
	                                }
	                                
	                                // ServiceLoader pattern
	                                if (calledMethodName.equals("load") && methodCalledClassName.contains("Service") && methodCalledClassName.contains("Loader")) {
	                                        m.replace("{$_ = $proceed($$); if ($_ != null && $1!=null) instrumentation.apisurfaceanalysis.CallTrackerTransformer.handleServiceLoaderPattern($_, $1);}");
	                                        return;
	                                }
	                                
	                                if (servicesInfo.values().stream().anyMatch(key -> key.implLibs.containsKey(methodCalledClassName))) {
	                                        ServicesInfoKey servicesInfoKey = servicesInfo.entrySet().stream().parallel()
	                                                      .filter(entry -> entry.getValue().implLibs.containsKey(methodCalledClassName))
	                                                      .map(Map.Entry::getKey).findAny().orElseGet(null);
	                                        if (servicesInfo.get(servicesInfoKey).implMethodsNotInInterface.get(methodCalledClassName).contains(m.getMethod())) { // !callingMethodLibName.equals(calledMethodLibName) && 
	                                                SPIInfoKey spiInfoKey = new SPIInfoKey(methodCallerClassName+"::"+callingMethodName+callingDescriptorName, callingMethodLibName, servicesInfoKey.interfaceName, servicesInfoKey.interfaceLib, methodCalledClassName+"::"+calledMethodName+calledDescriptorName, methodCalledClassName, calledMethodLibName);
	                                                spiInfo.add(spiInfoKey);
	                                        }
	                                }
	                                
	                                // class usage - Class.forName, ClassLoader.loadClass
	                                if ((calledMethodName.equals("forName") && methodCalledClassName.equals("java.lang.Class")) 
	                                                || (calledMethodName.equals("loadClass") && methodCalledClassName.equals("java.lang.ClassLoader"))) {
	                                        m.replace("{if ($1 != null) instrumentation.apisurfaceanalysis.CallTrackerTransformer.handleClassForNameUsage($1, \""+callingMethodLibName+"\", \""+methodCallerClassName+"\"); $_ = $proceed($$);}");
	                                        return;
	                                }
	                                                
	                                // method invocations
	                                if(!calledMethodLibName.equals(unknownEntry.getKey())) {
	                                        int modifiers = m.getMethod().getModifiers();
	                                        String mVisibility = javassist.Modifier.isPublic(modifiers) ? "public" : (javassist.Modifier.isPrivate(modifiers) ? "private" : (javassist.Modifier.isProtected(modifiers) ? "protected" : "default"));
	                                        String codeToAdd = "";
	                                        if (libsToClasses.get(runningLibrary)!=null
	                                            && libsToClasses.get(runningLibrary).containsNode(methodCallerClassName) 
	                                            && libsToClasses.get(runningLibrary).containsNode(methodCalledClassName)
	                                            && (javassist.Modifier.isPublic(modifiers) || javassist.Modifier.isProtected(modifiers))) {
	                                                // static
	                                                updateLibsToMethods(runningLibrary, methodCalledClassName, calledMethodName+calledDescriptorName);
	                                                // dynamic
	                                                if (!Modifier.isStatic(modifiers))
	                                                        codeToAdd = "if ($0 != null) instrumentation.apisurfaceanalysis.CallTrackerTransformer.updateLibsToMethods(\""+runningLibrary+"\", $0.getClass().getName(), \""+ calledMethodName+calledDescriptorName + "\");";
	                                        }
	                                        
	                                        String label = callingMethodLibName.equals(calledMethodLibName) ? labelMap.get(Label.INTRALIB) : (callingMethodLibName.equals(runningLibrary) ? labelMap.get(Label.CLIENTTOLIB)
	                                        				: (calledMethodLibName.equals(runningLibrary) ? labelMap.get(Label.LIBTOCLIENT) : labelMap.get(Label.LIBTOLIB)));
	
	                                        
	                                        // dynamic
	                                        if (Modifier.isStatic(m.getMethod().getModifiers())) {
	                                                m.replace("{if (instrumentation.apisurfaceanalysis.CallTrackerTransformer.checkAddCondition(\""+callingMethodLibName+"\", \""+calledMethodLibName+"\")) {"
	                                                        + "instrumentation.apisurfaceanalysis.CallTrackerTransformer.updateInterLibraryCounts(\"" + methodCallerClassName+"::"+callingMethodName+callingDescriptorName+"\",\"" + 
	                                                        callingMethodLibName+"\", \""+mVisibility+"\", \""+methodCalledClassName+"::"+calledMethodName+calledDescriptorName+"\", \"" + methodCalledClassName+"::"+calledMethodName+calledDescriptorName
	                                                        +"\", \"" + calledMethodLibName+"\", \"" + calledMethodLibName+"\", \""+callerClassVisibility+"\", 1, false, false, \""+label+"\");} $_ = $proceed($$);}");
	                                        } else {
	                                                m.replace("{if ($0 != null)  { " + codeToAdd 
	                                                        + "instrumentation.apisurfaceanalysis.CallTrackerTransformer.addRuntimeType(\""+methodCallerClassName+"\", \""+callingMethodName+callingDescriptorName+
	                                                        "\", \""+methodCalledClassName+"::"+calledMethodName+calledDescriptorName+"\", \""+callingMethodLibName+"\", $0.getClass(), \""
	                                                        + calledMethodName+calledDescriptorName+"\", \""+mVisibility+"\", \""+calledMethodLibName+"\", $0);" 
	                                                        + "} $_ = $proceed($$);}");
	                                        }
	                                	} 
	                                } catch (NotFoundException nfe) {
	                                        System.out.println("Not Found "+nfe);
	                                } catch (CannotCompileException cce) {
	                                        System.out.println("Cannot Compile "+cce);
	                                } catch (Exception e) {
	                                        System.out.println("Exception while instrumenting "+e);
	                                        e.printStackTrace();
	                                }
	                    }

	                    // 2. Field Accesses
	                    public void edit(FieldAccess f) throws CannotCompileException {
	                        try {
	                                String fieldClass = f.getClassName();
	                                String fieldLib = findLibrary(fieldClass);
	
	                                SignatureAttribute sa = (SignatureAttribute) f.getField().getFieldInfo().getAttribute(SignatureAttribute.tag);
	                                String sig;
	                                try {
	                                        sig = (sa == null) ? f.getSignature() : SignatureAttribute.toFieldSignature(sa.getSignature()).toString();
	                                } catch (BadBytecode e) {
	                                        sig = (sa == null) ? f.getSignature() : sa.getSignature();
	                                }
	                                try {
	                                        sig = Descriptor.toClassName(sig);
	                                } catch (Exception e) {}
	                                                
	                                int mods = f.getField().getModifiers();
	                                String fieldVisibility = javassist.Modifier.isPublic(mods) ? "public" : (javassist.Modifier.isPrivate(mods) ? "private" : (javassist.Modifier.isProtected(mods) ? "protected" : "default"));
	                                if (!fieldLib.equals(unknownEntry.getKey())) { //  && !callingMethodLibName.equals(fieldLib)
	                                	// dynamic
	                                	if (!f.getField().getName().startsWith("this")) {
		                                	f.replace("{ if ($0 != null) { "
		                                			+ "instrumentation.apisurfaceanalysis.CallTrackerTransformer.handleFields(\""+methodCallerClassName+"\", \""+callingMethodLibName+"\", \""
		                                		+fieldClass+"\", $0.getClass().getName(), \""+f.getField().getName()+"\", \""+sig+"\", "+f.isStatic()+", \""+fieldVisibility+"\", \""+fieldLib+"\");} $_ = $proceed($$);}");
	                                	} else {
	                                		f.replace("{ if ($0 != null) { "
		                                			+ "instrumentation.apisurfaceanalysis.CallTrackerTransformer.handleFields(\""+methodCallerClassName+"\", \""+callingMethodLibName+"\", \""
		                                		+fieldClass+"\", \""+methodCallerClassName+"\", \""+f.getField().getName()+"\", \""+sig+"\", "+f.isStatic()+", \""+fieldVisibility+"\", \""+fieldLib+"\");} $_ = $proceed($$);}");
	                                	}
	                                }
	                        } catch (NotFoundException e) {
	                                System.out.println(e);
	                        }
	                    } 
	                    
	                    // instantiations
	                    public void edit(NewExpr newExpr) throws CannotCompileException {                        
	                    	if (servicesInfo.values().stream().anyMatch(key -> key.implLibs.containsKey(newExpr.getClassName()))) {
	                                newExpr.replace("{$_ = $proceed($$); if ($_ != null) instrumentation.apisurfaceanalysis.CallTrackerTransformer.trackInstantiationsAndCasts($_, \"instantiation\");}");
	                        }
	                    }
	                    
	                    // casts
	                    public void edit(Cast c) throws CannotCompileException {
	                                if (servicesInfo.values().stream().anyMatch(key -> {
	                                                                try {
	                                                                        return key.implLibs.containsKey(c.getType().getName());
	                                                                } catch (NotFoundException e) {
	                                                                        return false;
	                                                                }
	                                                })) {
	                                        c.replace("{$_ = $proceed($$); if ($_ != null) instrumentation.apisurfaceanalysis.CallTrackerTransformer.trackInstantiationsAndCasts($_, \"cast\");}");
	                                }
	                    }                       
	                });
	        }
        }

        public static boolean checkAddCondition(String callingMethodLibName, String calledMethodLibName) {
                return !callingMethodLibName.equals("unknownLib") && !calledMethodLibName.equals("unknownLib");// && !callingMethodLibName.equals(calledMethodLibName);
        }
        
        public static void addRuntimeType(String methodCallerClassName, String callingMethodName, String virtualCalleeMethod, 
                        String callingMethodLibName, Class<?> actualMethodCalledClass, String actualCalledMethodName, String calleeVisibility, 
                        String virtualMethodLibName, Object obj) {
                String actualCalledMethodLibName = "";
                String actualMethodCalledClassName = actualMethodCalledClass.getName();
                int mods = actualMethodCalledClass.getModifiers();
                String actualMethodCalledClassVisibility = Modifier.isPublic(mods) ? "public" : (Modifier.isPrivate(mods) ? "private" : (Modifier.isProtected(mods) ? "protected" : "default"));
                
                try {
                        actualCalledMethodLibName = findLibrary(actualMethodCalledClassName);
                } catch (NullPointerException e) {
                        e.printStackTrace();
                }
                
                if (actualMethodCalledClassName.startsWith("com.sun.proxy.$Proxy"))
                        instrumentation.apisurfaceanalysis.CallTrackerTransformer.dynProxyCaller.put(Thread.currentThread().getId(), virtualCalleeMethod);

                String serviceBypass = callingMethodLibName.equals(actualCalledMethodLibName) ? labelMap.get(Label.INTRALIB) : (callingMethodLibName.equals(runningLibrary) ? labelMap.get(Label.CLIENTTOLIB)
        				: (actualCalledMethodLibName.equals(runningLibrary) ? labelMap.get(Label.LIBTOCLIENT) : labelMap.get(Label.LIBTOLIB)));
         
                if (!callingMethodName.equals("hashCode()I") 
                                && !callingMethodName.equals("equals(Ljava/lang/Object;)Z")) {
		                Object trackedObj = instrumentation.apisurfaceanalysis.CallTrackerTransformer.trackedObjs.keySet().stream().filter(o -> o.equals(obj))
		                                .findAny().orElse(null);
		                if (trackedObj!=null) {
		                        if (virtualCalleeMethod.equals(actualMethodCalledClassName+"::"+actualCalledMethodName))
		                        serviceBypass = trackedObjs.get(trackedObj)+","+labelMap.get(Label.CLIENTTOLIB);
		                }
                }

                if (checkAddCondition(callingMethodLibName, actualCalledMethodLibName)) {
                        updateInterLibraryCounts(methodCallerClassName+"::"+callingMethodName, callingMethodLibName, calleeVisibility, virtualCalleeMethod,
                                        actualMethodCalledClassName+"::"+actualCalledMethodName, virtualMethodLibName, actualCalledMethodLibName, actualMethodCalledClassVisibility, 1, false, false, serviceBypass);
                }
        }
        
        public static void updateReflectiveCaller(long threadID, String callerMethodName, String callerClassName, Object invokedOn) {
        		String serviceBypass = "";
                if (servicesInfo.values().stream().anyMatch(key -> key.implLibs.containsKey(((Method)invokedOn).getDeclaringClass().getName())))
                serviceBypass = "reflection,"+labelMap.get(Label.LIBTOCLIENT);
                instrumentation.apisurfaceanalysis.CallTrackerTransformer.reflectiveCaller.put(threadID, callerMethodName+":"+callerClassName+"\t"+serviceBypass);
        }
        
        public static void updateDynamicProxyCaller(Object calledMethodObj) {
                Method calledMethod = (Method)calledMethodObj;
                String calleeMethodName = calledMethod.getDeclaringClass()+"::"+calledMethod.getName()+NameUtility.getDescriptor(calledMethod);
                int mods = calledMethod.getModifiers();
                String calleeVisibility = Modifier.isPublic(mods) ? "public" : (Modifier.isPrivate(mods) ? "private" : (Modifier.isProtected(mods) ? "protected" : "default"));
                StackTraceElement[] stes = Thread.currentThread().getStackTrace();
                String className = stes[4].getClassName();
                String methodName = stes[4].getMethodName();
                String descriptor = "";
                try {descriptor = NameUtility.getDescriptor(Class.forName(className, false, classLoader).getMethod(methodName));} catch (Exception e) { }
                String calledMethodLibName = findLibrary(calledMethod.getDeclaringClass().getName());

                String virtualCalleeMethod = "";
                long currentThreadID = Thread.currentThread().getId();
                if (instrumentation.apisurfaceanalysis.CallTrackerTransformer.dynProxyCaller.containsKey(currentThreadID))
                        virtualCalleeMethod = instrumentation.apisurfaceanalysis.CallTrackerTransformer.dynProxyCaller.get(currentThreadID);

                int calleeClassMods = calledMethod.getDeclaringClass().getModifiers();
                String calleeClassVisibility = Modifier.isPublic(calleeClassMods) ? "public" : (Modifier.isPrivate(calleeClassMods) ? "private" : (Modifier.isProtected(calleeClassMods) ? "protected" : "default"));
                
                String callerLib = findLibrary(stes[4].getClassName());
                String label = callerLib.equals(calledMethodLibName) ? labelMap.get(Label.INTRALIB) : (callerLib.equals(runningLibrary) ? labelMap.get(Label.CLIENTTOLIB)
        				: (calledMethodLibName.equals(runningLibrary) ? labelMap.get(Label.LIBTOCLIENT) : labelMap.get(Label.LIBTOLIB)));

                instrumentation.apisurfaceanalysis.CallTrackerTransformer.dynProxyCaller.remove(currentThreadID);
                updateInterLibraryCounts(className+"::"+methodName+descriptor, callerLib, calleeVisibility, virtualCalleeMethod, calleeMethodName, calledMethodLibName, calledMethodLibName, calleeClassVisibility, 1, false, true, label);
        }
        
        public static void handleSetAccessible(Object calledOn, boolean setTo, String callerLib, String callerMethod) {
                String vis="", calledOnObjName="", libName="", sig="";
                if (calledOn.getClass().getName().equals("java.lang.reflect.Method")) {
                        Method calledOnMethod = (Method)calledOn;
                        int mod = calledOnMethod.getModifiers();
                        vis = Modifier.isPublic(mod) ? "public" : (Modifier.isPrivate(mod) ? "private" : (Modifier.isProtected(mod) ? "protected" : "default"));
                        calledOnObjName = calledOnMethod.getDeclaringClass().getName()+"::"+calledOnMethod.getName()+NameUtility.getDescriptor(calledOnMethod);
                        libName = findLibrary(calledOnMethod.getDeclaringClass().getName());
                } else if (calledOn.getClass().getName().equals("java.lang.reflect.Constructor")) {
                        Constructor<?> calledOnConstructor = (Constructor<?>)calledOn;
                        int mod = calledOnConstructor.getModifiers();
                        vis = Modifier.isPublic(mod) ? "public" : (Modifier.isPrivate(mod) ? "private" : (Modifier.isProtected(mod) ? "protected" : "default"));
                        calledOnObjName = calledOnConstructor.getDeclaringClass().getName()+"::"+calledOnConstructor.getName()+NameUtility.getDescriptor(calledOnConstructor);
                        libName = findLibrary(calledOnConstructor.getDeclaringClass().getName());
                } else if (calledOn.getClass().getName().equals("java.lang.reflect.Field")) {
                        Field calledOnField = (Field)calledOn;
                        int mod = calledOnField.getModifiers();
                        vis = Modifier.isPublic(mod) ? "public" : (Modifier.isPrivate(mod) ? "private" : (Modifier.isProtected(mod) ? "protected" : "default"));
                        calledOnObjName = calledOnField.getDeclaringClass().getName()+"::"+calledOnField.getName();
                        sig = calledOnField.getGenericType().getTypeName();
                        libName = findLibrary(calledOnField.getDeclaringClass().getName());
                }
                SetAccessibleCallsKey setAccessibleCallsKey = new SetAccessibleCallsKey(callerLib, callerMethod, calledOn.getClass().getName(), vis, sig, calledOnObjName, libName, setTo);
                setAccessibleCallsInfo.add(setAccessibleCallsKey);
        }
        
        public static void trackInstantiationsAndCasts(Object obj, String typeString) {
                try {
                        instrumentation.apisurfaceanalysis.CallTrackerTransformer.trackedObjs.put(obj, typeString);
                } catch (NullPointerException npe) {}
        }
        
        public static void handleServiceLoaderPattern(Object returnedObj, Object loadedInterface) {
                try {
                        String interfaceName = loadedInterface.toString().split(" ")[1];
                        // interface methods
                        HashSet<CtMethod> interfaceMethods = new HashSet<CtMethod>(Arrays.asList(classPool.get(interfaceName).getMethods()));
                        // impl methods
                        HashMap<String, HashSet<CtMethod>> implsToMethods = new HashMap<String, HashSet<CtMethod>>();
                        HashMap<String, String> implsToLibs = new HashMap<String, String>();
                        for (Object object : (Iterable<?>)returnedObj) {
                                String implName = object.getClass().toString().split(" ")[1];
                                HashSet<CtMethod> implMethods = new HashSet<CtMethod>(Arrays.asList(classPool.get(implName).getMethods()));
                                implMethods.removeAll(interfaceMethods);
                                implsToLibs.put(implName, findLibrary(implName));
                                implsToMethods.put(implName, implMethods);
                        }
                        ServicesInfoKey servicesInfoKey = new ServicesInfoKey(interfaceName, findLibrary(interfaceName));
                        if (!servicesInfo.containsKey(servicesInfoKey)) {
                                ServicesInfoValue servicesInfoValue = new ServicesInfoValue(implsToLibs, implsToMethods);
                                servicesInfo.put(servicesInfoKey, servicesInfoValue);
                        } else {
                                for (String impl: implsToMethods.keySet())
                                	if (implsToMethods.containsKey(impl) && implsToMethods.get(impl)!=null
                                			&& servicesInfo.get(servicesInfoKey).implMethodsNotInInterface.get(impl)!=null) {
                                		servicesInfo.get(servicesInfoKey).implMethodsNotInInterface.get(impl)
                                        .addAll(implsToMethods.get(impl));
                                	}
                                        
                        }
                } catch (NotFoundException e) {
                        System.out.println(e);
                }
        }
        
        public static void updateLibsToMethods(String calledMethodLibName, String methodCalledClassName, String calledMethodName) {
                        String calledMethodFullName = methodCalledClassName+"::"+calledMethodName;
                if (!libsToMethods.containsKey(calledMethodLibName))
                        libsToMethods.put(calledMethodLibName, new HashSet<String>(Arrays.asList(calledMethodFullName)));
                libsToMethods.get(calledMethodLibName).add(calledMethodFullName);
        }
        
        public static void updateInterLibraryCounts(String callerMethod, String callerLibrary, String calleeVisibility, String virtualCalleeMethod, String actualCalleeMethod, 
                        String virtualCalleeMethodLibString, String actualCalleeMethodLibString, String calleeClassVis, int count, boolean reflective, boolean dynamicProxy, String serviceBypass) {
                InterLibraryCallsKey key = new InterLibraryCallsKey(callerMethod, callerLibrary, calleeVisibility, virtualCalleeMethod, virtualCalleeMethodLibString,
                                actualCalleeMethod, actualCalleeMethodLibString, reflective, dynamicProxy, serviceBypass);
                interLibraryCalls.putIfAbsent(key, 0);
                interLibraryCalls.put(key, interLibraryCalls.get(key) + count);
                InterLibraryClassUsageKey interLibraryClassUsageKey = new InterLibraryClassUsageKey(actualCalleeMethod.split("::")[0], calleeClassVis, actualCalleeMethodLibString, "method call", callerMethod.split("::")[0], callerLibrary);
                interLibraryClassUsage.add(interLibraryClassUsageKey);
        }
        
        public static void getStackTrace(String calleeMethodName, String calleeLib, String calleeVisibility, String callerClassVisibility) {
        		if (instrumentation.apisurfaceanalysis.CallTrackerTransformer.reflectiveCaller.isEmpty())
        			return;
        		long currentThreadID = Thread.currentThread().getId();
                if (!instrumentation.apisurfaceanalysis.CallTrackerTransformer.reflectiveCaller.containsKey(currentThreadID))
                    return;
                
                StackTraceElement[] stes = Thread.currentThread().getStackTrace();
                for (int i=2; i<stes.length; i++) {
                	if (stes[i].getMethodName().equals("invoke0")) {
                		String calleeMethodNameString = calleeMethodName.split(":")[2];
                		calleeMethodNameString = calleeMethodNameString.split("\\(")[0];
                		if (!calleeMethodNameString.equals(stes[i-1].getMethodName())) {
                			
                			String className = stes[i-1].getClassName();
                            String methodName = stes[i-1].getMethodName();
                            String descriptor = "";
                            try {descriptor = NameUtility.getDescriptor(Class.forName(className, false, classLoader).getMethod(methodName));} catch (Exception e) { }
                            calleeMethodName = className+"::"+methodName+descriptor;
                            calleeLib = findLibrary(className);
                            try {
                            	int calleeClassMods = Class.forName(className, false, ClassLoader.getSystemClassLoader()).getModifiers();
                                calleeVisibility = Modifier.isPublic(calleeClassMods) ? "public" : (Modifier.isPrivate(calleeClassMods) ? "private" : (Modifier.isProtected(calleeClassMods) ? "protected" : "default"));
                            } catch (Exception e) { }                                  
                		}
                		break;
                	}
                }

                String [] reflData = instrumentation.apisurfaceanalysis.CallTrackerTransformer.reflectiveCaller.get(currentThreadID).split("\t");
                String[] caller = reflData[0].split(":");
                instrumentation.apisurfaceanalysis.CallTrackerTransformer.reflectiveCaller.remove(currentThreadID);
                String callerMethodName = caller[0];
                String callerClassName = caller[1];
                String callerLib = findLibrary(callerClassName);
                String label = callerLib.equals(calleeLib) ? labelMap.get(Label.INTRALIB) : (callerLib.equals(runningLibrary) ? labelMap.get(Label.CLIENTTOLIB)
        				: (calleeLib.equals(runningLibrary) ? labelMap.get(Label.LIBTOCLIENT) : labelMap.get(Label.LIBTOLIB)));

                if (checkAddCondition(callerLib, calleeLib))
                        updateInterLibraryCounts(callerClassName+"::"+callerMethodName, callerLib, calleeVisibility, calleeMethodName, 
                                        calleeMethodName, calleeLib, calleeLib, callerClassVisibility, 1, true, false, reflData.length>1 ? reflData[1]+","+label : label);
        }
        
        public static void handleFieldSetGet(Object field, Object actualObj, String callingMethodLibName, String callingClass) {
                java.lang.reflect.Field f = (java.lang.reflect.Field) field;
                String[] fieldData = field.toString().split(" ");
                String fieldClass = fieldData[fieldData.length-1].substring(0, fieldData[fieldData.length-1].lastIndexOf("."));
                String fieldLib = findLibrary(fieldClass);

                int mods = f.getModifiers();
                String fieldVisibility = javassist.Modifier.isPublic(mods) ? "public" : (javassist.Modifier.isPrivate(mods) ? "private" : (javassist.Modifier.isProtected(mods) ? "protected" : "default"));
                InterLibraryFieldsKey key = new InterLibraryFieldsKey(callingClass, callingMethodLibName, fieldClass, actualObj.getClass().getName(), actualObj.getClass().getName()+"::"+f.getName(), 
                                f.getGenericType().getTypeName(), javassist.Modifier.isStatic(mods), fieldVisibility, fieldLib, true);
                interLibraryFields.putIfAbsent(key, 0);
                interLibraryFields.put(key, interLibraryFields.get(key) + 1);
        }
        
        public static void handleFields(String methodCallerClassName, String callingMethodLibName, String fieldClass, String actualClass, String fieldName, String sig, boolean isStatic, String fieldVisibility, String fieldLib) {
                InterLibraryFieldsKey key = new InterLibraryFieldsKey(methodCallerClassName, callingMethodLibName, fieldClass, actualClass, actualClass+"::"+fieldName, sig, isStatic, fieldVisibility, fieldLib, false);
                interLibraryFields.putIfAbsent(key, 0);
                interLibraryFields.put(key, interLibraryFields.get(key) + 1);
        }
        
        public static void handleClassForNameUsage(String classname, String callingLib, String methodCallerClassName) {
                Class<?> c;
                try {
                        c = ClassLoader.getSystemClassLoader().loadClass(classname);
                        int mods = c.getModifiers();
                        String classVisibility = Modifier.isPublic(mods) ? "public" : (Modifier.isPrivate(mods) ? "private" : (Modifier.isProtected(mods) ? "protected" : "default"));
                        InterLibraryClassUsageKey interLibraryClassUsageKey = new InterLibraryClassUsageKey(classname, classVisibility, findLibrary(classname), "Class.forName", methodCallerClassName, callingLib);
                        interLibraryClassUsage.add(interLibraryClassUsageKey);
                } catch (ClassNotFoundException e) {
                }
        }
        
        public static String findLibrary(String className) {
                if (!classesToLibs.containsKey(className)) {
                        String libName = libsToClasses.entrySet().stream().parallel()
                                        .filter(map -> map.getValue().containsNode(className))
                                        .findAny().orElse(unknownEntry).getKey();
                        classesToLibs.put(className, libName);
                }
                if (classesToLibs.get(className).equals(unknownEntry.getKey())) {
                        if (className.lastIndexOf(".") > 0) {
                                String pkgName = className.substring(0, className.lastIndexOf("."));
                                if (libsToClasses.containsKey(pkgName)) {
                                        libsToClasses.get(pkgName).insert(className);
                                        classesToLibs.put(className, pkgName);
                                }
                        }
                }       
                return classesToLibs.get(className);
        }
}
