package misc;

import java.lang.reflect.Proxy;
import java.net.URL;
import java.security.CodeSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;  

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import packageA.RandomTypes;
import packageA.SubClass;
import packageA.SuperClass;
import packageA.GenericClass;
import packageA.CustomAnnotation;
import packageB.bar.Middle;
import packageB.baz.CommonInterface;
import packageB.baz.External;
import pkgDynamicProxy.DynamicInvocationHandler;

@CustomAnnotation
public class Client {
	
	public static void main(String[] args) throws ClassNotFoundException, IOException {
		 
        Object o1 = new Object(); Object o2 = new Object();
        System.out.println(o1.toString()+"---"+o2.toString());
        
		Class klass2 = java.sql.Driver.class;
		URL location = klass2.getResource('/' + klass2.getName().replace('.', '/') + ".class");
		System.out.println(location.toExternalForm());
		
		Class klass1 = Class.forName("java.sql.Driver");
		System.out.println("---"+klass1.getPackage());
		System.out.println(Pattern.matches("META-INF/services", "META-INF"+File.separator+"services"));
		System.out.println(Pattern.matches("META-INF/services/q", "META-INF"+File.separator+"services"+File.separator+".*"));
		System.out.println(Pattern.matches("META-INF"+File.separator+"services"+File.separator+".*", "META-INF/services/q"));
		System.out.println(Pattern.matches("META-INF"+File.separator+"services"+".*", "META-INF/services/javax.annotation.processing.Processor"));

		miscClientMethod();
		
//		String packageName="java.sql";
//		Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(packageName.replace('.', '/'));
//		List<File> dirs = new ArrayList<File>();
//	    while (resources.hasMoreElements()) {
//	        URL resource = resources.nextElement();
//	        dirs.add(new File(resource.getFile()));
//	    }
//	    Set<String> classNames = new HashSet<String>();
//	    for (File directory : dirs) {
//	    	classNames.addAll(findClasses(directory, packageName));
//	    }
//	    System.out.println("argh"+classNames.isEmpty()+getClassesInPackage(packageName));
//	    
//	    InputStream stream = Thread.currentThread().getContextClassLoader()
//	            .getResourceAsStream(packageName.replace('.', '/'));
//	          BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
//	          reader.lines()
//	            .filter(line -> line.endsWith(".class"))
//	            .map(line -> getClass(line, packageName))
//	            .collect(Collectors.toSet());
	}
	
	private static Class getClass(String className, String packageName) {
        try {
            return Class.forName(packageName + "."
              + className.substring(0, className.lastIndexOf('.')));
        } catch (ClassNotFoundException e) {
            // handle the exception
        }
        return null;
    }
	
	public static final List<Class<?>> getClassesInPackage(String packageName) {
	    String path = packageName.replaceAll("\\.", File.separator);
	    List<Class<?>> classes = new ArrayList<>();
	    String[] classPathEntries = System.getProperty("java.class.path").split(
	            System.getProperty("path.separator")
	    );

	    String name;
	    for (String classpathEntry : classPathEntries) {
	        if (classpathEntry.endsWith(".jar")) {
	            File jar = new File(classpathEntry);
	            try {
	                JarInputStream is = new JarInputStream(new FileInputStream(jar));
	                JarEntry entry;
	                while((entry = is.getNextJarEntry()) != null) {
	                    name = entry.getName();
	                    if (name.endsWith(".class")) {
	                        if (name.contains(path) && name.endsWith(".class")) {
	                            String classPath = name.substring(0, entry.getName().length() - 6);
	                            classPath = classPath.replaceAll("[\\|/]", ".");
	                            classes.add(Class.forName(classPath));
	                        }
	                    }
	                }
	            } catch (Exception ex) {
	                // Silence is gold
	            }
	        } else {
	            try {
	                File base = new File(classpathEntry + File.separatorChar + path);
	                for (File file : base.listFiles()) {
	                    name = file.getName();
	                    if (name.endsWith(".class")) {
	                        name = name.substring(0, name.length() - 6);
	                        classes.add(Class.forName(packageName + "." + name));
	                    }
	                }
	            } catch (Exception ex) {
	                // Silence is gold
	            }
	        }
	    }
	    return classes;
	}
	
