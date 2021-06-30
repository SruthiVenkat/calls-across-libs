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
import javassist.CtClass;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import javassist.expr.MethodCall;

class InterLibraryCallsKey {
	String callerMethodString;
	String callerMethodLibString;
	String virtualCalleeMethodString;
	String actualCalleeMethodString;
	String calleeMethodLibString;

	InterLibraryCallsKey(String callerMethodString, String callerMethodLibString, String virtualCalleeMethodString, 
			String actualCalleeMethodString, String calleeMethodLibString) {
		this.callerMethodString = callerMethodString;
		this.callerMethodLibString = callerMethodLibString;
		this.virtualCalleeMethodString = virtualCalleeMethodString;
		this.actualCalleeMethodString = actualCalleeMethodString;
		this.calleeMethodLibString = calleeMethodLibString;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) return false;
		if (obj.getClass() != this.getClass()) return false;
		InterLibraryCallsKey keyObj = (InterLibraryCallsKey) obj;
		return (keyObj.callerMethodString.equals(this.callerMethodString) && keyObj.callerMethodLibString.equals(this.callerMethodLibString)
				&& keyObj.virtualCalleeMethodString.equals(this.virtualCalleeMethodString) && keyObj.actualCalleeMethodString.equals(this.actualCalleeMethodString)
				&& keyObj.calleeMethodLibString.equals(this.calleeMethodLibString));
	}

	@Override
	public int hashCode() {
		return callerMethodString.hashCode() + callerMethodLibString.hashCode() + virtualCalleeMethodString.hashCode()
				+ actualCalleeMethodString.hashCode() + calleeMethodLibString.hashCode();
	}
}

class InterLibraryFieldsKey {
	String calleeLib;
	String fieldName;
	String fieldSignature;
	String libName;

	InterLibraryFieldsKey(String calleeLib, String fieldName, String fieldSignature, String libName) {
		this.calleeLib = calleeLib;
		this.fieldName = fieldName;
		this.fieldSignature = fieldSignature;
		this.libName = libName;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) return false;
		if (obj.getClass() != this.getClass()) return false;
		InterLibraryFieldsKey keyObj = (InterLibraryFieldsKey) obj;
		return (keyObj.calleeLib.equals(this.calleeLib) && keyObj.fieldName.equals(this.fieldName) && keyObj.fieldSignature.equals(this.fieldSignature) && keyObj.libName.equals(this.libName));
	}

	@Override
	public int hashCode() {
		return calleeLib.hashCode() + fieldName.hashCode() + fieldSignature.hashCode() + libName.hashCode();
	}
}

class InterLibrarySubtyping {
	String subClass;
	String subClassLib;
	String superClass;
	String superClassLib;

	InterLibrarySubtyping(String subClass, String subClassLib, String superClass, String superClassLib) {
		this.subClass = subClass;
		this.subClassLib = subClassLib;
		this.superClass = superClass;
		this.superClassLib = superClassLib;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) return false;
		if (obj.getClass() != this.getClass()) return false;
		InterLibrarySubtyping keyObj = (InterLibrarySubtyping) obj;
		return (keyObj.subClass.equals(this.subClass) && keyObj.subClassLib.equals(this.subClassLib) 
				&& keyObj.superClass.equals(this.superClass) && keyObj.superClassLib.equals(this.superClassLib));
	}

	@Override
	public int hashCode() {
		return subClass.hashCode() + subClassLib.hashCode() + superClass.hashCode() + superClassLib.hashCode();
	}
}

class InterLibraryAnnotations {
	String className;
	String methodName;
	String field;
	String classLib;
	String annotationName;
	String annotationLib;

	InterLibraryAnnotations(String className, String methodName, String field, String classLib, String annotationName, String annotationLib) {
		this.className = className;
		this.methodName = methodName;
		this.field = field;
		this.classLib = classLib;
		this.annotationName = annotationName;
		this.annotationLib = annotationLib;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) return false;
		if (obj.getClass() != this.getClass()) return false;
		InterLibraryAnnotations keyObj = (InterLibraryAnnotations) obj;
		return (keyObj.className.equals(this.className) && keyObj.methodName.equals(methodName) && keyObj.field.equals(field) && keyObj.classLib.equals(this.classLib) 
				&& keyObj.annotationName.equals(this.annotationName) && keyObj.annotationLib.equals(this.annotationLib));
	}

	@Override
	public int hashCode() {
		return className.hashCode() + methodName.hashCode() + field.hashCode() + classLib.hashCode() + annotationName.hashCode() + annotationLib.hashCode();
	}
}

