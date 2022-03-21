package runner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class DependentTestRunner {
	public static String agentPath;
	public static String javassistJarPath;
	public static String JAVA_OPTS;
	public static String outputPath;
	public static List<String> addedLibs = new ArrayList<String>();
	public static String configPath = Paths.get(new File(".").getAbsolutePath()).getParent().getParent().toString()+"/src/main/resources/config.properties";
	public static Map<Long, String> javaVersionPaths = new HashMap<Long, String>();
	public static Map<Long, String> javaHomes = new HashMap<Long, String>();
	public static String mavenHome;
	static {
		loadProperties();
	}
	
	public static void main(String[] args) {
		//TODO do we need to do this again? 
		loadProperties();	// load properties - paths
		
		JSONParser jsonParser = new JSONParser();
        try (FileReader reader = new FileReader(new File(".").getAbsolutePath()+File.separator+"projects"+File.separator+"projects-list.json")) {
            Object obj = jsonParser.parse(reader);
            JSONArray projects = (JSONArray) obj;
            JSONArray gradleProjects = new JSONArray();
            JSONArray mvnProjects = new JSONArray();
            Iterator<JSONObject> iterator = projects.iterator();
            while (iterator.hasNext()) {
            	JSONObject projectObject = (JSONObject)iterator.next();
        		if (projectObject.get("build").equals("maven")) {
        			projectObject.put("libName", mavenGetGAV(new File(".").getAbsolutePath()+File.separator+"projects"
            				+File.separator+projectObject.get("execDir")+File.separator+"pom.xml", (long) projectObject.get("javaVersion")));
        			mvnProjects.add(projectObject);
        		} else if (projectObject.get("build").equals("gradle")) {
            		projectObject.put("libName", gradleGetGAV(new File(".").getAbsolutePath()+File.separator+"projects"
            				+File.separator+projectObject.get("rootDir"), (String)projectObject.get("gavOf"), (long) projectObject.get("javaVersion")));
        			gradleProjects.add(projectObject);
        		}	
            }

            // install projects
            runMavenProjectsInstall(mvnProjects);
            runGradleProjectsInstall(gradleProjects);

    		// get total API counts for projects and packages in each project
            JarUtility.initLibsToCountsAndClasses("maven", mvnProjects);
            JarUtility.initLibsToCountsAndClasses("gradle", gradleProjects);
            
            // run unit tests
            Map<String, File> pomList = getPOMList(mvnProjects);
            runMavenProjectsTest(mvnProjects, pomList);
            runGradleProjectsTest(gradleProjects);
        } catch (Exception e) {
			System.out.println("Error while reading file with project list " + e.toString() + e);
			e.printStackTrace();
		}
	}
	
	public static void loadProperties() {
		try (FileReader input = new FileReader(configPath)) {
			Properties prop = new Properties();
            prop.load(input);
            agentPath = prop.getProperty("agentPath");
            javassistJarPath = prop.getProperty("javassistJarPath");
            outputPath = prop.getProperty("outputPath");
            File outputDir = new File(outputPath);
            if (!outputDir.exists())
            	outputDir.mkdir();

            JAVA_OPTS =  "-javaagent:"+agentPath+" -Xbootclasspath/a:"+javassistJarPath+":"+agentPath;
            //setting of the java home and maven properties
            javaVersionPaths.put((long)8, prop.getProperty("java8Path"));
    		javaVersionPaths.put((long)11, prop.getProperty("java11Path"));
    		javaHomes.put((long)8, prop.getProperty("java8Home"));
    		javaHomes.put((long)11, prop.getProperty("java11Home"));
    		mavenHome = prop.getProperty("mavenHome");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
	}
	
	public static String mavenGetGAV(String pomFile, long javaVersion) {
		String gav = "";
		setJavaVersion(javaVersion);
		InvocationRequest request = new DefaultInvocationRequest();
		request.setPomFile(new File(pomFile));
		request.setGoals(Arrays.asList("help:evaluate"));
		request.setJavaHome(new File(javaHomes.get(javaVersion)));
		
		//System.setProperty("maven.home", "/usr/share/maven");
		System.setProperty("maven.home", mavenHome);
		Invoker invoker = new DefaultInvoker();
		try {
			request.setMavenOpts("-Dexpression=project.groupId -Doutput=group.txt");
			invoker.execute( request );
			BufferedReader reader1 = new BufferedReader(new FileReader(pomFile.replace("pom.xml", "group.txt")));
			gav = reader1.readLine()+":";

			request.setMavenOpts("-Dexpression=project.artifactId -Doutput=artifact.txt");
			invoker.execute( request );
			BufferedReader reader2 = new BufferedReader(new FileReader(pomFile.replace("pom.xml", "artifact.txt")));
			gav += reader2.readLine()+":";

			request.setMavenOpts("-Dexpression=project.version -Doutput=version.txt");
			invoker.execute( request );
			BufferedReader reader3 = new BufferedReader(new FileReader(pomFile.replace("pom.xml", "version.txt")));
			gav += reader3.readLine();
			new File(pomFile.replace("pom.xml", "group.txt")).delete();
			new File(pomFile.replace("pom.xml", "artifact.txt")).delete();
			new File(pomFile.replace("pom.xml", "version.txt")).delete();
			reader1.close();
			reader2.close();
			reader3.close();
		} catch (MavenInvocationException e) {
			e.printStackTrace();
		} catch (IOException ex) {
		    ex.printStackTrace();
		}
		return gav;
	}
	
	public static String gradleGetGAV(String pathToRootPrj, String gavOf, long javaVersion) {
		setJavaVersion(javaVersion);
		String getDepsInitScriptPathString = new File(".").getAbsolutePath()+File.separator+"gradle-init-scripts"+File.separator+"get-GAV.gradle";
		executeCommand(" ./gradlew -I "+getDepsInitScriptPathString+" :"+gavOf+":getGAV --stacktrace > gav-output.txt", pathToRootPrj);
		String gav = gavOf;
		try {
			int count = -1;
			BufferedReader reader = new BufferedReader(new FileReader(pathToRootPrj+File.separator+"gav-output.txt"));
			String row, chk = gavOf.isEmpty() ? ":getGAV" : ":"+gavOf+":getGAV";
			while ((row = reader.readLine()) != null) {
				if (count>=0 && count<2) {gav += row+":";count++;}
				if (count==2) {gav += row;count++;}
				
				if (row.contains("Task "+chk)) count = 0;
			}
			reader.close();
			new File(pathToRootPrj+File.separator+"gav-output.txt").delete();
		} catch (IOException ex) {
		    ex.printStackTrace();
		}
		return gav;
	}
	
	public static Map<String, File> getPOMList(JSONArray mavenProjects) {
		// get poms for projects
		Map<String, File> pomList = new HashMap<String, File>();
        Iterator<JSONObject> iterator = mavenProjects.iterator();
        while (iterator.hasNext()) {
        	JSONObject projectObject = (JSONObject)iterator.next();
        	File pomFile = new File(new File(".").getAbsolutePath()+File.separator+"projects"+File.separator+projectObject.get("execDir")+File.separator+"pom.xml");
         	pomList.put((String)projectObject.get("libName"), pomFile);
        }
        return pomList;
	}
	
	public static void runMavenProjectsInstall(JSONArray mavenProjects) {
        Iterator<JSONObject> iterator = mavenProjects.iterator();
        while (iterator.hasNext()) {
        	JSONObject projectObject = (JSONObject)iterator.next();
        	File pomFile = new File(new File(".").getAbsolutePath()+File.separator+"projects"+File.separator+projectObject.get("execDir")+File.separator+"pom.xml");
        	setJavaVersion((long) projectObject.get("javaVersion"));
        	mvnInstallProject(pomFile, (long)projectObject.get("javaVersion")); // generate jars
        	if (!((String)projectObject.get("rootDir")).equals("")) {
        		mvnInstallProject(new File(new File(".").getAbsolutePath()+File.separator+"projects"+File.separator+projectObject.get("rootDir")+File.separator+"pom.xml"), (long)projectObject.get("javaVersion"));
        	}
        }
	}
		
	public static void runMavenProjectsTest(JSONArray mavenProjects, Map<String, File> pomList) {	
		Iterator<JSONObject> iterator = mavenProjects.iterator();
        while (iterator.hasNext()) {
        	JSONObject projectObject = (JSONObject)iterator.next();
        	String lib = (String) projectObject.get("libName");
        	
			try (FileReader input = new FileReader(configPath)) {
				Properties prop = new Properties();
	            prop.load(input);
	            new File(outputPath+File.separator+lib).mkdir();
	            prop.setProperty("dynamicInvocationsOutputPath", outputPath+File.separator+lib+File.separator+lib+"-dynamic-invocations.tsv");
	            prop.setProperty("staticInvocationsOutputPath", outputPath+File.separator+lib+File.separator+lib+"-static-invocations.tsv");
	            prop.setProperty("fieldsOutputPath", outputPath+File.separator+lib+File.separator+lib+"-fields.tsv");
	            prop.setProperty("subtypingOutputPath", outputPath+File.separator+lib+File.separator+lib+"-subtyping.tsv");
	            prop.setProperty("annotationsOutputPath", outputPath+File.separator+lib+File.separator+lib+"-annotations.tsv");
	            prop.setProperty("setAccessibleCallsPath", outputPath+File.separator+lib+File.separator+lib+"-setAccessibleCalls.tsv");
	            prop.setProperty("classesUsageInfoPath", outputPath+File.separator+lib+File.separator+lib+"-classesUsageInfo.tsv");
	            prop.setProperty("serviceBypassCallsPath", outputPath+File.separator+lib+File.separator+lib+"-serviceBypassCalls.tsv");
	            prop.setProperty("libsInfoPath", outputPath+File.separator+lib+File.separator+lib+"-libsInfo.tsv");
	            prop.setProperty("runningLibrary", lib);
	            prop.store(new FileWriter(configPath, false), null);
	            setJavaVersion((long) projectObject.get("javaVersion"));
	            runMvnProjectUnitTests(pomList.get(lib), (long) projectObject.get("javaVersion"));
	            prop.setProperty("dynamicInvocationsOutputPath", "");
	            prop.setProperty("staticInvocationsOutputPath", "");
	            prop.setProperty("fieldsOutputPath", "");
	            prop.setProperty("subtypingOutputPath", "");
	            prop.setProperty("annotationsOutputPath", "");
	            prop.setProperty("setAccessibleCallsPath", "");
	            prop.setProperty("classesUsageInfoPath", "");
	            prop.setProperty("serviceBypassCallsPath", "");
	            prop.setProperty("libsInfoPath", "");
	            prop.setProperty("runningLibrary", "");
	            prop.store(new FileWriter(configPath, false), null);
            } catch (IOException ex) {
		            ex.printStackTrace();
		    }
		}
	}

	public static void mvnInstallProject(File pomFile, long javaVersion) {
		InvocationRequest request = new DefaultInvocationRequest();
		request.setPomFile(pomFile);
		request.setGoals(Arrays.asList("clean", "install"));
		request.setMavenOpts("-Dlicense.skipCheckLicense=true -Dlicense.skip=true -DskipTests=true -Dcheckstyle.skip=true -Dgpg.skip=true -Drat.skip=true -Dmaven.buildNumber.doCheck=false");
		request.setJavaHome(new File(javaHomes.get(javaVersion)));
		//System.setProperty("maven.home", "/usr/share/maven");
		System.setProperty("maven.home", mavenHome);
		Invoker invoker = new DefaultInvoker();
		try {
			invoker.execute( request );
		} catch (MavenInvocationException e) {
			e.printStackTrace();
		}
	}
	
	public static void runMvnProjectUnitTests(File pomFile, long javaVersion) {
		InvocationRequest request = new DefaultInvocationRequest();
		request.setPomFile(pomFile);
		request.setGoals(Arrays.asList("test")); 
		request.setMavenOpts("-Dlicense.skipCheckLicense=true -Dlicense.skip=true -Drat.skip=true -Dmaven.test.skip=false -Dgpg.skip=true -Dmaven.buildNumber.doCheck=false");
		request.setJavaHome(new File(javaHomes.get(javaVersion)));
		
		// parse pom and get existing argLine param
		String argLine = "";
		try {
			Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomFile);
			NodeList pluginList = document.getElementsByTagName("plugin");
			for (int i=0; i<pluginList.getLength(); i++) {
				Node plugin = pluginList.item(i);
				NodeList pluginChildren = plugin.getChildNodes();
				for (int j=0; j<pluginChildren.getLength(); j++) {
					Node pluginChild = pluginChildren.item(j);
					if (pluginChild.getNodeName().equals("configuration")) {
						NodeList configChildren = pluginChild.getChildNodes();
						for (int k=0; k<configChildren.getLength(); k++) {
							Node configChild = configChildren.item(k);
							if (configChild.getNodeName().equals("argLine"))
								argLine = configChild.getTextContent()+" ";
						}
					}
				}
			}
			if (argLine.contains("${argLine}")) argLine = argLine.replace("${argLine}", "");
		} catch (Exception e) {
		    e.printStackTrace();
		}
		
		Properties properties = new Properties();
		properties.setProperty("argLine", argLine+JAVA_OPTS);
		request.setProperties(properties);
		//System.setProperty("maven.home", "/usr/share/maven");
		System.setProperty("maven.home",mavenHome);
		Invoker invoker = new DefaultInvoker();
		try {
			invoker.execute( request );
		} catch (MavenInvocationException e) {
			e.printStackTrace();
		}
	}
	
	public static void runGradleProjectsInstall(JSONArray gradleProjects) {
        Iterator<JSONObject> iterator = gradleProjects.iterator();
        String pathToGradleProject, pathToRootGradleProject;
        while (iterator.hasNext()) {
        	JSONObject projectObject = (JSONObject)iterator.next();
        	pathToGradleProject = new File(".").getAbsolutePath()+File.separator+"projects"+File.separator+projectObject.get("execDir");
        	pathToRootGradleProject = new File(".").getAbsolutePath()+File.separator+"projects"+File.separator+projectObject.get("rootDir");
        	setJavaVersion((long) projectObject.get("javaVersion"));
        	installGradleProject(pathToGradleProject, pathToRootGradleProject); // generate wars and jars
        }
	}	

    public static void runGradleProjectsTest(JSONArray gradleProjects) {
    	Iterator<JSONObject> iterator = gradleProjects.iterator();
    	String pathToGradleProject, pathToRootGradleProject;
        while (iterator.hasNext()) {
        	JSONObject projectObject = (JSONObject)iterator.next();
        	pathToGradleProject = new File(".").getAbsolutePath()+File.separator+"projects"+File.separator+projectObject.get("execDir");
        	pathToRootGradleProject = new File(".").getAbsolutePath()+File.separator+"projects"+File.separator+projectObject.get("rootDir");
        	String lib = (String) projectObject.get("libName");
			try (FileReader input = new FileReader(configPath)) {
	            Properties prop = new Properties();
	            prop.load(input);
	            new File(outputPath+File.separator+lib).mkdir();
	            prop.setProperty("dynamicInvocationsOutputPath", outputPath+File.separator+lib+File.separator+lib+"-dynamic-invocations.tsv");
	            prop.setProperty("staticInvocationsOutputPath", outputPath+File.separator+lib+File.separator+lib+"-static-invocations.tsv");
	            prop.setProperty("fieldsOutputPath", outputPath+File.separator+lib+File.separator+lib+"-fields.tsv");
	            prop.setProperty("subtypingOutputPath", outputPath+File.separator+lib+File.separator+lib+"-subtyping.tsv");
	            prop.setProperty("annotationsOutputPath", outputPath+File.separator+lib+File.separator+lib+"-annotations.tsv");
	            prop.setProperty("setAccessibleCallsPath", outputPath+File.separator+lib+File.separator+lib+"-setAccessibleCalls.tsv");
	            prop.setProperty("classesUsageInfoPath", outputPath+File.separator+lib+File.separator+lib+"-classesUsageInfo.tsv");
	            prop.setProperty("serviceBypassCallsPath", outputPath+File.separator+lib+File.separator+lib+"-serviceBypassCalls.tsv");
	            prop.setProperty("libsInfoPath", outputPath+File.separator+lib+File.separator+lib+"-libsInfo.tsv");
	            prop.setProperty("runningLibrary", lib);
	            prop.store(new FileWriter(configPath, false), null);
	            setJavaVersion((long) projectObject.get("javaVersion"));
	            runGradleProjectUnitTests(pathToGradleProject, pathToRootGradleProject);
	            prop.setProperty("dynamicInvocationsOutputPath", "");
	            prop.setProperty("staticInvocationsOutputPath", "");
	            prop.setProperty("fieldsOutputPath", "");
	            prop.setProperty("subtypingOutputPath", "");
	            prop.setProperty("annotationsOutputPath", "");
	            prop.setProperty("setAccessibleCallsPath", "");
	            prop.setProperty("classesUsageInfoPath", "");
	            prop.setProperty("serviceBypassCallsPath", "");
	            prop.setProperty("libsInfoPath", "");
	            prop.setProperty("runningLibrary", "");
	            prop.store(new FileWriter(configPath, false), null);
            } catch (IOException ex) {
		            ex.printStackTrace();
		    }
		}		
	}
	
	public static void installGradleProject(String pathToExecGradleProject, String pathToRootGradleProject) {
		executeCommand(" ./gradlew clean build -x test -p "+pathToExecGradleProject+" --stacktrace", pathToRootGradleProject); // jar
	}
	
	public static void runGradleProjectUnitTests(String pathToExecGradleProject, String pathToRootGradleProject) {
		String javaAgentInitScriptPathString = new File(".").getAbsolutePath()+File.separator+"gradle-init-scripts"+File.separator+"javaagent.gradle";
		executeCommand(" ./gradlew test -I "+javaAgentInitScriptPathString+" -p "+pathToExecGradleProject+" --stacktrace --continue", pathToRootGradleProject);
	}
	
	public static String executeCommand(String command, String dir) {
		StringBuilder output = new StringBuilder();
		StringBuilder error = new StringBuilder();
		boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
		try {
			ProcessBuilder builder = new ProcessBuilder();
			if (isWindows) {
				builder.command("cmd.exe", "/c", command);
			} else {
				builder.command("sh", "-c", command);
			}
			if (dir != null) {
				builder.directory(new File(dir));
			}
			builder.redirectOutput(Redirect.INHERIT);
			builder.redirectError(Redirect.INHERIT);

			Process process;
			boolean status;
			process = builder.start();

			status = process.waitFor(2, TimeUnit.HOURS);
			if (status) {
				BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
				String line = "";
				while ((line = reader.readLine()) != null) {
					output.append(line + "\n");
				}
				reader.close();
				if (process.exitValue() != 0) {
					BufferedReader readerErr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
					String lineErr = "";
					while ((lineErr = readerErr.readLine()) != null) {
						error.append(lineErr + "\n");
						System.out.println(lineErr);
					}

					readerErr.close();
					System.out.println(error.toString());
				}
			} else {
				process.destroy();
				try {
					if (!process.waitFor(5, TimeUnit.SECONDS)) {
						process.destroyForcibly();
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					process.destroyForcibly();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
			Thread.currentThread().interrupt();
		}
		String result = output.toString();
		System.out.println(result);
		return result;
	}
	
	public static void setJavaVersion(long version) {
        try {
                Process process = Runtime.getRuntime()
                              .exec(String.format("update-alternatives --set java %s", javaVersionPaths.get(version)));
                int exitCode = process.waitFor();
                if (exitCode != 0 ){
                        System.out.println("Error occured, non zero return value, "+String.valueOf(exitCode));
                        BufferedReader error = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                        String errorString = error.readLine();
                        System.out.println(errorString);
                }
        } catch (IOException | InterruptedException e) {
                System.out.println(e);
        }
	}
}
