package instrumentation.apisurfaceanalysis;

class InterLibraryCallsKey {
	String callerMethodString;
	String callerMethodLibString;
	String virtualCalleeMethodString;
	String actualCalleeMethodString;
	String calleeMethodLibString;

	InterLibraryCallsKey(String callerMethodString, String callerMethodLibString, String virtualCalleeMethodString, 
			String actualCalleeMethodString, String calleeMethodLibString) {
		this.callerMethodString = callerMethodString;
		this.callerMethodLibString = callerMethodLibString;
		this.virtualCalleeMethodString = virtualCalleeMethodString;
		this.actualCalleeMethodString = actualCalleeMethodString;
		this.calleeMethodLibString = calleeMethodLibString;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) return false;
		if (obj.getClass() != this.getClass()) return false;
		InterLibraryCallsKey keyObj = (InterLibraryCallsKey) obj;
		return (keyObj.callerMethodString.equals(this.callerMethodString) && keyObj.callerMethodLibString.equals(this.callerMethodLibString)
				&& keyObj.virtualCalleeMethodString.equals(this.virtualCalleeMethodString) && keyObj.actualCalleeMethodString.equals(this.actualCalleeMethodString)
				&& keyObj.calleeMethodLibString.equals(this.calleeMethodLibString));
	}

	@Override
	public int hashCode() {
		return callerMethodString.hashCode() + callerMethodLibString.hashCode() + virtualCalleeMethodString.hashCode()
				+ actualCalleeMethodString.hashCode() + calleeMethodLibString.hashCode();
	}
}
