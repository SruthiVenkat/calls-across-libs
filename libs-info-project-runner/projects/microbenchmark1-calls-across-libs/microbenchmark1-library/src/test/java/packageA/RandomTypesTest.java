package packageA;

import org.junit.Test;

public class RandomTypesTest {
	@Test
	public void randomTypesTestMethod() {
		RandomTypes randomTypes = new RandomTypes();
		System.out.println(randomTypes.getBoolean());
		System.out.println(randomTypes.getString(25));
	}
}
