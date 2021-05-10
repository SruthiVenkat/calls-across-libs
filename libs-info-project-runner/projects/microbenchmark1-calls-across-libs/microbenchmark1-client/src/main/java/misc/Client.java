package misc;

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
	}

	public static void clientMethod1(RandomTypes rtypes) {
		System.out.println("RandomTypes int - "+rtypes.getInt());
		System.out.println("RandomTypes double - "+rtypes.getDouble());
	}
}
