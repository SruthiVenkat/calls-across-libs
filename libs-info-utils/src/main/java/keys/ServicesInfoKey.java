package keys;

import java.util.HashSet;
import java.util.Map;

import javassist.CtMethod;

public class ServicesInfoKey {
	public String interfaceName;
	public String interfaceLib;
	
	public ServicesInfoKey(String interfaceName, String interfaceLib) {
		this.interfaceName = interfaceName;
		this.interfaceLib = interfaceLib;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj == null) return false;
		if (obj.getClass() != this.getClass()) return false;
		ServicesInfoKey keyObj = (ServicesInfoKey) obj;
		return (keyObj.interfaceName.equals(this.interfaceName)
				&& keyObj.interfaceLib.equals(this.interfaceLib));
	}

	@Override
	public int hashCode() {
		return interfaceName.hashCode() + interfaceLib.hashCode();
	}
}
