package packageA;

import java.util.List;
import java.util.ArrayList;

public class GenericClass<T extends Number> {
	public T genericField;
	public List<T> genericList = new ArrayList<T>();
	
	public GenericClass(T genericField) {  this.genericField = genericField;  }
}
