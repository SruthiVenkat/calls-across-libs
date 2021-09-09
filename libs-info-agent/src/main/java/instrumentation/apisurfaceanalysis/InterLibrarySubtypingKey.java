package instrumentation.apisurfaceanalysis;

class InterLibrarySubtypingKey {
	String subClass;
	String subClassLib;
	String superClass;
	String superClassVis;
	String superClassLib;

	InterLibrarySubtypingKey(String subClass, String subClassLib, String superClass, String superClassVis, String superClassLib) {
		this.subClass = subClass;
		this.subClassLib = subClassLib;
		this.superClass = superClass;
		this.superClassVis = superClassVis;
		this.superClassLib = superClassLib;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) return false;
		if (obj.getClass() != this.getClass()) return false;
		InterLibrarySubtypingKey keyObj = (InterLibrarySubtypingKey) obj;
		return (keyObj.subClass.equals(this.subClass) && keyObj.subClassLib.equals(this.subClassLib) 
				&& keyObj.superClass.equals(this.superClass) && keyObj.superClassVis.equals(this.superClassVis) && keyObj.superClassLib.equals(this.superClassLib));
	}

	@Override
	public int hashCode() {
		return subClass.hashCode() + subClassLib.hashCode() + superClass.hashCode() + superClassVis.hashCode() + superClassLib.hashCode();
	}
}
