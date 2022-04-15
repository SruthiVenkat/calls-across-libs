package keys;

public class SetAccessibleCallsKey {
	public String callerLib;
	public String callerMethod;
	public String calledOnType;
	public String visibility;
	public String fieldSignature;
	public String calledOnObjName;
	public String libName;
	public boolean setAccessible;

	public SetAccessibleCallsKey(String callerLib, String callerMethod, String calledOnType, String visibility, String fieldSignature, 
			String calledOnObjName, String libName, boolean setAccessible) {
		this.callerLib = callerLib;
		this.callerMethod = callerMethod;
		this.calledOnType = calledOnType;
		this.visibility = visibility;
		this.fieldSignature = fieldSignature;
		this.calledOnObjName = calledOnObjName;
		this.libName = libName;
		this.setAccessible = setAccessible;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) return false;
		if (obj.getClass() != this.getClass()) return false;
		SetAccessibleCallsKey keyObj = (SetAccessibleCallsKey) obj;
		return (keyObj.callerLib.equals(this.callerLib) && keyObj.callerMethod.equals(this.callerMethod) && keyObj.calledOnType.equals(this.calledOnType)
				&& keyObj.visibility.equals(this.visibility) && keyObj.fieldSignature.equals(this.fieldSignature) 
				&& keyObj.calledOnObjName.equals(this.calledOnObjName) && keyObj.libName.equals(this.libName) && keyObj.setAccessible==this.setAccessible);
	}

	@Override
	public int hashCode() {
		return callerLib.hashCode() + callerMethod.hashCode() + calledOnType.hashCode() + visibility.hashCode()
			+ fieldSignature.hashCode() + calledOnObjName.hashCode() + libName.hashCode() + (setAccessible ? 1 : 0);
	}
}
