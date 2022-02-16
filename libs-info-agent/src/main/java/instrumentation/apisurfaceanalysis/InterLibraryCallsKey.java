package instrumentation.apisurfaceanalysis;

class InterLibraryCallsKey {
	String callerMethodString;
	String callerMethodLibString;
	String calleeVisibilityString;
	String virtualCalleeMethodString;
	String virtualCalleeMethodLibString;
	String actualCalleeMethodString;
	String actualCalleeMethodLibString;
	boolean reflective;
	boolean dynamicProxy;
	String label;

	InterLibraryCallsKey(String callerMethodString, String callerMethodLibString, String calleeVisibilityString, String virtualCalleeMethodString, String virtualCalleeMethodLibString,
			String actualCalleeMethodString, String actualCalleeMethodLibString, boolean reflective, boolean dynamicProxy, String serviceBypass) {
		this.callerMethodString = callerMethodString;
		this.callerMethodLibString = callerMethodLibString;
		this.calleeVisibilityString = calleeVisibilityString;
		this.virtualCalleeMethodString = virtualCalleeMethodString;
		this.virtualCalleeMethodLibString = virtualCalleeMethodLibString;
		this.actualCalleeMethodString = actualCalleeMethodString;
		this.actualCalleeMethodLibString = actualCalleeMethodLibString;
		this.reflective = reflective;
		this.dynamicProxy = dynamicProxy;
		this.label = serviceBypass;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) return false;
		if (obj.getClass() != this.getClass()) return false;
		InterLibraryCallsKey keyObj = (InterLibraryCallsKey) obj;
		return (keyObj.callerMethodString.equals(this.callerMethodString) && keyObj.callerMethodLibString.equals(this.callerMethodLibString) && keyObj.calleeVisibilityString.equals(this.calleeVisibilityString)
				&& keyObj.virtualCalleeMethodString.equals(this.virtualCalleeMethodString) && keyObj.actualCalleeMethodString.equals(this.actualCalleeMethodString) && keyObj.virtualCalleeMethodLibString.equals(this.virtualCalleeMethodLibString)
				&& keyObj.actualCalleeMethodLibString.equals(this.actualCalleeMethodLibString) && keyObj.reflective==this.reflective && keyObj.dynamicProxy==this.dynamicProxy) && keyObj.label.equals(this.label);
	}

	@Override
	public int hashCode() {
		return callerMethodString.hashCode() + callerMethodLibString.hashCode() + virtualCalleeMethodString.hashCode() + calleeVisibilityString.hashCode() + actualCalleeMethodString.hashCode() 
				+ virtualCalleeMethodLibString.hashCode() + actualCalleeMethodLibString.hashCode() + (reflective ? 1 : 0) + (dynamicProxy ? 1 : 0) + label.hashCode();
	}
}
