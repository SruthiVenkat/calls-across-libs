package misc;

import java.lang.reflect.InvocationTargetException;

import packageA.RandomTypes;
import packageA.SubClass;
import packageA.SuperClass;
import packageB.bar.Middle;
import packageB.baz.External;

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
