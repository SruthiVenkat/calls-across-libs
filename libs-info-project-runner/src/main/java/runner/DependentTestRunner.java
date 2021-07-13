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

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class DependentTestRunner {
	public static String agentPath;
	public static String javassistJarPath;
	public static String postgresJarPath;
	public static String JAVA_OPTS;
	public static String outputPath;
	public static List<String> addedLibs = new ArrayList<String>();
	public static String configPath = Paths.get(new File(".").getAbsolutePath()).getParent().getParent().toString()+"/src/main/resources/config.properties";

	public static void main(String[] args) {
		loadProperties();	// load properties - paths
		
		JSONParser jsonParser = new JSONParser();
        try (FileReader reader = new FileReader(new File(".").getAbsolutePath()+File.separator+"projects"+File.separator+"projects-list.json"))
        {
            Object obj = jsonParser.parse(reader);
            JSONArray projects = (JSONArray) obj;
            JSONArray gradleProjects = new JSONArray();
            JSONArray mvnProjects = new JSONArray();
            Iterator<JSONObject> iterator = projects.iterator();
            while (iterator.hasNext()) {
            	JSONObject projectObject = (JSONObject)iterator.next();
            		if (projectObject.get("build").equals("maven")) {
            			projectObject.put("libName", mavenGetGAV(new File(".").getAbsolutePath()+File.separator+"projects"
                				+File.separator+projectObject.get("execDir")+File.separator+"pom.xml"));
            			mvnProjects.add(projectObject);
            		} else if (projectObject.get("build").equals("gradle")) {
                		projectObject.put("libName", gradleGetGAV(new File(".").getAbsolutePath()+File.separator+"projects"
                				+File.separator+projectObject.get("rootDir"), (String)projectObject.get("gavOf")));
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
			System.out.println("Error while reading file with project list " + e.toString());		
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
            JAVA_OPTS =  "-javaagent:"+agentPath+" -Xbootclasspath/p:"+javassistJarPath+":"+agentPath;
            
            String libsInfoPath = prop.getProperty("libsInfoPath");
            if (new File(libsInfoPath).exists()) {
				String row;
				BufferedReader reader = new BufferedReader(new FileReader(libsInfoPath));
				while ((row = reader.readLine()) != null) {
				    String[] data = row.split(",");
				    addedLibs.add(data[0]);
				}
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
	}
	
	public static String mavenGetGAV(String pomFile) {
		String gav = "";
		InvocationRequest request = new DefaultInvocationRequest();
		request.setPomFile(new File(pomFile));
		request.setGoals(Arrays.asList("help:evaluate"));
		request.setJavaHome(new File("/usr/lib/jvm/java-8-openjdk-amd64/jre/"));
		System.setProperty("maven.home", "/usr/share/maven"); //System.getenv("MAVEN_HOME")) - windows; //TODO - pick it up based on system
		
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
		} catch (MavenInvocationException e) {
			e.printStackTrace();
		} catch (IOException ex) {
		    ex.printStackTrace();
		}
		return gav;
	}
	
	public static String gradleGetGAV(String pathToRootPrj, String gavOf) {
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
			mvnInstallProjects(pomFile); // generate jars
        }
	}
		
	public static void runMavenProjectsTest(JSONArray mavenProjects, Map<String, File> pomList) {	
		for (String lib: pomList.keySet()) {
			try (FileReader input = new FileReader(configPath)) {
	            Properties prop = new Properties();
	            prop.load(input);
	            prop.setProperty("invocationsOutputPath", outputPath+lib+"-invocations.csv");
	            prop.setProperty("fieldsOutputPath", outputPath+lib+"-fields.csv");
	            prop.setProperty("subtypingOutputPath", outputPath+lib+"-subtyping.csv");
	            prop.setProperty("annotationsOutputPath", outputPath+lib+"-annotations.csv");
	            prop.setProperty("runningLibrary", lib);
	            prop.store(new FileWriter(configPath, false), null);
	            runMvnProjectUnitTests(pomList.get(lib));
	            prop.setProperty("invocationsOutputPath", "");
	            prop.setProperty("fieldsOutputPath", "");
	            prop.setProperty("runningLibrary", "");
	            prop.setProperty("subtypingOutputPath", "");
	            prop.setProperty("annotationsOutputPath", "");
	            prop.store(new FileWriter(configPath, false), null);
            } catch (IOException ex) {
		            ex.printStackTrace();
		     }
		}
	}

	public static void mvnInstallProjects(File pomFile) {
		InvocationRequest request = new DefaultInvocationRequest();
		request.setPomFile(pomFile);
		request.setGoals(Arrays.asList("clean", "install"));
		request.setMavenOpts("-Dlicense.skip=true -DskipTests=true -Dcheckstyle.skip=true");
		//request.setJavaHome(new File("/usr/lib/jvm/java-8-openjdk-amd64/jre/"));
		System.setProperty("maven.home", "/usr/share/maven"); //System.getenv("MAVEN_HOME")) - windows; //TODO - pick it up based on system
		
		Invoker invoker = new DefaultInvoker();
		try {
			invoker.execute( request );
		} catch (MavenInvocationException e) {
			e.printStackTrace();
		}
	}
	
	public static void runMvnProjectUnitTests(File pomFile) {
		InvocationRequest request = new DefaultInvocationRequest();
		request.setPomFile(pomFile);
		request.setGoals(Arrays.asList("test")); 
		Properties properties = new Properties();
		properties.setProperty("argLine", JAVA_OPTS);
		request.setProperties(properties);
		//request.setJavaHome(new File("/usr/lib/jvm/java-8-openjdk-amd64/jre/"));
		//-javaagent:/home/vishal/Documents/Waterloo/PL/calls-across-libs/libs-info-agent/target/libs-info-agent-1.0-SNAPSHOT.jar -Xbootclasspath/p:/home/vishal/.m2/repository/org/javassist/javassist/3.27.0-GA/javassist-3.27.0-GA.jar:/home/vishal/Documents/Waterloo/PL/calls-across-libs/libs-info-agent/target/libs-info-agent-1.0-SNAPSHOT.jar"
		System.setProperty("maven.home", "/usr/share/maven"); //System.getenv("MAVEN_HOME")) - windows; //TODO - pick it up based on system

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
    		installGradleProjects(pathToGradleProject, pathToRootGradleProject); // generate wars and jars
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
	            prop.setProperty("invocationsOutputPath", outputPath+lib+"-invocations.csv");
	            prop.setProperty("fieldsOutputPath", outputPath+lib+"-fields.csv");
	            prop.setProperty("subtypingOutputPath", outputPath+lib+"-subtyping.csv");
	            prop.setProperty("annotationsOutputPath", outputPath+lib+"-annotations.csv");
	            prop.setProperty("runningLibrary", lib);
	            prop.store(new FileWriter(configPath, false), null);
	            runGradleProjectUnitTests(pathToGradleProject, pathToRootGradleProject);
	            prop.setProperty("invocationsOutputPath", "");
	            prop.setProperty("fieldsOutputPath", "");
	            prop.setProperty("subtypingOutputPath", "");
	            prop.setProperty("annotationsOutputPath", "");
	            prop.setProperty("runningLibrary", "");
	            prop.store(new FileWriter(configPath, false), null);
            } catch (IOException ex) {
		            ex.printStackTrace();
		     }
		}		
	}
	
	public static void installGradleProjects(String pathToExecGradleProject, String pathToRootGradleProject) {
		executeCommand(" ./gradlew clean build -x test -p "+pathToExecGradleProject+" --stacktrace", pathToRootGradleProject); // jar
	}
	
	public static void runGradleProjectUnitTests(String pathToExecGradleProject, String pathToRootGradleProject) {
		String javaAgentInitScriptPathString = new File(".").getAbsolutePath()+File.separator+"gradle-init-scripts"+File.separator+"javaagent.gradle";
		executeCommand(" ./gradlew test -I "+javaAgentInitScriptPathString+" -p "+pathToExecGradleProject+" --stacktrace", pathToRootGradleProject);
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
			Map<String, String> envMap = builder.environment();

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
}
