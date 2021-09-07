package instrumentation.apisurfaceanalysis;

class InterLibraryFieldsKey {
	String calleeLib;
	String virtualClass;
	String actualClass;
	String fieldName;
	String fieldSignature;
	boolean isStatic;
	String visibility;
	String libName;
	boolean reflective;

	InterLibraryFieldsKey(String calleeLib, String virtualClass, String actualClass, String fieldName, String fieldSignature,
			boolean isStatic, String visibility, String libName, boolean reflective) {
		this.calleeLib = calleeLib;
		this.virtualClass = virtualClass;
		this.actualClass = actualClass;
		this.fieldName = fieldName;
		this.fieldSignature = fieldSignature;
		this.isStatic = isStatic;
		this.visibility = visibility;
		this.libName = libName;
		this.reflective = reflective;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) return false;
		if (obj.getClass() != this.getClass()) return false;
		InterLibraryFieldsKey keyObj = (InterLibraryFieldsKey) obj;
		return (keyObj.calleeLib.equals(this.calleeLib) && keyObj.virtualClass.equals(this.virtualClass) && keyObj.actualClass.equals(this.actualClass)
				&& keyObj.fieldName.equals(this.fieldName) && keyObj.fieldSignature.equals(this.fieldSignature) && keyObj.reflective==this.reflective
				&& keyObj.isStatic==this.isStatic && keyObj.visibility.equals(this.visibility) && keyObj.libName.equals(this.libName));
	}

	@Override
	public int hashCode() {
		return calleeLib.hashCode() + virtualClass.hashCode() + actualClass.hashCode() + fieldName.hashCode()
			+ fieldSignature.hashCode() + (isStatic ? 1 : 0) + visibility.hashCode() + libName.hashCode() + (reflective ? 1 : 0);
	}
}