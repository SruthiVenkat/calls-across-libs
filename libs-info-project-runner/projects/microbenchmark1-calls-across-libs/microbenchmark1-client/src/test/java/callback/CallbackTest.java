package callback;

import org.junit.Test;

public class CallbackTest {
	@Test
	public void testCallback() {
		CallbackClient callbackClient = new CallbackClient();
		callbackClient.callbackClientMethod();
	}
}
