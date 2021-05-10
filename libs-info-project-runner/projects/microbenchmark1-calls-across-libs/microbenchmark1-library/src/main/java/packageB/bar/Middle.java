package packageB.bar;

import packageB.baz.*;

public class Middle {
	
	public int getValue1() {
		External e = new External();
		return e.computeSomething();
	}

	public String getValue2() {
		CommonInterface c = External.getDetails();
		return c.getWhichClassString();
	}
}
