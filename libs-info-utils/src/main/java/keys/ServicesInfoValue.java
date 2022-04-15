package keys;

import java.util.HashSet;
import java.util.Map;

import javassist.CtMethod;

public class ServicesInfoValue {
	public Map<String, String> implLibs;
	public Map<String, HashSet<CtMethod>> implMethodsNotInInterface;
	
	public ServicesInfoValue(Map<String, String> implLibs, Map<String, HashSet<CtMethod>> implMethodsNotInInterface) {
		this.implLibs = implLibs;
		this.implMethodsNotInInterface = implMethodsNotInInterface;
	}
}