public class CallTrackerTransformer implements ClassFileTransformer {
	public static Map<String, TrieUtil> libsToClasses = new HashMap<String, TrieUtil>();
	public static String runningLibrary; 
	public static Map<String, List<String>> libsToMethods = new HashMap<String, List<String>>();
	public static ConcurrentHashMap<InterLibraryCallsKey, Integer> interLibraryCalls = new ConcurrentHashMap<InterLibraryCallsKey, Integer>();
	public static ConcurrentHashMap<InterLibraryFieldsKey, Integer> interLibraryFields = new ConcurrentHashMap<InterLibraryFieldsKey, Integer>();
	public static HashSet<InterLibrarySubtyping> interLibrarySubtyping = new HashSet<InterLibrarySubtyping>();
	public static HashSet<InterLibraryAnnotations> interLibraryAnnotations = new HashSet<InterLibraryAnnotations>();

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
			CtClass ctClass = classPool.makeClass(new ByteArrayInputStream(
					classfileBuffer));
			
			String methodCallerClassName = ctClass.getName();
			String callingMethodLibNameTmp = "";
			Entry<String, TrieUtil> unknownEntry = new AbstractMap.SimpleEntry<String, TrieUtil>("unknownLib", new TrieUtil());
			callingMethodLibNameTmp = libsToClasses.entrySet().stream()
			.filter(map -> map.getValue().containsNode(methodCallerClassName))
			.findAny().orElse(unknownEntry).getKey();
//			if (callingMethodLibNameTmp.equals(unknownEntry.getKey()) && methodCallerClassName.indexOf(".")>0) {
//				callingMethodLibNameTmp = methodCallerClassName.substring(0, methodCallerClassName.lastIndexOf("."));
//			}
			
			final String callingMethodLibName = callingMethodLibNameTmp;
			if(callingMethodLibName.equals(unknownEntry.getKey())) {
			//if(methodCallerClassName.startsWith("instrumentation")) {
				return null;
			}
			
