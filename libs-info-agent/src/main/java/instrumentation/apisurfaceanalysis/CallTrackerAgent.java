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
                    
                    System.out.println("Adding results to tsv");
                    FileWriter writer = new FileWriter(invocationsOutputPath, true);    
                    if (new File(invocationsOutputPath).length() == 0)
                        writer.write("Caller Method\tCaller Library\tCallee Visibility\tDeclared Callee Method\tDeclared Callee Library\tActual Callee Method\tActual Callee Library\tCount\tReflective\tDynamicProxy\tService Bypass\n");
                    for (InterLibraryCallsKey ilcKey: CallTrackerTransformer.interLibraryCalls.keySet()) {
                        writer.write(ilcKey.callerMethodString+"\t"+ ilcKey.callerMethodLibString+"\t"+ ilcKey.calleeVisibilityString+"\t"+ilcKey.virtualCalleeMethodString+"\t"+ilcKey.virtualCalleeMethodLibString
                                +"\t"+ilcKey.actualCalleeMethodString+"\t"+ilcKey.actualCalleeMethodLibString+"\t"+CallTrackerTransformer.interLibraryCalls.get(ilcKey)+"\t"+ilcKey.reflective+"\t"+ilcKey.dynamicProxy+"\t"+ilcKey.serviceBypass+"\n");
                    }
                    writer.flush();
                    writer.close();
                    writer = new FileWriter(fieldsOutputPath, true);
                    if (new File(fieldsOutputPath).length() == 0)
                        writer.write("Callee Library\tField Name\tDeclared Class\tActual Class\tField Signature\tStatic\tVisibility\tField Library\tReflective\tCount\n");
                    for (InterLibraryFieldsKey ilfKey: CallTrackerTransformer.interLibraryFields.keySet()) {
                        writer.write(ilfKey.calleeLib+"\t"+ilfKey.fieldName+"\t"+ilfKey.virtualClass+"\t"+ilfKey.actualClass+"\t"+ ilfKey.fieldSignature+"\t"+ ilfKey.isStatic+"\t"+ ilfKey.visibility+"\t"+ ilfKey.libName+"\t"+ ilfKey.reflective+"\t"+CallTrackerTransformer.interLibraryFields.get(ilfKey)+"\n");
                    }
                    writer.flush();
                    writer.close();
                    writer = new FileWriter(subtypingOutputPath, true);
                    if (new File(subtypingOutputPath).length() == 0)
                        writer.write("SubClass\tSub Library\tSuper Class/Interface\tSuper Class/Interface Visibility\tSuper Library\tCount\n");
                    for (InterLibrarySubtypingKey ils: CallTrackerTransformer.interLibrarySubtyping.keySet()) {
                        writer.write(ils.subClass+"\t"+ ils.subClassLib+"\t"+ ils.superClass+"\t"+ ils.superClassVis+"\t"+ils.superClassLib+"\t"+CallTrackerTransformer.interLibrarySubtyping.get(ils)+"\n");
                    }
                    writer.flush();
                    writer.close();
                    writer = new FileWriter(annotationsOutputPath, true);
                    if (new File(annotationsOutputPath).length() == 0)
                        writer.write("Class\tMethod\tField Name:Field Signature\tAnnotated In Library\tAnnotation\tAnnotation Visibility\tAnnotation Library\tCount\n");
                    for (InterLibraryAnnotationsKey ila: CallTrackerTransformer.interLibraryAnnotations.keySet()) {
                        writer.write(ila.className+"\t"+ ila.methodName+"\t"+ ila.field+"\t"+ ila.classLib+"\t"+ ila.annotationName+"\t"+ ila.annotationVis+"\t"+ila.annotationLib+"\t"+CallTrackerTransformer.interLibraryAnnotations.get(ila)+"\n");
                    }
                    writer.flush();
                    writer.close();
                    writer = new FileWriter(setAccessibleCallsPath, true);
                    if (new File(setAccessibleCallsPath).length() == 0)
                        writer.write("Caller Method\tCaller Library\tsetAccessible Called On\tVisibility\tCallee Name\tField Signature\tCallee Library\n");
                    for (SetAccessibleCallsKey sac: CallTrackerTransformer.setAccessibleCallsInfo) {
                        writer.write(sac.callerMethod+"\t"+ sac.callerLib+"\t"+ sac.calledOnType+"\t"+ sac.visibility+"\t"+ sac.calledOnObjName+"\t"+sac.fieldSignature+"\t"+sac.libName+"\n");
                    }
                    writer.flush();
                    writer.close();
                    writer = new FileWriter(classesUsageInfoPath, true);
                    if (new File(classesUsageInfoPath).length() == 0)
                        writer.write("Class Name\tClass Visibility\tClass Library\tUsage\tUsed In Library\n");
                    for (InterLibraryClassUsageKey ilcu: CallTrackerTransformer.interLibraryClassUsage) {
                        writer.write(ilcu.className+"\t"+ ilcu.classVisibility+"\t"+ ilcu.classLib+"\t"+ ilcu.usageType+"\t"+ ilcu.usedInLib+"\n");
                    }
                    writer.flush();
                    writer.close();
                    writer = new FileWriter(serviceBypassCallsPath, true);
                    if (new File(serviceBypassCallsPath).length() == 0)
                        writer.write("Caller Method\tCaller Library\tInterface Name\tInterface Library\tCallee Method\tImpl. Name\tImpl. Library\n");
                    for (SPIInfoKey spi: CallTrackerTransformer.spiInfo) {
                        writer.write(spi.callerMethodName+"\t"+ spi.callerMethodLib+"\t"+ spi.interfaceName+"\t"+ spi.interfaceLib+"\t"+ spi.calledMethodName+"\t"+spi.implName+"\t"+spi.implLib+"\n");
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
                System.gc();
            }
        }); 
    }
    
    public static void agentmain(String agentArgs, Instrumentation inst) {
        inst.addTransformer(new CallTrackerTransformer());
    }
}
