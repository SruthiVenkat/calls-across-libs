package instrumentation.apisurfaceanalysis;

class InterLibraryCallsKey {
	String callerMethodString;
	String callerMethodLibString;
	String calleeVisibilityString;
	String virtualCalleeMethodString;
	String actualCalleeMethodString;
	String calleeMethodLibString;
	boolean reflective;

	InterLibraryCallsKey(String callerMethodString, String callerMethodLibString, String calleeVisibilityString, String virtualCalleeMethodString, 
			String actualCalleeMethodString, String calleeMethodLibString, boolean reflective) {
		this.callerMethodString = callerMethodString;
		this.callerMethodLibString = callerMethodLibString;
		this.calleeVisibilityString = calleeVisibilityString;
		this.virtualCalleeMethodString = virtualCalleeMethodString;
		this.actualCalleeMethodString = actualCalleeMethodString;
		this.calleeMethodLibString = calleeMethodLibString;
		this.reflective = reflective;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) return false;
		if (obj.getClass() != this.getClass()) return false;
		InterLibraryCallsKey keyObj = (InterLibraryCallsKey) obj;
		return (keyObj.callerMethodString.equals(this.callerMethodString) && keyObj.callerMethodLibString.equals(this.callerMethodLibString) && keyObj.calleeVisibilityString.equals(this.calleeVisibilityString)
				&& keyObj.virtualCalleeMethodString.equals(this.virtualCalleeMethodString) && keyObj.actualCalleeMethodString.equals(this.actualCalleeMethodString)
				&& keyObj.calleeMethodLibString.equals(this.calleeMethodLibString)) && keyObj.reflective==this.reflective;
	}

	@Override
	public int hashCode() {
		return callerMethodString.hashCode() + callerMethodLibString.hashCode() + virtualCalleeMethodString.hashCode() + calleeVisibilityString.hashCode()
				+ actualCalleeMethodString.hashCode() + calleeMethodLibString.hashCode() + (reflective ? 1 : 0);
	}
}
