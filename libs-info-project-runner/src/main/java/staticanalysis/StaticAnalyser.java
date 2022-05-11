package staticanalysis;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import instrumentation.util.NameUtility;
import instrumentation.util.TrieUtil;
import instrumentation.apisurfaceanalysis.CallTrackerTransformer;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.ClassFile;
import javassist.bytecode.Descriptor;
import javassist.bytecode.SignatureAttribute;
import javassist.bytecode.annotation.Annotation;
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
import keys.SetAccessibleCallsKey;

public class StaticAnalyser {
    static ClassPool classPool;
    static String runningLibrary;
    static Map<String, String> classes = new HashMap<String, String>();
    static Entry<String, TrieUtil> unknownEntry = new AbstractMap.SimpleEntry<String, TrieUtil>("unknownLib", new TrieUtil());
    static Map<String, String> classesToLibs = new HashMap<String, String>();
    static Map<String, TrieUtil> libsToClasses = new HashMap<String, TrieUtil>();
    
    public static ConcurrentHashMap<InterLibraryCallsKey, Integer> interLibraryCalls = new ConcurrentHashMap<InterLibraryCallsKey, Integer>();
    public static ConcurrentHashMap<InterLibraryFieldsKey, Integer> interLibraryFields = new ConcurrentHashMap<InterLibraryFieldsKey, Integer>();
    public static ConcurrentHashMap<InterLibrarySubtypingKey, Integer> interLibrarySubtyping = new ConcurrentHashMap<InterLibrarySubtypingKey, Integer>();
    public static ConcurrentHashMap<InterLibraryAnnotationsKey, Integer> interLibraryAnnotations = new ConcurrentHashMap<InterLibraryAnnotationsKey, Integer>();
    public static Set<InterLibraryClassUsageKey> interLibraryClassUsage = new HashSet<InterLibraryClassUsageKey>();
    public static Set<SetAccessibleCallsKey> setAccessibleCallsInfo = new HashSet<SetAccessibleCallsKey>();
    public static Map<String, List<String>> superToSubClasses = new HashMap<String, List<String>>();
    public static enum Label { CLIENTTOLIB, LIBTOCLIENT, LIBTOLIB, INTRALIB };
    public static EnumMap<Label, String> labelMap = new EnumMap<Label, String>(Label.class);

