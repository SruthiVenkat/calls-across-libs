package packageA;

public class SuperClass {
	private static void checkSetAccessibility() {
		System.out.println("accessing private method - checkSetAccessibility");
	}
	
	public String field1 = "def";
	public void method1() {
		System.out.println("SuperClass");
	}
	
	public static void method2() {
		System.out.println("SuperClass Static");
	}
}
