package instrumentation;

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
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

class InterLibraryCountsKey {
	String callerMethodString;
	String callerMethodLibString;
	String calleeMethodString;
	String calleeMethodLibString;

	InterLibraryCountsKey(String callerMethodString, String callerMethodLibString, String calleeMethodString, 
			String calleeMethodLibString) {
		this.callerMethodString = callerMethodString;
		this.callerMethodLibString = callerMethodLibString;
		this.calleeMethodString = calleeMethodString;
		this.calleeMethodLibString = calleeMethodLibString;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) return false;
		if (obj.getClass() != this.getClass()) return false;
		InterLibraryCountsKey keyObj = (InterLibraryCountsKey) obj;
		return (keyObj.callerMethodString.equals(this.callerMethodString) && keyObj.callerMethodLibString.equals(this.callerMethodLibString)
				&& keyObj.calleeMethodString.equals(this.calleeMethodString) && keyObj.calleeMethodLibString.equals(this.calleeMethodLibString));
	}

	@Override
	public int hashCode() {
		return callerMethodString.hashCode() + callerMethodLibString.hashCode() + calleeMethodString.hashCode() + calleeMethodLibString.hashCode();
	}
}

class InterLibraryCountsValue {
	int staticCount;
	int dynamicCount;
	InterLibraryCountsValue(int staticCount, int dynamicCount) {
		this.staticCount = staticCount;
		this.dynamicCount = dynamicCount;
	}
}

public class CallTrackerTransformer implements ClassFileTransformer {
	public static Map<String, List<String>> libsToClasses = new HashMap<String, List<String>>();
	public static List<String> visitedCallerMethods = new ArrayList<String>();
	public static String runningLibrary; 
	public static Map<String, List<String>> libsToMethods = new HashMap<String, List<String>>();
	public static ConcurrentHashMap<InterLibraryCountsKey, InterLibraryCountsValue> interLibraryCounts = new ConcurrentHashMap<InterLibraryCountsKey, InterLibraryCountsValue>();

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
			    String[] data = row.split("\t");
			    if (data.length == 4)
			    libsToClasses.put(data[0], Arrays.asList(data[3].split(":")));
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
			//if(!libsToClasses.entrySet().stream().anyMatch(map -> map.getValue().contains(methodCallerClassName))) {
			if(methodCallerClassName.startsWith("java.")) {
				return null;
			}