    public static void getAllClassesStatically() {
		classPool = ClassPool.getDefault();
		
		labelMap.put(Label.CLIENTTOLIB, "ClientToLib");
    	labelMap.put(Label.LIBTOCLIENT, "LibToClient");
    	labelMap.put(Label.LIBTOLIB, "LibToLib");
    	labelMap.put(Label.INTRALIB, "IntraLib");
		
		Path configPath = Paths.get(new File(".").getAbsolutePath());
	    while (!configPath.endsWith("calls-across-libs"))
	            configPath = configPath.getParent();
	    String configPathName = configPath.toString()+"/src/main/resources/config.properties";
	    // read mapping of libraries to classes
	    try (FileReader input = new FileReader(configPathName)) {
	            Properties prop = new Properties();
	            prop.load(input); 
	            String libsInfoPath = prop.getProperty("libsInfoPath");
	            runningLibrary = prop.getProperty("runningLibrary");
                String row;
                BufferedReader reader = new BufferedReader(new FileReader(libsInfoPath));
                while ((row = reader.readLine()) != null) {
                    String[] data = row.split("\t");
                    if (data.length >= 5) {
                        TrieUtil t = new TrieUtil();
                        for (String str : data[4].split(":")) {
                        	classesToLibs.put(str, data[0]);
                        	classes.put(str, data[1]);
                        	t.insert(str);
                        }
                        libsToClasses.put(data[0], t);
                    }
                }     
	            reader.close();                        
	    } catch (IOException ex) {
	        System.out.println(ex);
	    }

	    Set<String> uniqJars = new HashSet<String>(classes.values());
    	for (String jar: uniqJars) {
    		try {
    			classPool.appendClassPath(jar);
			} catch (NotFoundException e) {
			} catch (Exception e) {
				e.printStackTrace();
			}
    	}
    	for (String cls: classes.keySet()) {
    		try {
    			CtClass ctcls = classPool.get(cls);
    			getClassHierarchies(ctcls);
			} catch (NotFoundException e) {
			}
    	}
    	for (String cls: classes.keySet()) {
    		try {
    			CtClass ctcls = classPool.get(cls);
    			if (!ctcls.isFrozen()) 
    				staticAnalysis(ctcls);
			} catch (ClassNotFoundException e) {
			} catch (NotFoundException e) {
			}
    	}
    	writeResults();
    }
    
    
    public static void staticAnalysis(CtClass ctClass) throws ClassNotFoundException {
        String methodCallerClassName = ctClass.getName();   
        final String callingMethodLibName = findLibrary(methodCallerClassName);
        if(callingMethodLibName.equals(unknownEntry.getKey())) {
                return;
        }

        CtMethod[] methods = ctClass.getDeclaredMethods();
        try {
            for (CtMethod method : methods) {
            	if (!CallTrackerTransformer.isNative(method))
            		handleMethodInvocationsStatic(method, false, methodCallerClassName, callingMethodLibName);
            }

            CtConstructor[] constructors = ctClass.getDeclaredConstructors();
            for (CtConstructor constructor : constructors) {
            	if (!CallTrackerTransformer.isNative(constructor))
            		handleMethodInvocationsStatic(constructor, true, methodCallerClassName, callingMethodLibName);
            }
        } catch (CannotCompileException e) {
		}

        // 4. Annotation Usage - class (& method, field)
        try {
			ClassFile cf = ctClass.getClassFile();
			AnnotationsAttribute ainfo = (AnnotationsAttribute)
	                cf.getAttribute(AnnotationsAttribute.invisibleTag);  
	        AnnotationsAttribute ainfo2 = (AnnotationsAttribute)
	                cf.getAttribute(AnnotationsAttribute.visibleTag);  
	        
	        if (ainfo!=null) {
	        	Annotation[] anns = ainfo.getAnnotations();
		        for (Annotation a: anns) {
		        	addAnnotationKey(a, methodCallerClassName, callingMethodLibName);
		        }
	        }
	        if (ainfo2!=null) {
	        	Annotation[] anns = ainfo2.getAnnotations();
		        for (Annotation a: anns) {
		        	addAnnotationKey(a, methodCallerClassName, callingMethodLibName);
		        }
	        }
        } catch (NoClassDefFoundError e) {
			System.out.println(e);
		} catch (Exception e) {
			System.out.println(e);
		}
	}
    
    public static void getClassHierarchies(CtClass ctClass) {
    	String methodCallerClassName = ctClass.getName();  
    	final String callingMethodLibName = findLibrary(methodCallerClassName);
        try {
            // 3. Subtyping ( extends / implements )
            CtClass superClass = ctClass.getSuperclass();
            if (superClass!=null) {
                String superClassName = superClass.getName();
                String superClassLib = findLibrary(superClassName);
                if (!superClassLib.equals(unknownEntry.getKey())) { // && !callingMethodLibName.equals(superClassLib)
                	int mods = superClass.getModifiers();
                    String superClassVisibility = javassist.Modifier.isPublic(mods) ? "public" : (javassist.Modifier.isPrivate(mods) ? "private" : (javassist.Modifier.isProtected(mods) ? "protected" : "default"));
                    InterLibrarySubtypingKey key = new InterLibrarySubtypingKey(methodCallerClassName, callingMethodLibName, superClassName, superClassVisibility, superClassLib);
                    interLibrarySubtyping.putIfAbsent(key, 0);
                    interLibrarySubtyping.put(key, interLibrarySubtyping.get(key) + 1);
                    InterLibraryClassUsageKey interLibraryClassUsageKey = new InterLibraryClassUsageKey(superClassName, superClassVisibility, superClassLib, "subtyping", methodCallerClassName, callingMethodLibName);
                    interLibraryClassUsage.add(interLibraryClassUsageKey);
                    
                    superToSubClasses.putIfAbsent(superClassName, new ArrayList<String>());
                    superToSubClasses.get(superClassName).add(methodCallerClassName);
                }
            }

            CtClass[] interfaces = ctClass.getInterfaces();
            if (interfaces!=null) {
                for (CtClass interfaceClass : interfaces) {
                    String interfaceName = interfaceClass.getName();
                    String interfaceLib = findLibrary(interfaceName);
                    if (!interfaceLib.equals(unknownEntry.getKey())) { //  && !callingMethodLibName.equals(interfaceLib)
                    	int mods = interfaceClass.getModifiers();
                        String interfaceVisibility = javassist.Modifier.isPublic(mods) ? "public" : (javassist.Modifier.isPrivate(mods) ? "private" : (javassist.Modifier.isProtected(mods) ? "protected" : "default"));
                        InterLibrarySubtypingKey key = new InterLibrarySubtypingKey(methodCallerClassName, callingMethodLibName, interfaceName, interfaceVisibility, interfaceLib);
                        interLibrarySubtyping.putIfAbsent(key, 0);
                        interLibrarySubtyping.put(key, interLibrarySubtyping.get(key) + 1);
                        InterLibraryClassUsageKey interLibraryClassUsageKey = new InterLibraryClassUsageKey(interfaceName, interfaceVisibility, interfaceLib, "subtyping", methodCallerClassName, callingMethodLibName);
                        interLibraryClassUsage.add(interLibraryClassUsageKey);
                        
                        superToSubClasses.putIfAbsent(interfaceName, new ArrayList<String>());
                        superToSubClasses.get(interfaceName).add(methodCallerClassName);
                    }
                }
            }
	    } catch (NotFoundException e) {
		}
	}
    
