package instrumentation.apisurfaceanalysis;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import instrumentation.util.NameUtility;
import instrumentation.util.TrieUtil;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.Descriptor;
import javassist.bytecode.SignatureAttribute;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import javassist.expr.MethodCall;

public class CallTrackerTransformer implements ClassFileTransformer {
	public static Map<String, TrieUtil> libsToClasses = new HashMap<String, TrieUtil>();
	public static String runningLibrary; 
	public static Map<String, List<String>> libsToMethods = new HashMap<String, List<String>>();
	public static ConcurrentHashMap<InterLibraryCallsKey, Integer> interLibraryCalls = new ConcurrentHashMap<InterLibraryCallsKey, Integer>();
	public static ConcurrentHashMap<InterLibraryFieldsKey, Integer> interLibraryFields = new ConcurrentHashMap<InterLibraryFieldsKey, Integer>();
	public static HashSet<InterLibrarySubtyping> interLibrarySubtyping = new HashSet<InterLibrarySubtyping>();
	public static HashSet<InterLibraryAnnotations> interLibraryAnnotations = new HashSet<InterLibraryAnnotations>();
	public static Entry<String, TrieUtil> unknownEntry = new AbstractMap.SimpleEntry<String, TrieUtil>("unknownLib", new TrieUtil());
	public static Map<String, String> reflectiveCaller = new HashMap<String, String>();
	
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
		} catch (IOException ex) {
		    ex.printStackTrace();
		}
	}

	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
		byte[] byteCode = classfileBuffer;
		try {
			ClassPool classPool = ClassPool.getDefault();
			CtClass ctClass = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
			
			String methodCallerClassName = ctClass.getName();
			String callingMethodLibNameTmp = "";
			callingMethodLibNameTmp = libsToClasses.entrySet().stream()
			.filter(map -> map.getValue().containsNode(methodCallerClassName))
			.findAny().orElse(unknownEntry).getKey();

			final String callingMethodLibName = callingMethodLibNameTmp;

			if(callingMethodLibName.equals(unknownEntry.getKey())) {
				return null;
			}
			
			System.out.println("------>"+methodCallerClassName); // TODO remove print
			CtMethod[] methods = ctClass.getDeclaredMethods();
			
			for (CtMethod method : methods) {
				if (!isNative(method) && !isAbstract(method)) {
					String callingMethodName = method.getName();
					String callingDescriptorName = NameUtility.getDescriptor(method);
					int mods = method.getModifiers();
            		String methodVisibility = javassist.Modifier.isPublic(mods) ? "public" : (javassist.Modifier.isPrivate(mods) ? "private" : (javassist.Modifier.isProtected(mods) ? "protected" : "unknown"));
							
					// reflective
					method.insertBefore("{if (!instrumentation.apisurfaceanalysis.CallTrackerTransformer.reflectiveCaller.isEmpty()) instrumentation.apisurfaceanalysis.CallTrackerTransformer.getStackTrace(\""+methodCallerClassName+"::"+callingMethodName+callingDescriptorName+"\", \""+callingMethodLibName+"\", \""+methodVisibility+"\");}");

					// Annotations - methods
        			Object[] methodAnnotations = method.getAnnotations();
					if (methodAnnotations!=null) {
						for (Object annotationObj : methodAnnotations) {
							String annotationName = annotationObj.getClass().getName();
							String annotationLib = libsToClasses.entrySet().stream()
									.filter(map -> map.getValue().containsNode(annotationName))
									.findAny().orElse(unknownEntry).getKey();
							if (!annotationLib.equals(unknownEntry.getKey()) && !callingMethodLibName.equals(annotationLib)) {
								InterLibraryAnnotations methodKey = new InterLibraryAnnotations("-", methodCallerClassName+"::"+callingMethodName+callingDescriptorName, "-", callingMethodLibName, annotationName, annotationLib);
		            			interLibraryAnnotations.add(methodKey);
							}
						}
					}

					method.instrument(
					        new ExprEditor() {
					        	// 1. Method Invocations
					            public void edit(MethodCall m) throws CannotCompileException
					            {
					            	try {
					            		if (method == m.getMethod() || isNative(m.getMethod()))
					            			return;

						            	String calledMethodName = m.getMethodName();
						            	String methodCalledClassName = m.getClassName();
										String calledDescriptorName = NameUtility.getDescriptor(m.getMethod());
										String calledMethodLibName = libsToClasses.entrySet().stream()
												.filter(map -> map.getValue().containsNode(methodCalledClassName))
												.findAny().orElse(unknownEntry).getKey();
										
										// reflective calls
										if (methodCalledClassName.equals("java.lang.reflect.Method") && calledMethodName.equals("invoke")) {
											m.replace("{instrumentation.apisurfaceanalysis.CallTrackerTransformer.reflectiveCaller.put(\""+callingMethodName+"\", \""+methodCallerClassName+"\"); $_ = $proceed($$);}");
										}
										if (methodCalledClassName.equals("java.lang.reflect.Field")) {
											if (calledMethodName.equals("set")) {
												m.replace("{instrumentation.apisurfaceanalysis.CallTrackerTransformer.handleFieldSetGet($0, $1, \""+callingMethodLibName+"\"); $_ = $proceed($$);}");
											}
											else if (calledMethodName.equals("get"))
												m.replace("{instrumentation.apisurfaceanalysis.CallTrackerTransformer.handleFieldSetGet($0, $1, \""+callingMethodLibName+"\"); $_ = $proceed($$);}");
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
													codeToAdd = "instrumentation.apisurfaceanalysis.CallTrackerTransformer.updateLibsToMethods(\""+runningLibrary+"\", $0.getClass().getName(), \""+ calledMethodName+calledDescriptorName + "\");";
											}

											if (Modifier.isStatic(m.getMethod().getModifiers()))
												m.replace("{if (instrumentation.apisurfaceanalysis.CallTrackerTransformer.checkAddCondition(\""+callingMethodLibName+"\", \""+calledMethodLibName+"\")) {"
													+ "instrumentation.apisurfaceanalysis.CallTrackerTransformer.updateInterLibraryCounts(\"" + methodCallerClassName+"::"+callingMethodName+callingDescriptorName+"\",\"" + 
													callingMethodLibName+"\", \""+mVisibility+"\", \""+methodCalledClassName+"::"+calledMethodName+calledDescriptorName+"\", \"" + methodCalledClassName+"::"+calledMethodName+calledDescriptorName
													+"\", \"" + calledMethodLibName+"\", 1, false);} $_ = $proceed($$);}");
											else
												m.replace("{if ($0 != null) { " + codeToAdd 
													+ "instrumentation.apisurfaceanalysis.CallTrackerTransformer.addRuntimeType(\""+methodCallerClassName+"\", \""+callingMethodName+callingDescriptorName+
													"\", \""+methodCalledClassName+"::"+calledMethodName+calledDescriptorName+"\", \""+callingMethodLibName+"\", $0.getClass(), \""
													+ calledMethodName+calledDescriptorName+"\", \""+mVisibility+"\");}" + " $_ = $proceed($$);}");
											} 
										} catch (Exception e) {
											System.out.println(e);
										}
									}

					            // 2. Field Accesses
					            public void edit(FieldAccess f) throws CannotCompileException {
					            	try {
					            		String fieldClass = f.getClassName();
					            		String fieldLib = libsToClasses.entrySet().stream()
												.filter(map -> map.getValue().containsNode(fieldClass))
												.findAny().orElse(unknownEntry).getKey();
					            		SignatureAttribute sa = (SignatureAttribute) f.getField().getFieldInfo().getAttribute(SignatureAttribute.tag);
					            		String sig;
										try {
											sig = (sa == null) ? f.getSignature() : SignatureAttribute.toFieldSignature(sa.getSignature()).toString();
										} catch (BadBytecode e) {
											sig = (sa == null) ? f.getSignature() : sa.getSignature();
											e.printStackTrace();
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
					    							String annotationLib = libsToClasses.entrySet().stream()
					    									.filter(map -> map.getValue().containsNode(annotationName))
					    									.findAny().orElse(unknownEntry).getKey();
					    							if (!annotationLib.equals(unknownEntry.getKey()) && !fieldLib.equals(annotationLib)) {
					    								InterLibraryAnnotations fieldKey = new InterLibraryAnnotations("-", "-", fieldClass+"::"+f.getField().getName()+":"+f.getSignature(), fieldLib, annotationName, annotationLib);
					    		            			interLibraryAnnotations.add(fieldKey);
					    							}
					    						}
					    					}
					    					
					    					if (f.isStatic()) {
						            			InterLibraryFieldsKey key = new InterLibraryFieldsKey(callingMethodLibName, fieldClass, fieldClass, fieldClass+"::"+f.getField().getName(), sig, f.isStatic(), fieldVisibility, fieldLib);
						            			interLibraryFields.putIfAbsent(key, 0);
						            			interLibraryFields.put(key, interLibraryFields.get(key) + 1);
					            			} else f.replace("{ instrumentation.apisurfaceanalysis.CallTrackerTransformer.handleFields(\""+callingMethodLibName+"\", \""
						            			+fieldClass+"\", $0.getClass().getName(), \""+f.getField().getName()+"\", \""+sig+"\", "+f.isStatic()+", \""+fieldVisibility+"\", \""+fieldLib+"\"); $_ = $proceed($$);}");
					            		}
									} catch (NotFoundException e) {
										e.printStackTrace();
									} catch (ClassNotFoundException e) {
										e.printStackTrace();
									}
					            }
					        });
				}
			}

			// 3. Subtyping ( extends / implements )
			CtClass superClass = ctClass.getSuperclass();
			if (superClass!=null) {
				String superClassName = superClass.getName();
				String superClassLib = libsToClasses.entrySet().stream()
						.filter(map -> map.getValue().containsNode(superClassName))
						.findAny().orElse(unknownEntry).getKey();
				if (!superClassLib.equals(unknownEntry.getKey()) && !callingMethodLibName.equals(superClassLib)) {
					InterLibrarySubtyping key = new InterLibrarySubtyping(methodCallerClassName, callingMethodLibName, superClassName, superClassLib);
        			interLibrarySubtyping.add(key);
				}
			}
			CtClass[] interfaces = ctClass.getInterfaces();
			if (interfaces!=null) {
				for (CtClass interfaceClass : interfaces) {
					String interfaceName = interfaceClass.getName();
					String interfaceLib = libsToClasses.entrySet().stream()
							.filter(map -> map.getValue().containsNode(interfaceName))
							.findAny().orElse(unknownEntry).getKey();
					if (!interfaceLib.equals(unknownEntry.getKey()) && !callingMethodLibName.equals(interfaceLib)) {
						InterLibrarySubtyping key = new InterLibrarySubtyping(methodCallerClassName, callingMethodLibName, interfaceName, interfaceLib);
            			interLibrarySubtyping.add(key);
					}
				}
			}

			// 4. Annotation Usage - class (& method, field)
			Object[] annotations = ctClass.getAnnotations();
			if (annotations!=null) {
				for (Object annotationObj : annotations) {
					String annotationName = annotationObj.toString().substring(1);
					String annotationLib = libsToClasses.entrySet().stream()
							.filter(map -> map.getValue().containsNode(annotationName))
							.findAny().orElse(unknownEntry).getKey();
					if (!annotationLib.equals(unknownEntry.getKey()) && !callingMethodLibName.equals(annotationLib)) {
						InterLibraryAnnotations key = new InterLibraryAnnotations(methodCallerClassName, "-", "-", callingMethodLibName, annotationName, annotationLib);
            			interLibraryAnnotations.add(key);
					}
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
	
	public static boolean checkAddCondition(String callingMethodLibName, String calledMethodLibName) {
		return !callingMethodLibName.equals("unknownLib") && !calledMethodLibName.equals("unknownLib") && !callingMethodLibName.equals(calledMethodLibName);
	}
	
	public static void addRuntimeType(String methodCallerClassName, String callingMethodName, String virtualCalleeMethod, 
			String callingMethodLibName, Class<?> actualMethodCalledClass, String actualCalledMethodName, String calleeVisibility) {
		String calledMethodLibName = "";
		String actualMethodCalledClassName = actualMethodCalledClass.getName();
		try {
			calledMethodLibName = libsToClasses.entrySet().stream()
			.filter(map -> map.getValue().containsNode(actualMethodCalledClassName))
			.findAny().orElse(unknownEntry).getKey();
		} catch (NullPointerException e) {
			System.out.println(e);e.printStackTrace();
		}

		if (checkAddCondition(callingMethodLibName, calledMethodLibName)) {
			updateInterLibraryCounts(methodCallerClassName+"::"+callingMethodName, callingMethodLibName, calleeVisibility, virtualCalleeMethod,
					actualMethodCalledClassName+"::"+actualCalledMethodName, calledMethodLibName, 1, false);
		}
	}
	
	public static void updateLibsToMethods(String calledMethodLibName, String methodCalledClassName, String calledMethodName) {
		String calledMethodFullName = methodCalledClassName+"::"+calledMethodName;
		if (!libsToMethods.containsKey(calledMethodLibName))
			libsToMethods.put(calledMethodLibName, new ArrayList<String>(Arrays.asList(calledMethodFullName)));
		else if (!libsToMethods.get(calledMethodLibName).contains(calledMethodFullName))
			libsToMethods.get(calledMethodLibName).add(calledMethodFullName);
	}
	
	public static void updateInterLibraryCounts(String callerMethod, String callerLibrary, String calleeVisibility, String virtualCalleeMethod, 
			String actualCalleeMethod, String calleeLibrary, int count, boolean reflective) {
		InterLibraryCallsKey key = new InterLibraryCallsKey(callerMethod, callerLibrary, calleeVisibility, virtualCalleeMethod, 
				actualCalleeMethod, calleeLibrary, reflective);
		interLibraryCalls.putIfAbsent(key, 0);
		interLibraryCalls.put(key, interLibraryCalls.get(key) + count);
	}
	
	public static void getStackTrace(String calleeMethodName, String calleeLib, String calleeVisibility) {
		if (instrumentation.apisurfaceanalysis.CallTrackerTransformer.reflectiveCaller.isEmpty())
			return;
		String callerMethodName = (String)reflectiveCaller.keySet().toArray()[0];
		String callerClassName = reflectiveCaller.get(callerMethodName);
	
		String callerLib = libsToClasses.entrySet().stream()
				.filter(map -> map.getValue().containsNode(callerClassName))
				.findAny().orElse(unknownEntry).getKey();
		if (checkAddCondition(callerLib, calleeLib))
			updateInterLibraryCounts(callerClassName+"::"+callerMethodName, callerLib, calleeVisibility, calleeMethodName, calleeMethodName, calleeLib, 1, true);
		instrumentation.apisurfaceanalysis.CallTrackerTransformer.reflectiveCaller.clear();
	}
	
	public static void handleFieldSetGet(Object field, Object actualObj, String callingMethodLibName) {
		System.out.println("actual obj - "+actualObj.getClass().getName());
		java.lang.reflect.Field f = (java.lang.reflect.Field) field;
		String[] fieldData = field.toString().split(" ");
		String fieldClass = fieldData[fieldData.length-1].substring(0, fieldData[fieldData.length-1].lastIndexOf("."));
		String fieldLib = libsToClasses.entrySet().stream()
				.filter(map -> map.getValue().containsNode(fieldClass))
				.findAny().orElse(unknownEntry).getKey();

		int mods = f.getModifiers();
		String fieldVisibility = javassist.Modifier.isPublic(mods) ? "public" : (javassist.Modifier.isPrivate(mods) ? "private" : (javassist.Modifier.isProtected(mods) ? "protected" : "unknown"));
		InterLibraryFieldsKey key = new InterLibraryFieldsKey(callingMethodLibName, fieldClass, actualObj.getClass().getName(), actualObj.getClass().getName()+"::"+f.getName(), 
				f.getGenericType().getTypeName(), javassist.Modifier.isStatic(mods), fieldVisibility, fieldLib);
		interLibraryFields.putIfAbsent(key, 0);
		interLibraryFields.put(key, interLibraryFields.get(key) + 1);
	}
	
	public static void handleFields(String callingMethodLibName, String fieldClass, String actualClass, String fieldName, String sig, boolean isStatic, String fieldVisibility, String fieldLib) {
		System.out.println(actualClass+"---"+fieldName);
		InterLibraryFieldsKey key = new InterLibraryFieldsKey(callingMethodLibName, fieldClass, actualClass, actualClass+"::"+fieldName, sig, isStatic, fieldVisibility, fieldLib);
		interLibraryFields.putIfAbsent(key, 0);
		interLibraryFields.put(key, interLibraryFields.get(key) + 1);
	}
}