	private static Set<String> findClasses(File directory, String packageName) throws ClassNotFoundException {
		Set<String> classes = new HashSet<String>();
	    if (!directory.exists()) {
	        return classes;
	    }
	    File[] files = directory.listFiles();
	    for (File file : files) {
	        if (file.isDirectory()) {
	            assert !file.getName().contains(".");
	            classes.addAll(findClasses(file, packageName + "." + file.getName()));
	        } else if (file.getName().endsWith(".class")) {
	            classes.add(Class.forName(packageName + '.' + file.getName().substring(0, file.getName().length() - 6)).getName());
	        }
	    }
	    return classes;
	}

	
	public static void miscClientMethod() {
		// packageA - dispatch
		SuperClass sc = new SubClass();
		System.out.print("Dispatch - "); sc.method1();
		System.out.print("Static - "); SubClass.method2();
		System.out.print("Dispatch + Static - "); sc.method2();
		System.out.println(sc.field1);
		
		// field access
		SubClass sub = new SubClass();
		System.out.println(sub.field1);
		sub.i = 10;
		System.out.println(sub.i);
		sub.strs.add("test");
		
		// generic field access
		GenericClass <Integer> genObj = new GenericClass<Integer>(5);
		System.out.println(genObj.genericField);
		
		// reflective field access
		try {
			Field field = SubClass.class.getField("field1");
			System.out.println(field.get(sub));
			System.out.println(field.get(sc));
			field.set(sc, "bnm");
			System.out.println(field.get(sub));
			System.out.println(field.get(sc));
			Field field2 = GenericClass.class.getField("genericField");
			System.out.println(field2.get(genObj));
			Field field3 = GenericClass.class.getField("genericList");
			field3.set(genObj, new ArrayList<Integer>());
		} catch (Exception e) { }

		// packageB
		External e = new External();
		System.out.println("packageB External - "+e.getText());
				
		Middle m = new Middle();
		System.out.println("packageB Middle - "+m.getValue1());
		System.out.println("packageB Middle - "+m.getValue2());
		
		java.lang.reflect.Method method;
		try {
			// setAccessible
			SuperClass sc2 = new SuperClass();
			java.lang.reflect.Method privateMethod = SuperClass.class.getDeclaredMethod("checkSetAccessibility");
			privateMethod.setAccessible(true);
			privateMethod.invoke(sc2);
		
		  System.out.println("packageB External - reflection - ");
		  method = External.class.getMethod("getText");
		  String reString = (String) method.invoke(e);
		  System.out.println(reString);
		} catch (Exception ex) { ex.printStackTrace(); }
		//catch (SecurityException e1) { } catch (NoSuchMethodException e2) { } catch (IllegalArgumentException e1) { } catch (IllegalAccessException e2) { } catch (InvocationTargetException e3) { }
		
		ArrayList<Integer> numbers = new ArrayList<Integer>();
		    numbers.add(5);
		    numbers.add(9);
		    numbers.add(8);
		    numbers.add(1);
		    numbers.forEach( (n) -> { System.out.println(e.getHi()+n); } );
		RandomTypes rtypes = new RandomTypes();
		numbers
		    .stream()
		    .filter(n -> n>=5)
		    .map(n -> n+rtypes.getInt())
		    .sorted()
		    .forEach(n -> {System.out.println(e.getHi()+n+sub.field1);});
		    
		CommonInterface proxyInstance = (CommonInterface) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class[] { CommonInterface.class }, new DynamicInvocationHandler(new External()));
		System.out.println(proxyInstance.getWhichClassString());
		
		clientMethod1(rtypes);
        
	}

	public static void clientMethod1(RandomTypes rtypes) {
		System.out.println("RandomTypes int - "+rtypes.getInt());
		System.out.println("RandomTypes double - "+rtypes.getDouble());
		try {
			SubClass sub = (SubClass) new SubClass().getClass().newInstance();
			System.out.println("newInstance - SubClass");
			sub.method1();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		
		try {
            final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            for (CommonInterface o : ServiceLoader.load(CommonInterface.class, classLoader)) {
            	System.out.println("--->"+o.getClass().getName());

                External ext = (External) o;
                System.out.println("printing loaded service method text---"+ext.getWhichClassString());
                System.out.println("printing loaded service method text---"+o.getWhichClassString());
            }
        } catch (ClassCastException ex) {
            System.out.println(ex);
        }
		
		Connection con;
		try {
			con = DriverManager.getConnection("jdbc:postgresql://localhost/testdb", "postgres", "password");
			Statement stmt = con.createStatement();
			String sql = "select method_name from all_methods";
			ResultSet result = stmt.executeQuery(sql);
			while (result.next()) {
				System.out.println(result.getString(1));
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}
