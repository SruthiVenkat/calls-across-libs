package instrumentation.apisurfaceanalysis;

import java.util.HashSet;
import java.util.Map;

import javassist.CtMethod;

public class ServicesInfoKey {
	String interfaceName;
	String interfaceLib;
	Map<String, String> implLibs;
	Map<String, HashSet<CtMethod>> implMethodsNotInInterface;
	
	ServicesInfoKey(String interfaceName, String interfaceLib, Map<String, String> implLibs, Map<String, HashSet<CtMethod>> implMethodsNotInInterface) {
		this.interfaceName = interfaceName;
		this.interfaceLib = interfaceLib;
		this.implLibs = implLibs;
		this.implMethodsNotInInterface = implMethodsNotInInterface;
	}
}
