package callback;

import packageA.foo.CallbackCaller;
import packageA.foo.CallbackImpl;
import packageA.foo.CallbackInterface;

public class CallbackClient {

	public static void main(String[] args) {
		CallbackClient callbackClient = new CallbackClient();
		callbackClient.callbackClientMethod();
	}

	public void callbackClientMethod() {
		CallbackCaller callbackCaller = new CallbackCaller();

		CallbackImpl callbackImpl = new CallbackImpl();
		callbackCaller.callbackCallerMethod(callbackImpl);
		
		callbackCaller.callbackCallerMethod(new CallbackInterface() {
			@Override
			public void callbackMethod() {
				System.out.println("client impl of callback interface");
			}
		});
	}
}
