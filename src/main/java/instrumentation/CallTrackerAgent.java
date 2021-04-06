package instrumentation;

import java.lang.instrument.Instrumentation;

/**
 * Java Agent To Track Calls Across Libraries
 * @author sruthi
 *
 */
public class CallTrackerAgent {
	public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("Start!");
        System.out.println(inst.getAllLoadedClasses().toString());
        inst.addTransformer(new CallTrackerTransformer());
    }
}