    public static void addAnnotationKey(Annotation a, String methodCallerClassName, String callingMethodLibName) {
    	String annotationName = a.getTypeName();
        String annotationLib = findLibrary(annotationName);
        if (!annotationLib.equals(unknownEntry.getKey()) && !callingMethodLibName.equals(unknownEntry.getKey())) {
                InterLibraryAnnotationsKey key = new InterLibraryAnnotationsKey(methodCallerClassName, "-", "-", callingMethodLibName, annotationName, "", annotationLib);
                interLibraryAnnotations.putIfAbsent(key, 0);
                interLibraryAnnotations.put(key, interLibraryAnnotations.get(key) + 1);
                InterLibraryClassUsageKey interLibraryClassUsageKey = new InterLibraryClassUsageKey(annotationName, "", annotationLib, "annotation", methodCallerClassName, callingMethodLibName);
                interLibraryClassUsage.add(interLibraryClassUsageKey);
        }
    }
    
    public static void handleMethodInvocationsStatic(CtBehavior method, boolean isConstructor, String methodCallerClassName, 
            String callingMethodLibName) throws CannotCompileException {
    	if (!CallTrackerTransformer.isNative(method) && !CallTrackerTransformer.isAbstract(method)) {
	        String callingMethodName = method.getName();
	        String callingDescriptorName = isConstructor ? NameUtility.getDescriptor((CtConstructor)method) : NameUtility.getDescriptor((CtMethod)method);
	        int callerClassMods = method.getDeclaringClass().getModifiers();
	        String callerClassVisibility = javassist.Modifier.isPublic(callerClassMods) ? "public" : (javassist.Modifier.isPrivate(callerClassMods) ? "private" : (javassist.Modifier.isProtected(callerClassMods) ? "protected" : "default"));
	        
	        // Annotations - methods
	        Object[] methodAnnotations = null;
			try {
				methodAnnotations = method.getAnnotations();
			} catch (ClassNotFoundException e2) {
			} catch (NoClassDefFoundError e) {
			}
	        if (methodAnnotations!=null) {
	                    for (Object annotationObj : methodAnnotations) {
	                            String annotationName = annotationObj.getClass().getName();
	                            String annotationLib = findLibrary(annotationName);
	
	                            if (!annotationLib.equals(unknownEntry.getKey()) && !callingMethodLibName.equals(unknownEntry.getKey())) {
	                                    int annMods = annotationObj.getClass().getModifiers();
	                                    String annotationVisibility = Modifier.isPublic(annMods) ? "public" : (Modifier.isPrivate(annMods) ? "private" : (Modifier.isProtected(annMods) ? "protected" : "default"));
	                                    InterLibraryAnnotationsKey methodKey = new InterLibraryAnnotationsKey("-", methodCallerClassName+"::"+callingMethodName+callingDescriptorName, "-", callingMethodLibName, annotationName, annotationVisibility, annotationLib);
	                                    interLibraryAnnotations.putIfAbsent(methodKey, 0);
	                                    interLibraryAnnotations.put(methodKey, interLibraryAnnotations.get(methodKey) + 1);
		                                InterLibraryClassUsageKey interLibraryClassUsageKey = new InterLibraryClassUsageKey(annotationName, annotationVisibility, annotationLib, "annotation", methodCallerClassName, callingMethodLibName);
		                                interLibraryClassUsage.add(interLibraryClassUsageKey);
	                            }
	                    }
	    }

        method.instrument(
	            new ExprEditor() {
	            	// 1. Method Invocations
	                public void edit(MethodCall m) throws CannotCompileException {
	                    try {
	                            if (method.getName().equals(m.getMethodName())
	                            		&& callingMethodName.equals(m.getClassName()))
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
	                           
	        	                // method parameter types
	        	                for (CtClass type: m.getMethod().getParameterTypes()) {
	        	                	int modifiers = type.getModifiers();
	                                String visibility = javassist.Modifier.isPublic(modifiers) ? "public" : (javassist.Modifier.isPrivate(modifiers) ? "private" : (javassist.Modifier.isProtected(modifiers) ? "protected" : "default"));
	                                
	                                InterLibraryClassUsageKey interLibraryClassUsageKey = new InterLibraryClassUsageKey(type.getName(), visibility, findLibrary(type.getName()), "method parameter", methodCallerClassName, callingMethodLibName);
	                                interLibraryClassUsage.add(interLibraryClassUsageKey);
	                                
	                                if (superToSubClasses.keySet().contains(type.getName())) {
	                                	List<String> subclss = superToSubClasses.get(type.getName());
		                        		if (subclss!=null) {
		                            		for (String subClsName: superToSubClasses.get(type.getName())) {
		                            			interLibraryClassUsageKey = new InterLibraryClassUsageKey(subClsName, "-", findLibrary(subClsName), "method parameter", methodCallerClassName, callingMethodLibName);
		    	                                interLibraryClassUsage.add(interLibraryClassUsageKey);
		                            		}
		                        		}
	                                }
	        	                }
	                                          
	                            // method invocations
	                            if(!calledMethodLibName.equals(unknownEntry.getKey())) {
	                                    int modifiers = m.getMethod().getModifiers();
	                                    String mVisibility = javassist.Modifier.isPublic(modifiers) ? "public" : (javassist.Modifier.isPrivate(modifiers) ? "private" : (javassist.Modifier.isProtected(modifiers) ? "protected" : "default"));
	                                    
	                                    String label = callingMethodLibName.equals(calledMethodLibName) ? labelMap.get(Label.INTRALIB) 
	                                    		: (callingMethodLibName.equals(runningLibrary) ? labelMap.get(Label.CLIENTTOLIB)
	                                    				: (calledMethodLibName.equals(runningLibrary) ? labelMap.get(Label.LIBTOCLIENT) 
	                                    						:  labelMap.get(Label.LIBTOLIB)));

	                                    // static
	                                    if (CallTrackerTransformer.checkAddCondition(callingMethodLibName, calledMethodLibName)) {                                                		
	                                    		updateInterLibraryCounts(methodCallerClassName+"::"+callingMethodName+callingDescriptorName,
	                                                callingMethodLibName, mVisibility, methodCalledClassName+"::"+calledMethodName+calledDescriptorName, "-",
	                                                calledMethodLibName, "-", callerClassVisibility, 1, false, false, label);
	                                    }
	                                    if (superToSubClasses.keySet().contains(m.getClassName())) {
		                                	List<String> subclss = superToSubClasses.get(m.getClassName());
			                        		if (subclss!=null) {
			                            		for (String subClsName: superToSubClasses.get(m.getClassName())) {
			                            			String subClsLib = findLibrary(subClsName);
			                            			if (CallTrackerTransformer.checkAddCondition(callingMethodLibName, subClsLib)) {
			                            				CtMethod subMethod = classPool.get(subClsName).getDeclaredMethod(calledMethodName);
			                            				if (subMethod!=null) {
				                                    		updateInterLibraryCounts(methodCallerClassName+"::"+callingMethodName+callingDescriptorName,
				                                                callingMethodLibName, mVisibility, subClsName+"::"+calledMethodName+calledDescriptorName, "-",
				                                                subClsLib, "-", callerClassVisibility, 1, false, false, label);
			                            				}
			                            			}
			                            		}
			                        		}
		                                }
	                            	} 
	                            } catch (NotFoundException nfe) {
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
	                                    // Annotations - fields
	                                    Object[] annotations = null;
	                                    try {
	                                    	annotations = f.getField().getAnnotations();
	                                    } catch (ClassNotFoundException e2) {
	                        			} catch (NoClassDefFoundError e) {
	                        			}
	                                            if (annotations!=null) {
	                                                    for (Object annotationObj : annotations) {
	                                                            String annotationName = annotationObj.getClass().getName();
	                                                            String annotationLib = findLibrary(annotationName);
	                                                            if (!annotationLib.equals(unknownEntry.getKey())) { //  && !fieldLib.equals(annotationLib)
	                                                                    int annMods = annotationObj.getClass().getModifiers();
	                                                                    String annotationVisibility = Modifier.isPublic(annMods) ? "public" : (Modifier.isPrivate(annMods) ? "private" : (Modifier.isProtected(annMods) ? "protected" : "default"));
	                                                                    InterLibraryAnnotationsKey fieldKey = new InterLibraryAnnotationsKey("-", "-", fieldClass+"::"+f.getField().getName()+":"+f.getSignature(), fieldLib, annotationName, annotationVisibility, annotationLib);
	                                                                    interLibraryAnnotations.putIfAbsent(fieldKey, 0);
	                                                                    interLibraryAnnotations.put(fieldKey, interLibraryCalls.get(fieldKey) + 1);
		                                                                InterLibraryClassUsageKey interLibraryClassUsageKey = new InterLibraryClassUsageKey(annotationName, annotationVisibility, annotationLib, "annotation", fieldClass, fieldLib);
		                                                                interLibraryClassUsage.add(interLibraryClassUsageKey);
	                                                            }
	                                                    }
	                                            }
	                                            
	                                            // static
	                                                InterLibraryFieldsKey key = new InterLibraryFieldsKey(methodCallerClassName, callingMethodLibName, "static", fieldClass, fieldClass+"::"+f.getField().getName(), sig, f.isStatic(), fieldVisibility, fieldLib, false);
	                                                interLibraryFields.putIfAbsent(key, 0);
	                                                if (superToSubClasses.keySet().contains(f.getClassName())) {
	        		                                	List<String> subclss = superToSubClasses.get(f.getClassName());
	        			                        		if (subclss!=null) {
	        			                            		for (String subClsName: superToSubClasses.get(f.getClassName())) {
	        			                            			String subClsLib = findLibrary(subClsName);
	        			                            			CtField subField = classPool.get(subClsName).getField(f.getFieldName());
	    			                            				if (subField!=null) {
		        			                            			if (CallTrackerTransformer.checkAddCondition(callingMethodLibName, subClsLib)) {                                                		
		        			                            				key = new InterLibraryFieldsKey(methodCallerClassName, callingMethodLibName, "static", subClsName, subClsName+"::"+f.getField().getName(), sig, f.isStatic(), fieldVisibility, subClsLib, false);
		        		                                                interLibraryFields.putIfAbsent(key, 0);
		        			                            			}
	    			                            				}
	        			                            		}
	        			                        		}
	        		                                }
	                            }
	                        } catch (NotFoundException e) {
	                        }
	                }
	                
	                // instantiations
	                public void edit(NewExpr newExpr) throws CannotCompileException {
	                    String newExprLib = findLibrary(newExpr.getClassName());
	                    if (!newExprLib.equals(unknownEntry.getKey())) { //  && !callingMethodLibName.equals(newExprLib)
	                            int mods = newExpr.getClass().getModifiers();
	                            String newExprVisibility = Modifier.isPublic(mods) ? "public" : (Modifier.isPrivate(mods) ? "private" : (Modifier.isProtected(mods) ? "protected" : "default"));
	                                    
	                            InterLibraryClassUsageKey interLibraryClassUsageKey = new InterLibraryClassUsageKey(newExpr.getClassName(), newExprVisibility, newExprLib, "instantiation", methodCallerClassName, callingMethodLibName);
	                            interLibraryClassUsage.add(interLibraryClassUsageKey);
	                    }
	                }
	                
	                // casts
	                public void edit(Cast c) throws CannotCompileException {
	                    try {
	                            String castLib = findLibrary(c.getType().getName());
	                            if (!castLib.equals(unknownEntry.getKey())) { //  && !callingMethodLibName.equals(castLib)
	                                    int mods = c.getType().getModifiers();
	                                    String castVisibility = Modifier.isPublic(mods) ? "public" : (Modifier.isPrivate(mods) ? "private" : (Modifier.isProtected(mods) ? "protected" : "default"));
	                                            
	                                    InterLibraryClassUsageKey interLibraryClassUsageKey = new InterLibraryClassUsageKey(c.getType().getName(), castVisibility, castLib, "cast", methodCallerClassName, callingMethodLibName);
	                                    interLibraryClassUsage.add(interLibraryClassUsageKey);
	                            }
	                    } catch (NotFoundException e) {}
	                }                           
	            });
		    }
	}
    
    public static void updateInterLibraryCounts(String callerMethod, String callerLibrary, String calleeVisibility, String virtualCalleeMethod, String actualCalleeMethod, 
            String virtualCalleeMethodLibString, String actualCalleeMethodLibString, String calleeClassVis, int count, boolean reflective, boolean dynamicProxy, String label) {
    	InterLibraryCallsKey key = new InterLibraryCallsKey(callerMethod, callerLibrary, calleeVisibility, virtualCalleeMethod, virtualCalleeMethodLibString,
	                    actualCalleeMethod, actualCalleeMethodLibString, reflective, dynamicProxy, label);
	    interLibraryCalls.putIfAbsent(key, 0);
	    interLibraryCalls.put(key, interLibraryCalls.get(key) + count);
	    InterLibraryClassUsageKey interLibraryClassUsageKey = new InterLibraryClassUsageKey(actualCalleeMethod.split("::")[0], calleeClassVis, actualCalleeMethodLibString, "method call", callerMethod.split("::")[0], callerLibrary);
	    interLibraryClassUsage.add(interLibraryClassUsageKey);
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
    
    public static void writeResults() {
    	Path configPath = Paths.get(new File(".").getAbsolutePath());
        while (!configPath.endsWith("calls-across-libs"))
            configPath = configPath.getParent();
        String configPathName = configPath.toString()+"/src/main/resources/config.properties";
        try (FileReader input = new FileReader(configPathName)) {
            Properties prop = new Properties();
            prop.load(input);
            String staticInvocationsOutputPath = prop.getProperty("staticInvocationsOutputPath");
            String fieldsOutputPath = prop.getProperty("fieldsOutputPath");
            String subtypingOutputPath = prop.getProperty("subtypingOutputPath");
            String annotationsOutputPath = prop.getProperty("annotationsOutputPath");
            String setAccessibleCallsPath = prop.getProperty("setAccessibleCallsPath");
            String classesUsageInfoPath = prop.getProperty("classesUsageInfoPath");
            String libsInfoPath = prop.getProperty("libsInfoPath");
            
            System.out.println("Adding results to tsv");
            FileWriter writer = new FileWriter(staticInvocationsOutputPath, true);
            if (new File(staticInvocationsOutputPath).length() == 0)
                writer.write("Caller Method\tCaller Library\tCallee Visibility\tDeclared Callee Method\tDeclared Callee Library\tCount\tLabel\n");
            
            for (InterLibraryCallsKey ilcKey: interLibraryCalls.keySet()) {
            		writer.write(ilcKey.callerMethodString+"\t"+ ilcKey.callerMethodLibString+"\t"+ ilcKey.calleeVisibilityString+"\t"+ilcKey.virtualCalleeMethodString+"\t"+ilcKey.virtualCalleeMethodLibString
                        +"\t"+ilcKey.actualCalleeMethodString+"\t"+ilcKey.actualCalleeMethodLibString+"\t"+interLibraryCalls.get(ilcKey)+"\t"+ilcKey.reflective+"\t"+ilcKey.dynamicProxy+"\t"+ilcKey.label+"\n");
            }
            writer.flush();
            writer.close();
            writer = new FileWriter(fieldsOutputPath, true);
            if (new File(fieldsOutputPath).length() == 0)
                writer.write("Caller Class\tCaller Library\tField Name\tDeclared Class\tActual Class\tField Signature\tStatic\tVisibility\tField Library\tReflective\tCount\n");
            for (InterLibraryFieldsKey ilfKey: interLibraryFields.keySet()) {
            	writer.write(ilfKey.callerClass+"\t"+ilfKey.callerLib+"\t"+ilfKey.fieldName+"\t"+ilfKey.virtualClass+"\t"+ilfKey.actualClass+"\t"+ ilfKey.fieldSignature+"\t"+ ilfKey.isStatic+"\t"+ ilfKey.visibility+"\t"+ ilfKey.libName+"\t"+ ilfKey.reflective+"\t"+interLibraryFields.get(ilfKey)+"\n");
            }
            
            writer.flush();
            writer.close();
            writer = new FileWriter(subtypingOutputPath, true);
            if (new File(subtypingOutputPath).length() == 0)
                writer.write("SubClass\tSub Library\tSuper Class/Interface\tSuper Class/Interface Visibility\tSuper Library\tCount\n");
            for (InterLibrarySubtypingKey ils: interLibrarySubtyping.keySet()) {
                writer.write(ils.subClass+"\t"+ ils.subClassLib+"\t"+ ils.superClass+"\t"+ ils.superClassVis+"\t"+ils.superClassLib+"\t"+interLibrarySubtyping.get(ils)+"\n");
            }
            writer.flush();
            writer.close();
            writer = new FileWriter(annotationsOutputPath, true);
            if (new File(annotationsOutputPath).length() == 0)
                writer.write("Class\tMethod\tField Name:Field Signature\tAnnotated In Library\tAnnotation\tAnnotation Visibility\tAnnotation Library\tCount\n");
            for (InterLibraryAnnotationsKey ila: interLibraryAnnotations.keySet()) {
                writer.write(ila.className+"\t"+ ila.methodName+"\t"+ ila.field+"\t"+ ila.classLib+"\t"+ ila.annotationName+"\t"+ ila.annotationVis+"\t"+ila.annotationLib+"\t"+interLibraryAnnotations.get(ila)+"\n");
            }
            writer.flush();
            writer.close();
            writer = new FileWriter(setAccessibleCallsPath, true);
            if (new File(setAccessibleCallsPath).length() == 0)
                writer.write("Caller Method\tCaller Library\tsetAccessible Called On\tVisibility\tCallee Name\tField Signature\tCallee Library\n");
            for (SetAccessibleCallsKey sac: setAccessibleCallsInfo) {
                writer.write(sac.callerMethod+"\t"+ sac.callerLib+"\t"+ sac.calledOnType+"\t"+ sac.visibility+"\t"+ sac.calledOnObjName+"\t"+sac.fieldSignature+"\t"+sac.libName+"\n");
            }
            writer.flush();
            writer.close();
            writer = new FileWriter(classesUsageInfoPath, true);
            if (new File(classesUsageInfoPath).length() == 0)
                writer.write("Class Name\tClass Visibility\tClass Library\tUsage\tUsed in Class\tUsed In Library\n");
            for (InterLibraryClassUsageKey ilcu: interLibraryClassUsage) {
                writer.write(ilcu.className+"\t"+ ilcu.classVisibility+"\t"+ ilcu.classLib+"\t"+ ilcu.usageType+"\t"+ ilcu.usedInCls+"\t"+ ilcu.usedInLib+"\n");
            }
            writer.flush();
            writer.close();
            
            String[] rows = new String[(int) new File(libsInfoPath).length()]; String row; int count = 0;
            BufferedReader reader = new BufferedReader(new FileReader(libsInfoPath));
            while ((row = reader.readLine()) != null) {
                rows[count++] = row;
            }
            reader.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
		
	}
}
