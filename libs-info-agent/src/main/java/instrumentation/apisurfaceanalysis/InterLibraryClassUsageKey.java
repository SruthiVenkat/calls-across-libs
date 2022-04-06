package instrumentation.apisurfaceanalysis;

public class InterLibraryClassUsageKey {
	String className;
	String classVisibility;
	String classLib;
	String usageType;
	String usedInCls;
	String usedInLib;

	InterLibraryClassUsageKey(String className, String classVisibility, 
			String classLib, String usageType, String usedInCls, String usedInLib) {
		this.className = className;
		this.classVisibility = classVisibility;
		this.classLib = classLib;
		this.usageType = usageType;
		this.usedInCls = usedInCls;
		this.usedInLib = usedInLib;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) return false;
		if (obj.getClass() != this.getClass()) return false;
		InterLibraryClassUsageKey keyObj = (InterLibraryClassUsageKey) obj;
		return (keyObj.className.equals(this.className) && keyObj.classVisibility.equals(classVisibility)
				&& keyObj.classLib.equals(this.classLib) && keyObj.usageType.equals(this.usageType) 
				&& keyObj.usedInCls.equals(this.usedInCls) && keyObj.usedInLib.equals(this.usedInLib));
	}

	@Override
	public int hashCode() {
		return className.hashCode() + classVisibility.hashCode() + classLib.hashCode() 
			+ usedInCls.hashCode() + usageType.hashCode() + usedInLib.hashCode();
	}
}
