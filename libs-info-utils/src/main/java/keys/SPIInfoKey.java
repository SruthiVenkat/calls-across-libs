package keys;

public class SPIInfoKey {
	public String callerMethodName;
	public String callerMethodLib;
	public String interfaceName;
	public String interfaceLib;
	public String calledMethodName;
	public String implName;
	public String implLib;
	
	public SPIInfoKey(String callerMethodName, String callerMethodLib, String interfaceName, String interfaceLib,
		String calledMethodName, String implName, String implLib) {
		this.callerMethodName = callerMethodName;
		this.callerMethodLib = callerMethodLib;
		this.interfaceName = interfaceName;
		this.interfaceLib = interfaceLib;
		this.calledMethodName = calledMethodName;
		this.implName = implName;
		this.implLib = implLib;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) return false;
		if (obj.getClass() != this.getClass()) return false;
		SPIInfoKey keyObj = (SPIInfoKey) obj;
		return (keyObj.callerMethodName.equals(this.callerMethodName) && keyObj.callerMethodLib.equals(this.callerMethodLib) && keyObj.interfaceName.equals(this.interfaceName)
				&& keyObj.interfaceLib.equals(this.interfaceLib) && keyObj.calledMethodName.equals(this.calledMethodName) 
				&& keyObj.implName.equals(this.implName) && keyObj.implLib.equals(this.implLib));
	}

	@Override
	public int hashCode() {
		return callerMethodName.hashCode() + callerMethodLib.hashCode() + interfaceName.hashCode() + interfaceLib.hashCode()
			+ calledMethodName.hashCode() + implName.hashCode() + implLib.hashCode();
	}
}
