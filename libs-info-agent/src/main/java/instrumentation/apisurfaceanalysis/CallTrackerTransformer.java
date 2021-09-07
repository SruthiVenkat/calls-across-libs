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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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

public class CallTrackerTransformer implements ClassFileTransformer {
	public static Map<String, TrieUtil> libsToClasses = new HashMap<String, TrieUtil>();
	public static String runningLibrary; 
	public static Map<String, HashSet<String>> libsToMethods = new HashMap<String, HashSet<String>>();
	public static ConcurrentHashMap<InterLibraryCallsKey, Integer> interLibraryCalls = new ConcurrentHashMap<InterLibraryCallsKey, Integer>();
	public static ConcurrentHashMap<InterLibraryFieldsKey, Integer> interLibraryFields = new ConcurrentHashMap<InterLibraryFieldsKey, Integer>();
	public static Set<InterLibrarySubtypingKey> interLibrarySubtyping = new HashSet<InterLibrarySubtypingKey>();
	public static Set<InterLibraryAnnotationsKey> interLibraryAnnotations = new HashSet<InterLibraryAnnotationsKey>();
	public static Set<SetAccessibleCallsKey> setAccessibleCallsInfo = new HashSet<SetAccessibleCallsKey>();
	public static Entry<String, TrieUtil> unknownEntry = new AbstractMap.SimpleEntry<String, TrieUtil>("unknownLib", new TrieUtil());
	public static Map<String, String> classesToLibs = new HashMap<String, String>();
	public static ConcurrentHashMap<Long, String> reflectiveCaller = new ConcurrentHashMap<Long, String>();
	public static ConcurrentHashMap<Long, String> dynProxyCaller = new ConcurrentHashMap<Long, String>();
	public static Set<ServicesInfoKey> servicesInfo = new HashSet<ServicesInfoKey>();
	public static HashMap<Object, String> trackedObjs = new HashMap<Object, String>();
	public static ClassLoader classLoader;
	public static ClassPool classPool;
	