			System.out.println("------>"+methodCallerClassName); // TODO remove print
			CtMethod[] methods = ctClass.getDeclaredMethods();
			for (CtMethod method : methods) {
				if (!isNative(method) && !isAbstract(method)) {
					String callingMethodName = method.getName();
					String callingDescriptorName = NameUtility.getDescriptor(method);

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
					
					// reflective calls
					method.insertBefore("{instrumentation.apisurfaceanalysis.CallTrackerTransformer.getStackTrace(\""+methodCallerClassName+"::"+callingMethodName+callingDescriptorName+"\", \""+callingMethodLibName+"\");}");
		
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
//										if (calledMethodLibName.equals(unknownEntry.getKey()) && methodCalledClassName.indexOf(".")>0) {
//											calledMethodLibName = methodCalledClassName.substring(0, methodCalledClassName.lastIndexOf("."));
//										}
										
										// Annotations - methods
					        			Object[] mAnnotations = m.getMethod().getAnnotations();
										if (mAnnotations!=null) {
											for (Object annotationObj : mAnnotations) {
												String annotationName = annotationObj.getClass().getName();
												String annotationLib = libsToClasses.entrySet().stream()
														.filter(map -> map.getValue().containsNode(annotationName))
														.findAny().orElse(unknownEntry).getKey();
												if (!annotationLib.equals(unknownEntry.getKey()) && !calledMethodLibName.equals(annotationLib)) {
													InterLibraryAnnotations mKey = new InterLibraryAnnotations("-", methodCalledClassName+"::"+calledMethodName+calledDescriptorName, "-", calledMethodLibName, annotationName, annotationLib);
							            			interLibraryAnnotations.add(mKey);
												}
											}
										}
										
										if(!calledMethodLibName.equals(unknownEntry.getKey())) {
										//if (!methodCalledPkgName.startsWith("java.")) {
										//if (!methodCalledClassName.startsWith("instrumentation")) {
																				
											int modifiers = m.getMethod().getModifiers();
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
													callingMethodLibName+"\", \""+methodCalledClassName+"::"+calledMethodName+calledDescriptorName+"\", \"" + methodCalledClassName+"::"+calledMethodName+calledDescriptorName
													+"\", \"" + calledMethodLibName+"\", 1);} $_ = $proceed($$);}");
											else
												m.replace("{if ($0 != null) { " + codeToAdd 
													+ "instrumentation.apisurfaceanalysis.CallTrackerTransformer.addRuntimeType(\""+methodCallerClassName+"\", \""+callingMethodName+callingDescriptorName+
													"\", \""+methodCalledClassName+"::"+calledMethodName+calledDescriptorName+"\", \""+callingMethodLibName+"\", $0.getClass(), \""
													+ calledMethodName+calledDescriptorName+"\");}" + " $_ = $proceed($$);}");
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
					            		if (!fieldLib.equals(unknownEntry.getKey()) && !callingMethodLibName.equals(fieldLib)) {
					            			//System.out.println(f.getField().getName()+"<----"+fieldClass+"<----"+f.getSignature()+"<----"+fieldLib);
					            			InterLibraryFieldsKey key = new InterLibraryFieldsKey(callingMethodLibName, fieldClass+"::"+f.getField().getName(), f.getSignature(), fieldLib);
					            			interLibraryFields.putIfAbsent(key, 0);
					            			interLibraryFields.put(key, interLibraryFields.get(key) + 1);
		
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

			// 4. Annotation Usage - class (, method, field)
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
			String callingMethodLibName, Class<?> actualMethodCalledClass, String actualCalledMethodName) {
		String calledMethodLibName = "";
		String actualMethodCalledClassName = actualMethodCalledClass.getName();
		Entry<String, TrieUtil> unknownEntry = new AbstractMap.SimpleEntry<String, TrieUtil>("unknownLib", new TrieUtil());
		try {
			calledMethodLibName = libsToClasses.entrySet().stream()
			.filter(map -> map.getValue().containsNode(actualMethodCalledClassName))
			.findAny().orElse(unknownEntry).getKey();
			if (calledMethodLibName.equals(unknownEntry.getKey()) && actualMethodCalledClassName.indexOf(".") > 0) {
				calledMethodLibName = actualMethodCalledClassName.substring(0, actualMethodCalledClassName.lastIndexOf("."));
			}
		} catch (NullPointerException e) {
			System.out.println(e);e.printStackTrace();
		}

		if (checkAddCondition(callingMethodLibName, calledMethodLibName)) {
			updateInterLibraryCounts(methodCallerClassName+"::"+callingMethodName, callingMethodLibName, virtualCalleeMethod,
					actualMethodCalledClassName+"::"+actualCalledMethodName, calledMethodLibName, 1);
		}
	}
	
	public static void updateLibsToMethods(String calledMethodLibName, String methodCalledClassName, String calledMethodName) {
		String calledMethodFullName = methodCalledClassName+"::"+calledMethodName;
		if (!libsToMethods.containsKey(calledMethodLibName))
			libsToMethods.put(calledMethodLibName, new ArrayList<String>(Arrays.asList(calledMethodFullName)));
		else if (!libsToMethods.get(calledMethodLibName).contains(calledMethodFullName))
			libsToMethods.get(calledMethodLibName).add(calledMethodFullName);
	}
	
	public static void updateInterLibraryCounts(String callerMethod, String callerLibrary, String virtualCalleeMethod, 
			String actualCalleeMethod, String calleeLibrary, int count) {
		InterLibraryCallsKey key = new InterLibraryCallsKey(callerMethod, callerLibrary, virtualCalleeMethod, actualCalleeMethod, calleeLibrary);
		interLibraryCalls.putIfAbsent(key, 0);
		interLibraryCalls.put(key, interLibraryCalls.get(key) + count);
	}
	
	public static void getStackTrace(String calleeMethodName, String calleeLib) {
		StackTraceElement[] ste = Thread.currentThread().getStackTrace();
		if (ste.length<3 || !(ste[3].getMethodName().contains("invoke0") && ste[3].getClassName().contains("sun.reflect.NativeMethodAccessorImpl")))
			return;
		assert ste.length >= 8;
		String callerClassName = ste[7].getClassName();
		Entry<String, TrieUtil> unknownEntry = new AbstractMap.SimpleEntry<String, TrieUtil>("unknownLib", new TrieUtil());
		String callerLib = libsToClasses.entrySet().stream()
				.filter(map -> map.getValue().containsNode(callerClassName))
				.findAny().orElse(unknownEntry).getKey();
		if (checkAddCondition(callerLib, calleeLib))
			updateInterLibraryCounts(callerClassName+"::"+ste[7].getMethodName(), callerLib, calleeMethodName, calleeMethodName, calleeLib, 1);
	}
}
