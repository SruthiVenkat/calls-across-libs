package packageB.baz;

public class External implements CommonInterface{
	// these methods are for public use
	
	public String getHi() {
		return "hi";
	}

	public String getText() {
		return "hello";
	}
	
	// I can access Internal's internalMethod here - same package baz
	public int computeSomething() {
		Internal i = new Internal();
		return i.internalMethod() * 5;
	}
	
	
	public static CommonInterface getDetails() {
		// since CommonInterface is exposed to outside, returning CommonInterface type is ok
		
		// this could escape to the outside since Internal also implements CommonInterface
		return new Internal();
	}

	// this method is the one you expect to be returned, it is exposed
	public String getWhichClassString() {
		return "External";
	}
}
