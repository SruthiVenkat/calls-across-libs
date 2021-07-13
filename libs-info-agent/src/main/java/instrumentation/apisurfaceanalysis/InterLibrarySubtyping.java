package instrumentation.apisurfaceanalysis;

class InterLibrarySubtyping {
	String subClass;
	String subClassLib;
	String superClass;
	String superClassLib;

	InterLibrarySubtyping(String subClass, String subClassLib, String superClass, String superClassLib) {
		this.subClass = subClass;
		this.subClassLib = subClassLib;
		this.superClass = superClass;
		this.superClassLib = superClassLib;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) return false;
		if (obj.getClass() != this.getClass()) return false;
		InterLibrarySubtyping keyObj = (InterLibrarySubtyping) obj;
		return (keyObj.subClass.equals(this.subClass) && keyObj.subClassLib.equals(this.subClassLib) 
				&& keyObj.superClass.equals(this.superClass) && keyObj.superClassLib.equals(this.superClassLib));
	}

	@Override
	public int hashCode() {
		return subClass.hashCode() + subClassLib.hashCode() + superClass.hashCode() + superClassLib.hashCode();
	}
}
