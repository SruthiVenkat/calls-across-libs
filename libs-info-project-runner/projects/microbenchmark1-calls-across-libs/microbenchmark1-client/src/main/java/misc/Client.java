package misc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;  

import packageA.RandomTypes;
import packageA.SubClass;
import packageA.SuperClass;
import packageA.GenericClass;
import packageA.CustomAnnotation;
import packageB.bar.Middle;
import packageB.baz.External;

@CustomAnnotation
public class Client {

	public static void main(String[] args) {
		miscClientMethod();
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
		//System.out.println(sub.field1);
		sub.i = 10;
		System.out.println(sub.i);
		sub.strs.add("test");
		
		// generic field access
		GenericClass <Integer> genObj = new GenericClass<Integer>(5);
		System.out.println(genObj.genericField);
		
		// reflective field access
		try {
			Field field = SubClass.class.getField("field1");
			field.set(field, "bnm");
			System.out.println(field.get(sub));
		} catch (Exception e) { }

		// packageB
		External e = new External();
		System.out.println("packageB External - "+e.getText());
				
		Middle m = new Middle();
		System.out.println("packageB Middle - "+m.getValue1());
		System.out.println("packageB Middle - "+m.getValue2());
		
		java.lang.reflect.Method method;
		try {
			System.out.println("packageB External - reflection - ");
		  method = External.class.getMethod("getText");
		  String reString = (String) method.invoke(e);
		  System.out.println(reString);
		} catch (SecurityException e1) { } catch (NoSuchMethodException e2) { } catch (IllegalArgumentException e1) { } catch (IllegalAccessException e2) { } catch (InvocationTargetException e3) { }
	}

	public static void clientMethod1(RandomTypes rtypes) {
		System.out.println("RandomTypes int - "+rtypes.getInt());
		System.out.println("RandomTypes double - "+rtypes.getDouble());
	}
}