			System.out.println("------>"+methodCallerClassName);
			CtMethod[] methods = ctClass.getDeclaredMethods();
			for (CtMethod method : methods) {
				if (!isNative(method) && !isAbstract(method) && !visitedCallerMethods.contains(method.getLongName())) {
					visitedCallerMethods.add(method.getLongName());
					Entry<String, List<String>> unknownEntry = new AbstractMap.SimpleEntry<String, List<String>>("unknownLib", new ArrayList<String>());
					String callingMethodName = method.getLongName();
					String callingMethodLibNameTmp = "";
					try {
						callingMethodLibNameTmp = libsToClasses.entrySet().stream()
						.filter(map -> map.getValue().contains(methodCallerClassName))
						.findAny().orElse(unknownEntry).getKey();
						if (callingMethodLibNameTmp.equals(unknownEntry.getKey()) && method.getClass().getPackage()!=null) {
							int index1 = methodCallerClassName.indexOf(".");
							int index2 = methodCallerClassName.indexOf(".", methodCallerClassName.indexOf(".") + 1);
							callingMethodLibNameTmp = index2>0 ? methodCallerClassName.substring(0, index2) : 
								(index1>0 ? methodCallerClassName.substring(0, index1) : "unknownLib");
						}
					} catch (NullPointerException e) {
						System.out.println(e);e.printStackTrace();
					}

					final String callingMethodLibName = callingMethodLibNameTmp;
					method.instrument(
					        new ExprEditor() {
					            public void edit(MethodCall m) throws CannotCompileException
					            {
					            	try {
					            		if (method == m.getMethod())
					            			return;
					            	} catch (Exception e) {}

					            	String calledMethodName; 
					            	try {
					            		calledMethodName = m.getMethod().getLongName();
					            	} catch (Exception e) {
										calledMethodName = m.getMethodName();
									}
					            	int i1 = calledMethodName.indexOf(".");
									int i2 = calledMethodName.indexOf("(");
									String dynCalledMethodName = i1>0 ? (i2>0 ? calledMethodName.substring(calledMethodName.substring(0, calledMethodName.indexOf("(")).lastIndexOf(".")+1)
										: calledMethodName.substring(calledMethodName.lastIndexOf(".")+1)) : calledMethodName;
					            	
					            	String methodCalledClassName = m.getClassName();
									String calledMethodLibName = "";

									//if(libsToClasses.entrySet().stream().anyMatch(map -> map.getValue().contains(methodCalledClassName))) {
									if (!methodCalledClassName.startsWith("java.")) {
										try {
											calledMethodLibName = libsToClasses.entrySet().stream()
											.filter(map -> map.getValue().contains(methodCalledClassName))
											.findAny().orElse(unknownEntry).getKey();
											if (calledMethodLibName.equals(unknownEntry.getKey()) && m.getClass().getPackage()!=null) {
												int index1 = methodCalledClassName.indexOf(".");
												int index2 = methodCalledClassName.indexOf(".", methodCalledClassName.indexOf(".") + 1);
												calledMethodLibName = index2>0 ? methodCalledClassName.substring(0, index2) : 
													(index1>0 ? methodCalledClassName.substring(0, index1) : "unknownLib");
											}
										} catch (NullPointerException e) {
											System.out.println(e);e.printStackTrace();
										}

										try {
											if (!callingMethodLibName.equals(unknownEntry.getKey()) && !calledMethodLibName.equals(unknownEntry.getKey())) {
												int modifiers = m.getMethod().getModifiers();
												String codeToAdd = "";
												if (libsToClasses.get(runningLibrary).contains(methodCallerClassName) && libsToClasses.get(runningLibrary).contains(methodCalledClassName)
														&& (javassist.Modifier.isPublic(modifiers) || javassist.Modifier.isProtected(modifiers))) {
													// static
													updateLibsToMethods(runningLibrary, methodCalledClassName, calledMethodName);
													// dynamic
													if (!Modifier.isStatic(modifiers))
														codeToAdd = "instrumentation.CallTrackerTransformer.updateLibsToMethods(\""+runningLibrary+"\", $0.getClass().getName(), $0.getClass().getName()+\"."+ dynCalledMethodName + "\");";
												}
												
												if (!callingMethodLibName.equals(calledMethodLibName)) {
												// static
												updateInterLibraryCounts(methodCallerClassName+"::"+callingMethodName, callingMethodLibName,
														methodCalledClassName+"::"+calledMethodName, calledMethodLibName, 1, 0);
												}
												// dynamic
												if (Modifier.isStatic(m.getMethod().getModifiers()))
													m.replace("{if (instrumentation.CallTrackerTransformer.checkAddCondition(\""+callingMethodLibName+"\", \""+calledMethodLibName+"\")) {"
														+ "instrumentation.CallTrackerTransformer.updateInterLibraryCounts(\"" + methodCallerClassName+"::"+callingMethodName+"\",\"" + 
														callingMethodLibName+"\", \""+methodCalledClassName+"::"+calledMethodName+"\", \"" + calledMethodLibName+"\", 0, 1);} $_ = $proceed($$);}");
												else
													m.replace("{if ($0 != null) { " + codeToAdd 
														+ "instrumentation.CallTrackerTransformer.addRuntimeType(\""+methodCallerClassName+"\", \""+callingMethodName+
														"\", \""+callingMethodLibName+"\", $0.getClass(), \""+dynCalledMethodName+"\");}" + " $_ = $proceed($$);}");
											}	
										} catch (Exception e) {
											System.out.println(e);
										}
									}
					            }
					        });
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
	
	public static void addRuntimeType(String methodCallerClassName, String callingMethodName, String callingMethodLibName,
			Class<?> methodCalledClass, String calledMethodName) {
		String calledMethodLibName = "";
		String methodCalledClassName = methodCalledClass.getName();
		Entry<String, List<String>> unknownEntry = new AbstractMap.SimpleEntry<String, List<String>>("unknownLib", new ArrayList<String>());
		try {
			calledMethodLibName = libsToClasses.entrySet().stream()
			.filter(map -> map.getValue().contains(methodCalledClassName))
			.findAny().orElse(unknownEntry).getKey();
			if (calledMethodLibName.equals(unknownEntry.getKey()) && methodCalledClass.getPackage()!=null) {
				int index1 = methodCalledClassName.indexOf(".");
				int index2 = methodCalledClassName.indexOf(".", methodCalledClassName.indexOf(".") + 1);
				calledMethodLibName = index2>0 ? methodCalledClassName.substring(0, index2) : 
					(index1>0 ? methodCalledClassName.substring(0, index1) : "unknownLib");
			}
		} catch (NullPointerException e) {
			System.out.println(e);e.printStackTrace();
		}
		if (!callingMethodLibName.equals(unknownEntry.getKey()) && !calledMethodLibName.equals(unknownEntry.getKey()) && !callingMethodLibName.equals(calledMethodLibName)) {
			updateInterLibraryCounts(methodCallerClassName+"::"+callingMethodName, callingMethodLibName,
					methodCalledClassName+"::"+methodCalledClassName+"."+calledMethodName, calledMethodLibName, 0, 1);
		}
	}
	
	public static void updateLibsToMethods(String calledMethodLibName, String methodCalledClassName, String calledMethodName) {
		String calledMethodFullName = methodCalledClassName+"::"+calledMethodName;
		if (!libsToMethods.containsKey(calledMethodLibName))
			libsToMethods.put(calledMethodLibName, new ArrayList<String>(Arrays.asList(calledMethodFullName)));
		else if (!libsToMethods.get(calledMethodLibName).contains(calledMethodFullName))
			libsToMethods.get(calledMethodLibName).add(calledMethodFullName);
	}
	
	public static void updateInterLibraryCounts(String callerMethod, String callerLibrary, String calleeMethod, String calleeLibrary,
			int static_count, int dynamic_count) {
		InterLibraryCountsKey key = new InterLibraryCountsKey(callerMethod, callerLibrary, calleeMethod, calleeLibrary);
		interLibraryCounts.putIfAbsent(key, new InterLibraryCountsValue(0, 0));
		interLibraryCounts.get(key).staticCount+=static_count;
		interLibraryCounts.get(key).dynamicCount+=dynamic_count;
	}
}
