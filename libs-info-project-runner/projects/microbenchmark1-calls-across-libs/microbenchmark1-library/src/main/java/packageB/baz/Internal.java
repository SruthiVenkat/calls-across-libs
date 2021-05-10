package packageB.baz;

public class Internal implements CommonInterface {
	public String input;

	// initial implementation
	///*
	public int internalMethod() {
		return 5;
	}
	//*/

	// breaking change
	/*
	protected int internalMethod() {
		if (input.length() < 2) {
			return -1; // maybe caller expects >0
		}
		return 5;
	}
	*/
	
	
	
	// initial implementation
	///*
	public String getWhichClassString() {
		return "Internal";
	}
	//*/
	// breaking change
	/*
	public String getWhichClassString() {
		if (input.length() < 2) {
			throw new Exception();
		}
		return "Internal";
	}
	*/
}