	public CallTrackerTransformer() {
		Path configPath = Paths.get(new File(".").getAbsolutePath());
		while (!configPath.endsWith("calls-across-libs"))
			configPath = configPath.getParent();
		String configPathName = configPath.toString()+"/src/main/resources/config.properties";
		try (FileReader input = new FileReader(configPathName)) {
			Properties prop = new Properties();
			prop.load(input); 
			String libsInfoPath = prop.getProperty("libsInfoPath");
			runningLibrary = prop.getProperty("runningLibrary");
			String row;
			BufferedReader reader = new BufferedReader(new FileReader(libsInfoPath));
			while ((row = reader.readLine()) != null) {
			    String[] data = row.split(",");
			    if (data.length == 4) {
			    	TrieUtil t = new TrieUtil();
			    	for (String str : data[3].split(":"))
			    		t.insert(str);
			    	libsToClasses.put(data[0], t);
			    }
			}
			reader.close();
			String servicesInfoPath = prop.getProperty("servicesInfoPath");
			reader = new BufferedReader(new FileReader(servicesInfoPath));
			while ((row = reader.readLine()) != null) {
				String[] data = row.split(",");
				if (CallTrackerTransformer.findLibrary(data[0]).equals(unknownEntry.getKey())) {
					try {
						Class<?> klass = Class.forName(data[0]);
						String packageName = klass.getPackage().getName();
						libsToClasses.put(packageName, new TrieUtil());
					} catch (Exception e) {
					}
				}
				HashMap<String, HashSet<CtMethod>> implsToMethods = new HashMap<String, HashSet<CtMethod>>();
				HashMap<String, String> implsToLibs = new HashMap<String, String>();
				for (String implinfo : data[1].split(";")) {
					if (implinfo.contains(":")) {
					String implName = implinfo.split("::")[0];
					String impllib = implinfo.split("::")[1];
					HashSet<CtMethod> implMethods = new HashSet<CtMethod>();
					implsToLibs.put(implName, impllib);
					implsToMethods.put(implName, implMethods);
					}
				}
				
				ServicesInfoKey servicesInfoKey = new ServicesInfoKey(data[0], findLibrary(data[0]), implsToLibs, implsToMethods);
				servicesInfo.add(servicesInfoKey);
			}
			reader.close();
		} catch (IOException ex) {
		    System.out.println(ex);
		}
	}

	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
		byte[] byteCode = classfileBuffer;
		classLoader = loader;
		try {
			classPool = ClassPool.getDefault();
			CtClass ctClass = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));

			String methodCallerClassName = ctClass.getName();			
			final String callingMethodLibName = findLibrary(methodCallerClassName);

			if(callingMethodLibName.equals(unknownEntry.getKey())) {
				return byteCode;
			}

			CtMethod[] methods = ctClass.getDeclaredMethods();
			for (CtMethod method : methods) {
				handleMethodInvocations(method, false, methodCallerClassName, callingMethodLibName);
			}
			CtConstructor[] constructors = ctClass.getDeclaredConstructors();
			for (CtConstructor constructor : constructors) {
				handleMethodInvocations(constructor, true, methodCallerClassName, callingMethodLibName);
			}

			// 3. Subtyping ( extends / implements )
			CtClass superClass = ctClass.getSuperclass();
			if (superClass!=null) {
				String superClassName = superClass.getName();
				String superClassLib = findLibrary(superClassName);
				if (!superClassLib.equals(unknownEntry.getKey()) && !callingMethodLibName.equals(superClassLib)) {
					InterLibrarySubtypingKey key = new InterLibrarySubtypingKey(methodCallerClassName, callingMethodLibName, superClassName, superClassLib);
        			interLibrarySubtyping.add(key);
				}
			}

			CtClass[] interfaces = ctClass.getInterfaces();
			if (interfaces!=null) {
				for (CtClass interfaceClass : interfaces) {
					String interfaceName = interfaceClass.getName();
					String interfaceLib = findLibrary(interfaceName);
					if (!interfaceLib.equals(unknownEntry.getKey()) && !callingMethodLibName.equals(interfaceLib)) {
						InterLibrarySubtypingKey key = new InterLibrarySubtypingKey(methodCallerClassName, callingMethodLibName, interfaceName, interfaceLib);
            			interLibrarySubtyping.add(key);
					}
				}
			}

			// 4. Annotation Usage - class (& method, field)
			Object[] annotations = ctClass.getAnnotations();
			if (annotations!=null) {
				for (Object annotationObj : annotations) {
					String annotationName = annotationObj.toString().substring(1);
					String annotationLib = findLibrary(annotationName);
					if (!annotationLib.equals(unknownEntry.getKey()) && !callingMethodLibName.equals(annotationLib)) {
						InterLibraryAnnotationsKey key = new InterLibraryAnnotationsKey(methodCallerClassName, "-", "-", callingMethodLibName, annotationName, annotationLib);
            			interLibraryAnnotations.add(key);
					}
				}
			}
			byteCode = ctClass.toBytecode();
			ctClass.detach();
		} catch (Throwable ex) {
			ex.printStackTrace();
			System.out.println(ex);
		}
		return byteCode;
	}
	
	public static boolean isNative(CtBehavior method) {
	    return Modifier.isNative(method.getModifiers());
	}
	
	public static boolean isAbstract(CtBehavior method) {
		return Modifier.isAbstract(method.getModifiers());
	}
	
	public static void handleMethodInvocations(CtBehavior method, boolean isConstructor, String methodCallerClassName, 
			String callingMethodLibName) throws CannotCompileException, ClassNotFoundException {
		if (!isNative(method) && !isAbstract(method)) {
			String callingMethodName = method.getName();
			String callingDescriptorName = isConstructor ? NameUtility.getDescriptor((CtConstructor)method) : NameUtility.getDescriptor((CtMethod)method);
			int mods = method.getModifiers();
    		String methodVisibility = javassist.Modifier.isPublic(mods) ? "public" : (javassist.Modifier.isPrivate(mods) ? "private" : (javassist.Modifier.isProtected(mods) ? "protected" : "unknown"));
					
			// reflective calls
			method.insertBefore("{if (!instrumentation.apisurfaceanalysis.CallTrackerTransformer.reflectiveCaller.isEmpty()) instrumentation.apisurfaceanalysis.CallTrackerTransformer.getStackTrace(\""+methodCallerClassName+"::"+callingMethodName+callingDescriptorName+"\", \""+callingMethodLibName+"\", \""+methodVisibility+"\");}");

			// Annotations - methods
			Object[] methodAnnotations = method.getAnnotations();
			if (methodAnnotations!=null) {
					for (Object annotationObj : methodAnnotations) {
						String annotationName = annotationObj.getClass().getName();
						String annotationLib = findLibrary(annotationName);

						if (!annotationLib.equals(unknownEntry.getKey()) && !callingMethodLibName.equals(annotationLib)) {
							InterLibraryAnnotationsKey methodKey = new InterLibraryAnnotationsKey("-", methodCallerClassName+"::"+callingMethodName+callingDescriptorName, "-", callingMethodLibName, annotationName, annotationLib);
	            			interLibraryAnnotations.add(methodKey);
						}
					}
			}

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

								if (calledMethodName.equals("invoke") && methodCalledClassName.equals("java.lang.reflect.Method")) {
									boolean invocationHandler = Arrays.stream(method.getDeclaringClass().getInterfaces()).anyMatch(impl -> impl.getName().equals("java.lang.reflect.InvocationHandler"));
									if (invocationHandler) {
										// dynamic proxy
										m.replace("{if ($0 != null) instrumentation.apisurfaceanalysis.CallTrackerTransformer.updateDynamicProxyCaller($0); $_ = $proceed($$);}");
										return;
									} else {
										// reflective call
										m.replace("{instrumentation.apisurfaceanalysis.CallTrackerTransformer.updateReflectiveCaller(Thread.currentThread().getId(), \""+callingMethodName+callingDescriptorName+"\", \""+methodCallerClassName+"\", $0); $_ = $proceed($$);}");
									}
								}
						
								// reflective field access
								if (methodCalledClassName.equals("java.lang.reflect.Field")) {
									if (calledMethodName.equals("set")) {
										m.replace("{if ($0 != null && $1 != null) instrumentation.apisurfaceanalysis.CallTrackerTransformer.handleFieldSetGet($0, $1, \""+callingMethodLibName+"\"); $_ = $proceed($$);}");
									}
									else if (calledMethodName.equals("get"))
										m.replace("{if ($0 != null && $1 != null) instrumentation.apisurfaceanalysis.CallTrackerTransformer.handleFieldSetGet($0, $1, \""+callingMethodLibName+"\"); $_ = $proceed($$);}");
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
								
								if (servicesInfo.stream().anyMatch(key -> key.implLibs.containsKey(methodCalledClassName))) {
									ServicesInfoKey servicesInfoKey = servicesInfo.stream().parallel().filter(key -> key.implLibs.containsKey(methodCalledClassName))
											.findAny().orElseGet(null);
									if (servicesInfoKey.implMethodsNotInInterface.get(methodCalledClassName).contains(m.getMethod())) {
										SPIInfoKey spiInfoKey = new SPIInfoKey(methodCallerClassName+"::"+callingMethodName+callingDescriptorName, callingMethodLibName, servicesInfoKey.interfaceName, servicesInfoKey.interfaceLib, methodCalledClassName+"::"+calledMethodName+calledDescriptorName, methodCalledClassName, findLibrary(methodCalledClassName));
									}
								}

								if(!calledMethodLibName.equals(unknownEntry.getKey())) {
									int modifiers = m.getMethod().getModifiers();
				            		String mVisibility = javassist.Modifier.isPublic(modifiers) ? "public" : (javassist.Modifier.isPrivate(modifiers) ? "private" : (javassist.Modifier.isProtected(modifiers) ? "protected" : "unknown"));
									String codeToAdd = "";
									
									if (libsToClasses.get(runningLibrary).containsNode(methodCallerClassName) && libsToClasses.get(runningLibrary).containsNode(methodCalledClassName)
									&& (javassist.Modifier.isPublic(modifiers) || javassist.Modifier.isProtected(modifiers))) {
										// static
										updateLibsToMethods(runningLibrary, methodCalledClassName, calledMethodName+calledDescriptorName);
										// dynamic
										if (!Modifier.isStatic(modifiers))
											codeToAdd = "if ($0 != null) instrumentation.apisurfaceanalysis.CallTrackerTransformer.updateLibsToMethods(\""+runningLibrary+"\", $0.getClass().getName(), \""+ calledMethodName+calledDescriptorName + "\");";
									}

									if (Modifier.isStatic(m.getMethod().getModifiers()))
										m.replace("{if (instrumentation.apisurfaceanalysis.CallTrackerTransformer.checkAddCondition(\""+callingMethodLibName+"\", \""+calledMethodLibName+"\")) {"
											+ "instrumentation.apisurfaceanalysis.CallTrackerTransformer.updateInterLibraryCounts(\"" + methodCallerClassName+"::"+callingMethodName+callingDescriptorName+"\",\"" + 
											callingMethodLibName+"\", \""+mVisibility+"\", \""+methodCalledClassName+"::"+calledMethodName+calledDescriptorName+"\", \"" + methodCalledClassName+"::"+calledMethodName+calledDescriptorName
											+"\", \"" + calledMethodLibName+"\", \"" + calledMethodLibName+"\", 1, false, false, \"\");} $_ = $proceed($$);}");
									else {
										m.replace("{if ($0 != null)  { " + codeToAdd 
											+ "instrumentation.apisurfaceanalysis.CallTrackerTransformer.addRuntimeType(\""+methodCallerClassName+"\", \""+callingMethodName+callingDescriptorName+
											"\", \""+methodCalledClassName+"::"+calledMethodName+calledDescriptorName+"\", \""+callingMethodLibName+"\", $0.getClass(), \""
											+ calledMethodName+calledDescriptorName+"\", \""+mVisibility+"\", \""+calledMethodLibName+"\", $0);" 
											+ "} $_ = $proceed($$);}");
									}
									} 
								} catch (Exception e) {
									e.printStackTrace();
									System.out.println(e);
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
			            		String fieldVisibility = javassist.Modifier.isPublic(mods) ? "public" : (javassist.Modifier.isPrivate(mods) ? "private" : (javassist.Modifier.isProtected(mods) ? "protected" : "unknown"));
			            		if (!fieldLib.equals(unknownEntry.getKey()) && !callingMethodLibName.equals(fieldLib)) {
			            			// Annotations - fields
			            			Object[] annotations = f.getField().getAnnotations();
			    					if (annotations!=null) {
			    						for (Object annotationObj : annotations) {
			    							String annotationName = annotationObj.getClass().getName();
			    							String annotationLib = findLibrary(annotationName);
			    							if (!annotationLib.equals(unknownEntry.getKey()) && !fieldLib.equals(annotationLib)) {
			    								InterLibraryAnnotationsKey fieldKey = new InterLibraryAnnotationsKey("-", "-", fieldClass+"::"+f.getField().getName()+":"+f.getSignature(), fieldLib, annotationName, annotationLib);
			    		            			interLibraryAnnotations.add(fieldKey);
			    							}
			    						}
			    					}
			    					
			    					if (f.isStatic()) {
				            			InterLibraryFieldsKey key = new InterLibraryFieldsKey(callingMethodLibName, fieldClass, fieldClass, fieldClass+"::"+f.getField().getName(), sig, f.isStatic(), fieldVisibility, fieldLib, false);
				            			interLibraryFields.putIfAbsent(key, 0);
				            			interLibraryFields.put(key, interLibraryFields.get(key) + 1);
			            			} else f.replace("{ if ($0 != null) instrumentation.apisurfaceanalysis.CallTrackerTransformer.handleFields(\""+callingMethodLibName+"\", \""
				            			+fieldClass+"\", $0.getClass().getName(), \""+f.getField().getName()+"\", \""+sig+"\", "+f.isStatic()+", \""+fieldVisibility+"\", \""+fieldLib+"\"); $_ = $proceed($$);}");
			            		}
							} catch (NotFoundException e) {
								System.out.println(e);
							} catch (ClassNotFoundException e) {
								System.out.println(e);
							}
			            }
			            
			            // instantiations
			            public void edit(NewExpr newExpr) throws CannotCompileException {
			            	if (servicesInfo.stream().anyMatch(key -> key.implLibs.values().contains(findLibrary(newExpr.getClassName())))) {
			            		newExpr.replace("{$_ = $proceed($$); if ($_ != null) instrumentation.apisurfaceanalysis.CallTrackerTransformer.trackInstantiationsAndCasts($_, \"instantiation\");}");
			            	}
			            }
			            
			            // casts
			            public void edit(Cast c) throws CannotCompileException {
			            	if (servicesInfo.stream().anyMatch(key -> {
								try {
									return key.implLibs.values().contains(findLibrary(c.getType().getName()));
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
		return !callingMethodLibName.equals("unknownLib") && !calledMethodLibName.equals("unknownLib") && !callingMethodLibName.equals(calledMethodLibName);
	}
	
	public static void addRuntimeType(String methodCallerClassName, String callingMethodName, String virtualCalleeMethod, 
			String callingMethodLibName, Class<?> actualMethodCalledClass, String actualCalledMethodName, String calleeVisibility, String virtualMethodLibName, Object obj) {
		String actualCalledMethodLibName = "";
		String actualMethodCalledClassName = actualMethodCalledClass.getName();
		try {
			actualCalledMethodLibName = findLibrary(actualMethodCalledClassName);
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
		
		if (actualMethodCalledClassName.startsWith("com.sun.proxy.$Proxy"))
			instrumentation.apisurfaceanalysis.CallTrackerTransformer.dynProxyCaller.put(Thread.currentThread().getId(), virtualCalleeMethod);

		String serviceBypass = "";
		if (!callingMethodName.equals("hashCode()I")) {
    		Object trackedObj = instrumentation.apisurfaceanalysis.CallTrackerTransformer.trackedObjs.keySet().stream().parallel().findAny().orElse(null);
    		if (trackedObj!=null) {
//    			System.out.println(methodCallerClassName+"::"+callingMethodName+"->"+virtualCalleeMethod+" , "+actualCalledMethodName);
//    			System.out.println("tracked-"+virtualCalleeMethod+"--"+actualMethodCalledClassName+"::"+actualCalledMethodName);
    			if (virtualCalleeMethod.equals(actualMethodCalledClassName+"::"+actualCalledMethodName))
    			serviceBypass = trackedObjs.get(trackedObj);
    		}
		}

		if (checkAddCondition(callingMethodLibName, actualCalledMethodLibName)) {
			updateInterLibraryCounts(methodCallerClassName+"::"+callingMethodName, callingMethodLibName, calleeVisibility, virtualCalleeMethod,
					actualMethodCalledClassName+"::"+actualCalledMethodName, virtualMethodLibName, actualCalledMethodLibName, 1, false, false, serviceBypass);
		}
	}
	
	public static void updateReflectiveCaller(long threadID, String callerMethodName, String callerClassName, Object invokedOn) {
    	String serviceBypass = "";
		if (servicesInfo.stream().anyMatch(key -> key.implLibs.values().contains(findLibrary(((Method)invokedOn).getDeclaringClass().getName()))))
    		serviceBypass = "reflection";
		instrumentation.apisurfaceanalysis.CallTrackerTransformer.reflectiveCaller.put(threadID, callerMethodName+":"+callerClassName+";"+serviceBypass);
	}
	
	public static void updateDynamicProxyCaller(Object calledMethodObj) {
		Method calledMethod = (Method)calledMethodObj;
		String calleeMethodName = calledMethod.getDeclaringClass()+"::"+calledMethod.getName()+NameUtility.getDescriptor(calledMethod);
		int mods = calledMethod.getModifiers();
		String calleeVisibility = Modifier.isPublic(mods) ? "public" : (Modifier.isPrivate(mods) ? "private" : (Modifier.isProtected(mods) ? "protected" : "unknown"));
		StackTraceElement[] stes = Thread.currentThread().getStackTrace();
		String className = stes[4].getClassName();
		String methodName = stes[4].getMethodName();
		String descriptor = "";
		try {descriptor = NameUtility.getDescriptor(Class.forName(className, false, classLoader).getMethod(methodName));} catch (Exception e) { }
		String calledMethodLibName = findLibrary(calledMethod.getDeclaringClass().getName());
		System.out.println("chking dynamic proxy----"+calledMethod.getDeclaringClass());

		String virtualCalleeMethod = "";
		long currentThreadID = Thread.currentThread().getId();
		if (instrumentation.apisurfaceanalysis.CallTrackerTransformer.dynProxyCaller.containsKey(currentThreadID))
			virtualCalleeMethod = instrumentation.apisurfaceanalysis.CallTrackerTransformer.dynProxyCaller.get(currentThreadID);

		instrumentation.apisurfaceanalysis.CallTrackerTransformer.reflectiveCaller.remove(currentThreadID);
		updateInterLibraryCounts(className+"::"+methodName+descriptor, findLibrary(stes[4].getClassName()), calleeVisibility, virtualCalleeMethod, calleeMethodName, calledMethodLibName, calledMethodLibName, 1, false, true, "");
	}
	
	public static void handleSetAccessible(Object calledOn, boolean setTo, String callerLib, String callerMethod) {
		String vis="", calledOnObjName="", libName="", sig="";
		if (calledOn.getClass().getName().equals("java.lang.reflect.Method")) {
			Method calledOnMethod = (Method)calledOn;
			int mod = calledOnMethod.getModifiers();
			vis = Modifier.isPublic(mod) ? "public" : (Modifier.isPrivate(mod) ? "private" : (Modifier.isProtected(mod) ? "protected" : "unknown"));
			calledOnObjName = calledOnMethod.getDeclaringClass().getName()+"::"+calledOnMethod.getName()+NameUtility.getDescriptor(calledOnMethod);
			libName = findLibrary(calledOnMethod.getDeclaringClass().getName());
		} else if (calledOn.getClass().getName().equals("java.lang.reflect.Constructor")) {
			Constructor<?> calledOnConstructor = (Constructor<?>)calledOn;
			int mod = calledOnConstructor.getModifiers();
			vis = Modifier.isPublic(mod) ? "public" : (Modifier.isPrivate(mod) ? "private" : (Modifier.isProtected(mod) ? "protected" : "unknown"));
			calledOnObjName = calledOnConstructor.getDeclaringClass().getName()+"::"+calledOnConstructor.getName()+NameUtility.getDescriptor(calledOnConstructor);
			libName = findLibrary(calledOnConstructor.getDeclaringClass().getName());
		} else if (calledOn.getClass().getName().equals("java.lang.reflect.Field")) {
			Field calledOnField = (Field)calledOn;
			int mod = calledOnField.getModifiers();
			vis = Modifier.isPublic(mod) ? "public" : (Modifier.isPrivate(mod) ? "private" : (Modifier.isProtected(mod) ? "protected" : "unknown"));
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
			
			ServicesInfoKey servicesInfoKey = new ServicesInfoKey(interfaceName, findLibrary(interfaceName), implsToLibs, implsToMethods);
			servicesInfo.add(servicesInfoKey);
		} catch (NotFoundException e) {
			System.out.println(e);
		}
	}
	
	public static void updateLibsToMethods(String calledMethodLibName, String methodCalledClassName, String calledMethodName) {
			String calledMethodFullName = methodCalledClassName+"::"+calledMethodName;
		if (!libsToMethods.containsKey(calledMethodLibName))
			libsToMethods.put(calledMethodLibName, new HashSet<String>(Arrays.asList(calledMethodFullName)));
		else if (!libsToMethods.get(calledMethodLibName).contains(calledMethodFullName))
			libsToMethods.get(calledMethodLibName).add(calledMethodFullName);
	}
	
	public static void updateInterLibraryCounts(String callerMethod, String callerLibrary, String calleeVisibility, String virtualCalleeMethod, String actualCalleeMethod, 
			String virtualCalleeMethodLibString, String actualCalleeMethodLibString, int count, boolean reflective, boolean dynamicProxy, String serviceBypass) {
		InterLibraryCallsKey key = new InterLibraryCallsKey(callerMethod, callerLibrary, calleeVisibility, virtualCalleeMethod, virtualCalleeMethodLibString,
				actualCalleeMethod, actualCalleeMethodLibString, reflective, dynamicProxy, serviceBypass);
		interLibraryCalls.putIfAbsent(key, 0);
		interLibraryCalls.put(key, interLibraryCalls.get(key) + count);
	}
	
	public static void getStackTrace(String calleeMethodName, String calleeLib, String calleeVisibility) {
		long currentThreadID = Thread.currentThread().getId();
		if (!instrumentation.apisurfaceanalysis.CallTrackerTransformer.reflectiveCaller.containsKey(currentThreadID))
			return;
		String [] reflData = instrumentation.apisurfaceanalysis.CallTrackerTransformer.reflectiveCaller.get(currentThreadID).split(";");
		String[] caller = reflData[0].split(":");
		instrumentation.apisurfaceanalysis.CallTrackerTransformer.reflectiveCaller.remove(currentThreadID);
		String callerMethodName = caller[0];
		String callerClassName = caller[1];
		String callerLib = findLibrary(callerClassName);
		if (checkAddCondition(callerLib, calleeLib))
			updateInterLibraryCounts(callerClassName+"::"+callerMethodName, callerLib, calleeVisibility, calleeMethodName, calleeMethodName, calleeLib, calleeLib, 1, true, false, reflData[1]);
	}
	
	public static void handleFieldSetGet(Object field, Object actualObj, String callingMethodLibName) {
		java.lang.reflect.Field f = (java.lang.reflect.Field) field;
		String[] fieldData = field.toString().split(" ");
		String fieldClass = fieldData[fieldData.length-1].substring(0, fieldData[fieldData.length-1].lastIndexOf("."));
		String fieldLib = findLibrary(fieldClass);

		int mods = f.getModifiers();
		String fieldVisibility = javassist.Modifier.isPublic(mods) ? "public" : (javassist.Modifier.isPrivate(mods) ? "private" : (javassist.Modifier.isProtected(mods) ? "protected" : "unknown"));
		InterLibraryFieldsKey key = new InterLibraryFieldsKey(callingMethodLibName, fieldClass, actualObj.getClass().getName(), actualObj.getClass().getName()+"::"+f.getName(), 
				f.getGenericType().getTypeName(), javassist.Modifier.isStatic(mods), fieldVisibility, fieldLib, true);
		interLibraryFields.putIfAbsent(key, 0);
		interLibraryFields.put(key, interLibraryFields.get(key) + 1);
	}
	
	public static void handleFields(String callingMethodLibName, String fieldClass, String actualClass, String fieldName, String sig, boolean isStatic, String fieldVisibility, String fieldLib) {
		InterLibraryFieldsKey key = new InterLibraryFieldsKey(callingMethodLibName, fieldClass, actualClass, actualClass+"::"+fieldName, sig, isStatic, fieldVisibility, fieldLib, false);
		interLibraryFields.putIfAbsent(key, 0);
		interLibraryFields.put(key, interLibraryFields.get(key) + 1);
	}
	
	public static String findLibrary(String className) {
		if (className.startsWith("java.util.stream"))
			return unknownEntry.getKey();
		if (!classesToLibs.containsKey(className))
			classesToLibs.put(className, libsToClasses.entrySet().stream().parallel()
			.filter(map -> map.getValue().containsNode(className))
			.findAny().orElse(unknownEntry).getKey());
		if (className.lastIndexOf(".") > 0) {
			String pkgName = className.substring(0, className.lastIndexOf("."));
			if (classesToLibs.get(className).equals(unknownEntry.getKey()) && libsToClasses.containsKey(pkgName)) {
				libsToClasses.get(pkgName).insert(className);
				classesToLibs.put(className, pkgName);
			}
		}
		return classesToLibs.get(className);
	}
}
