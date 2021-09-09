package instrumentation.apisurfaceanalysis;

class InterLibraryAnnotationsKey {
	String className;
	String methodName;
	String field;
	String classLib;
	String annotationName;
	String annotationVis;
	String annotationLib;

	InterLibraryAnnotationsKey(String className, String methodName, String field, String classLib, String annotationName, String annotationVis, String annotationLib) {
		this.className = className;
		this.methodName = methodName;
		this.field = field;
		this.classLib = classLib;
		this.annotationName = annotationName;
		this.annotationVis = annotationVis;
		this.annotationLib = annotationLib;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) return false;
		if (obj.getClass() != this.getClass()) return false;
		InterLibraryAnnotationsKey keyObj = (InterLibraryAnnotationsKey) obj;
		return (keyObj.className.equals(this.className) && keyObj.methodName.equals(methodName) && keyObj.field.equals(field) && keyObj.classLib.equals(this.classLib) 
				&& keyObj.annotationName.equals(this.annotationName) && keyObj.annotationVis.equals(this.annotationVis) && keyObj.annotationLib.equals(this.annotationLib));
	}

	@Override
	public int hashCode() {
		return className.hashCode() + methodName.hashCode() + field.hashCode() + classLib.hashCode() + annotationName.hashCode() + annotationVis.hashCode() + annotationLib.hashCode();
	}
}