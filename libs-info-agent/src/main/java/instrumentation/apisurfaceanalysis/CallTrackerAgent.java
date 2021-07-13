package instrumentation.apisurfaceanalysis;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Java Agent To Track Calls Across Libraries
 * @author sruthi
 *
 */
public class CallTrackerAgent {
	public static void premain(String agentArgs, Instrumentation inst) {
		/* track calls across libraries */
		CallTrackerTransformer transformer = new CallTrackerTransformer();
        inst.addTransformer(transformer);

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
					String invocationsOutputPath = prop.getProperty("invocationsOutputPath");
					String fieldsOutputPath = prop.getProperty("fieldsOutputPath");
					String subtypingOutputPath = prop.getProperty("subtypingOutputPath");
					String annotationsOutputPath = prop.getProperty("annotationsOutputPath");
					String libsInfoPath = prop.getProperty("libsInfoPath");
					
					System.out.println("Adding results to CSV");
		      		FileWriter writer = new FileWriter(invocationsOutputPath, true);	
		      		if (new File(invocationsOutputPath).length() == 0)
		      			writer.write("Caller Method,Caller Library,Virtual Callee Method,Actual Callee Method,Callee Library,Count\n");
		      		for (InterLibraryCallsKey ilcKey: CallTrackerTransformer.interLibraryCalls.keySet()) {
		            	writer.write(ilcKey.callerMethodString+","+ ilcKey.callerMethodLibString+","+ ilcKey.virtualCalleeMethodString+","+
		            			ilcKey.actualCalleeMethodString+","+ilcKey.calleeMethodLibString+","+CallTrackerTransformer.interLibraryCalls.get(ilcKey)+"\n");
		            }
		      		writer.flush();
		      		writer.close();
		      		writer = new FileWriter(fieldsOutputPath, true);
		      		if (new File(fieldsOutputPath).length() == 0)
		      			writer.write("Callee Library,Field Name,Field Signature,Static,Visibility,Field Library,Count\n");
		      		for (InterLibraryFieldsKey ilfKey: CallTrackerTransformer.interLibraryFields.keySet()) {
		            	writer.write(ilfKey.calleeLib+","+ilfKey.fieldName+","+ ilfKey.fieldSignature+","+ ilfKey.isStatic+","+ ilfKey.visibility+","+ ilfKey.libName+","+CallTrackerTransformer.interLibraryFields.get(ilfKey)+"\n");
		            }
		      		writer.flush();
		      		writer.close();
		      		writer = new FileWriter(subtypingOutputPath, true);
		      		if (new File(subtypingOutputPath).length() == 0)
		      			writer.write("SubClass,Sub Library,Super Class/Interface,Super Library\n");
		      		for (InterLibrarySubtyping ils: CallTrackerTransformer.interLibrarySubtyping) {
		            	writer.write(ils.subClass+","+ ils.subClassLib+","+ ils.superClass+","+ils.superClassLib+"\n");
		            }
		      		writer.flush();
		      		writer.close();
		      		writer = new FileWriter(annotationsOutputPath, true);
		      		if (new File(annotationsOutputPath).length() == 0)
		      			writer.write("Class,Method,Field Name:Field Signature,User Library,Annotation,Annotation Library\n");
		      		for (InterLibraryAnnotations ila: CallTrackerTransformer.interLibraryAnnotations) {
		            	writer.write(ila.className+","+ ila.methodName+","+ ila.field+","+ ila.classLib+","+ ila.annotationName+","+ila.annotationLib+"\n");
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
							String[] data = rowString.split(",");
							if (CallTrackerTransformer.libsToMethods.containsKey(data[0]))
								data[2] = String.valueOf(CallTrackerTransformer.libsToMethods.get(data[0]).size());
							writer2.write(String.join(",", data)+"\n");
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
