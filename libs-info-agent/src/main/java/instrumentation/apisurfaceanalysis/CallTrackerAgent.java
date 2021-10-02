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
					String setAccessibleCallsPath = prop.getProperty("setAccessibleCallsPath");
					String classesUsageInfoPath = prop.getProperty("classesUsageInfoPath");
					String serviceBypassCallsPath = prop.getProperty("serviceBypassCallsPath");
					String libsInfoPath = prop.getProperty("libsInfoPath");
					
					System.out.println("Adding results to CSV");
		      		FileWriter writer = new FileWriter(invocationsOutputPath, true);	
		      		if (new File(invocationsOutputPath).length() == 0)
		      			writer.write("Caller Method,Caller Library,Callee Visibility,Declared Callee Method,Declared Callee Library,Actual Callee Method,Actual Callee Library,Count,Reflective,DynamicProxy,Service Bypass\n");
		      		for (InterLibraryCallsKey ilcKey: CallTrackerTransformer.interLibraryCalls.keySet()) {
		            	writer.write(ilcKey.callerMethodString+","+ ilcKey.callerMethodLibString+","+ ilcKey.calleeVisibilityString+","+ilcKey.virtualCalleeMethodString+","+ilcKey.virtualCalleeMethodLibString
		            			+","+ilcKey.actualCalleeMethodString+","+ilcKey.actualCalleeMethodLibString+","+CallTrackerTransformer.interLibraryCalls.get(ilcKey)+","+ilcKey.reflective+","+ilcKey.dynamicProxy+","+ilcKey.serviceBypass+"\n");
		            }
		      		writer.flush();
		      		writer.close();
		      		writer = new FileWriter(fieldsOutputPath, true);
		      		if (new File(fieldsOutputPath).length() == 0)
		      			writer.write("Callee Library,Field Name,Declared Class,Actual Class,Field Signature,Static,Visibility,Field Library,Reflective,Count\n");
		      		for (InterLibraryFieldsKey ilfKey: CallTrackerTransformer.interLibraryFields.keySet()) {
		            	writer.write(ilfKey.calleeLib+","+ilfKey.fieldName+","+ilfKey.virtualClass+","+ilfKey.actualClass+","+ ilfKey.fieldSignature+","+ ilfKey.isStatic+","+ ilfKey.visibility+","+ ilfKey.libName+","+ ilfKey.reflective+","+CallTrackerTransformer.interLibraryFields.get(ilfKey)+"\n");
		            }
		      		writer.flush();
		      		writer.close();
		      		writer = new FileWriter(subtypingOutputPath, true);
		      		if (new File(subtypingOutputPath).length() == 0)
		      			writer.write("SubClass,Sub Library,Super Class/Interface,Super Class/Interface Visibility,Super Library,Count\n");
		      		for (InterLibrarySubtypingKey ils: CallTrackerTransformer.interLibrarySubtyping.keySet()) {
		            	writer.write(ils.subClass+","+ ils.subClassLib+","+ ils.superClass+","+ ils.superClassVis+","+ils.superClassLib+","+CallTrackerTransformer.interLibrarySubtyping.get(ils)+"\n");
		            }
		      		writer.flush();
		      		writer.close();
		      		writer = new FileWriter(annotationsOutputPath, true);
		      		if (new File(annotationsOutputPath).length() == 0)
		      			writer.write("Class,Method,Field Name:Field Signature,Annotated In Library,Annotation,Annotation Visibility,Annotation Library,Count\n");
		      		for (InterLibraryAnnotationsKey ila: CallTrackerTransformer.interLibraryAnnotations.keySet()) {
		            	writer.write(ila.className+","+ ila.methodName+","+ ila.field+","+ ila.classLib+","+ ila.annotationName+","+ ila.annotationVis+","+ila.annotationLib+","+CallTrackerTransformer.interLibraryAnnotations.get(ila)+"\n");
		            }
		      		writer.flush();
		      		writer.close();
		      		writer = new FileWriter(setAccessibleCallsPath, true);
		      		if (new File(setAccessibleCallsPath).length() == 0)
		      			writer.write("Caller Method,Caller Library,setAccessible Called On,Visibility,Callee Name,Field Signature,Callee Library\n");
		      		for (SetAccessibleCallsKey sac: CallTrackerTransformer.setAccessibleCallsInfo) {
		            	writer.write(sac.callerMethod+","+ sac.callerLib+","+ sac.calledOnType+","+ sac.visibility+","+ sac.calledOnObjName+","+sac.fieldSignature+","+sac.libName+"\n");
		            }
		      		writer.flush();
		      		writer.close();
		      		writer = new FileWriter(classesUsageInfoPath, true);
		      		if (new File(classesUsageInfoPath).length() == 0)
		      			writer.write("Class Name,Class Visibility,Class Library,Usage,Used In Library\n");
		      		for (InterLibraryClassUsageKey ilcu: CallTrackerTransformer.interLibraryClassUsage) {
		            	writer.write(ilcu.className+","+ ilcu.classVisibility+","+ ilcu.classLib+","+ ilcu.usageType+","+ ilcu.usedInLib+"\n");
		            }
		      		writer.flush();
		      		writer.close();
		      		writer = new FileWriter(serviceBypassCallsPath, true);
		      		if (new File(serviceBypassCallsPath).length() == 0)
		      			writer.write("Caller Method,Caller Library,Interface Name,Interface Library,Callee Method,Impl. Name,Impl. Library\n");
		      		for (SPIInfoKey spi: CallTrackerTransformer.spiInfo) {
		            	writer.write(spi.callerMethodName+","+ spi.callerMethodLib+","+ spi.interfaceName+","+ spi.interfaceLib+","+ spi.calledMethodName+","+spi.implName+","+spi.implLib+"\n");
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
	      		System.gc();
			}
        }); 
    }
	
	public static void agentmain(String agentArgs, Instrumentation inst) {
        inst.addTransformer(new CallTrackerTransformer());
    }
}
