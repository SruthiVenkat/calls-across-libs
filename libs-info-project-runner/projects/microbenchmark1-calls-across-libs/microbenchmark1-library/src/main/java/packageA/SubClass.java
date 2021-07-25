package packageA;

import java.util.List;
import java.util.ArrayList;

public class SubClass extends SuperClass {
	public String field1 = "abc";
	public Integer i = new Integer(0);
	public List<String> strs = new ArrayList<String>();
	public void method1() {
		System.out.println("SubClass");
	}
	
	public static void method2() {
		System.out.println("SubClass Static");
	}
}
