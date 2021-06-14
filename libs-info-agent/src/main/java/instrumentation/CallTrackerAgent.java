package instrumentation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;

/**
 * Java Agent To Track Calls Across Libraries
 * @author sruthi
 *
 */
public class CallTrackerAgent {
	public static void premain(String agentArgs, Instrumentation inst) {
		/* track calls across libraries */
        inst.addTransformer(new CallTrackerTransformer());
        
		Runtime.getRuntime().addShutdownHook(new Thread()
		{
			public void run()
			{
				Path configPath = Paths.get(new File(".").getAbsolutePath());
				while (!configPath.endsWith("calls-across-libs"))
					configPath = configPath.getParent();
				String configPathName = configPath.toString()+"/src/main/resources/config.properties";
	      		try (FileReader input = new FileReader(configPathName)) {
					Properties prop = new Properties();
					prop.load(input);
					String outputPath = prop.getProperty("outputPath");
					String libsInfoPath = prop.getProperty("libsInfoPath");
					
					System.out.println("Adding results to CSV");
		      		FileWriter writer = new FileWriter(outputPath, true);	
		      		if (new File(outputPath).length() == 0)
		      			writer.write("Caller Method\tCaller Library\tCallee Method\tCallee Library\tStatic Count\tDynamic Count\n");
		      		for (InterLibraryCountsKey ilcKey: CallTrackerTransformer.interLibraryCounts.keySet()) {
		            	InterLibraryCountsValue ilcValue = CallTrackerTransformer.interLibraryCounts.get(ilcKey);
		            	writer.write(ilcKey.callerMethodString+"\t"+ ilcKey.callerMethodLibString+"\t"+ ilcKey.calleeMethodString+"\t"+
		            			ilcKey.calleeMethodLibString+"\t"+ ilcValue.staticCount+"\t"+ ilcValue.dynamicCount+"\n");
		            }
		      		writer.flush();
		      		writer.close();
		      		String[] rows = new String[(int) new File(libsInfoPath).length()]; String row; int count = 0;
					BufferedReader reader = new BufferedReader(new FileReader(libsInfoPath));
					while ((row = reader.readLine()) != null) {
						rows[count++] = row;
					}
					reader.close();
					BufferedWriter writer2 = new BufferedWriter(new FileWriter(libsInfoPath, false));
					for (String rowString : rows) {
						if (rowString!=null) {
							String[] data = rowString.split("\t");
							if (CallTrackerTransformer.libsToMethods.containsKey(data[0]))
								data[2] = String.valueOf(CallTrackerTransformer.libsToMethods.get(data[0]).size());
							writer2.write(String.join("\t", data)+"\n");
						}
					}
					writer2.flush();
					writer2.close();
				} catch (IOException ex) {
				    ex.printStackTrace();
				}
			}
        });
        
       
    }
	public static void agentmain(String agentArgs, Instrumentation inst) {
        inst.addTransformer(new CallTrackerTransformer());
    }
}
