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
		miscClientMethod();
	}
	
	public static void miscClientMethod() {
		// packageA - dispatch
		SuperClass supCls = new SubClass();
		System.out.print("Dispatch - "); supCls.method1();
		System.out.print("Static - "); SubClass.method2();
		System.out.print("Dispatch + Static - "); supCls.method2();
		System.out.println(supCls.field1);
		
		// field access
		SubClass subCls = new SubClass();
		System.out.println(subCls.field1);
		subCls.i = 10;
		System.out.println(subCls.i);
		subCls.strs.add("test");
		
		// generic field access
		GenericClass <Integer> genObj = new GenericClass<Integer>(5);
		System.out.println(genObj.genericField);
		
		// reflective field access
		try {
			Field field = SubClass.class.getField("field1");
			System.out.println(field.get(subCls));
			System.out.println(field.get(supCls));
			field.set(supCls, "bnm");
			System.out.println(field.get(subCls));
			System.out.println(field.get(supCls));
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
			SuperClass supCls2 = new SuperClass();
			java.lang.reflect.Method privateMethod = SuperClass.class.getDeclaredMethod("checkSetAccessibility");
			privateMethod.setAccessible(true);
			privateMethod.invoke(supCls2);
		
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
		    .forEach(n -> {System.out.println(e.getHi()+n+subCls.field1);});
		    
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
