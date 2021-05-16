package instrumentation;

import java.lang.instrument.Instrumentation;
import java.util.List;
import java.util.Map;

import db.DatabaseConnector;

/**
 * Java Agent To Track Calls Across Libraries
 * @author sruthi
 *
 */
public class CallTrackerAgent {
	public static void premain(String agentArgs, Instrumentation inst) {
        inst.addTransformer(new CallTrackerTransformer());
        
        Runtime.getRuntime().addShutdownHook(new Thread()
        {
          public void run()
          {
            System.out.println("Shutdown Hook is running !");
          }
        });
    }
	public static void agentmain(String agentArgs, Instrumentation inst) {
        inst.addTransformer(new CallTrackerTransformer());
    }
}
