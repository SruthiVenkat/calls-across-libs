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

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
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
		runMavenProjects();
		runGradleProjects();
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
				    String[] data = row.split("\t");
				    addedLibs.add(data[0]);
				}
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
	}
	
	public static void runMavenProjects() {
		// get poms for projects
		Map<String, File> pomList = new HashMap<String, File>();
		JSONParser jsonParser = new JSONParser();
        try (FileReader reader = new FileReader(new File(".").getAbsolutePath()+File.separator+"projects"+File.separator+"projects-list.json"))
        {
            Object obj = jsonParser.parse(reader);
            JSONArray projects = (JSONArray) obj;
            Iterator<JSONObject> iterator = projects.iterator();
            while (iterator.hasNext()) {
            	JSONObject projectObject = (JSONObject)iterator.next();
            	if (!addedLibs.contains(projectObject.get("libName"))) {
	            	File pomFile = new File(new File(".").getAbsolutePath()+File.separator+"projects"+File.separator+projectObject.get("folderName")+File.separator+"pom.xml");
	     			writeXMLToProjectPOM(pomFile);
	    			mvnInstallProjects(pomFile); // generate wars and jars
	            	pomList.put((String)projectObject.get("libName"), pomFile);
            	}
            }
        } catch (Exception e) {
			System.out.println("Error while reading file with project list" + e.toString());		
		}
		
		// get total API counts for projects and packages in each project
		JarUtility.initLibsToCountsAndClasses("maven");
		
		// run unit tests to get data
		for (String lib: pomList.keySet()) {
			try (FileReader input = new FileReader(configPath)) {
	            Properties prop = new Properties();
	            prop.load(input);
	            prop.setProperty("outputPath", outputPath+lib+".tsv");
	            prop.store(new FileWriter(configPath, false), null);
	            runMvnProjectUnitTests(pomList.get(lib));
	            prop.setProperty("outputPath", outputPath);
	            prop.store(new FileWriter(configPath, false), null);
            } catch (IOException ex) {
		            ex.printStackTrace();
		     }
		}
	}
	
	public static void writeXMLToProjectPOM(File xmlFile) {
		SAXBuilder builder = new SAXBuilder();
		Document doc;
		try {
			doc = (Document) builder.build(xmlFile);
			Element rootNode = doc.getRootElement();

			Element packagingElement = rootNode.getChild("packaging", rootNode.getNamespace());
			if (packagingElement != null) {
				rootNode.removeChild("packaging", rootNode.getNamespace());
			}
			Element packaging = new Element("packaging", rootNode.getNamespace()).setText("war");
			rootNode.addContent(packaging);
			Element buildFromXML = rootNode.getChild("build", rootNode.getNamespace());
			if (buildFromXML == null) {
				Element newBuild = new Element("build", rootNode.getNamespace());
				rootNode.addContent(newBuild);
				buildFromXML = newBuild;
			}
			Element pluginsElement = buildFromXML.getChild("plugins", rootNode.getNamespace());
			if (pluginsElement == null) {
				Element pluginsToAdd = new Element("plugins", rootNode.getNamespace());
				buildFromXML.addContent(pluginsToAdd);
				pluginsElement = pluginsToAdd;
			}
			if (!checkIfPluginExistsInProjectPOM(pluginsElement, rootNode, "org.apache.maven.plugins",
					"maven-war-plugin")) {
				Element pluginToAdd = new Element("plugin", rootNode.getNamespace());
				pluginToAdd.addContent(new Element("groupId", rootNode.getNamespace()).setText("org.apache.maven.plugins"));
				pluginToAdd.addContent(new Element("artifactId", rootNode.getNamespace()).setText("maven-war-plugin"));

				Element configurationToAdd = new Element("configuration", rootNode.getNamespace());
				configurationToAdd.addContent(new Element("failOnMissingWebXml", rootNode.getNamespace()).setText("false"));
				configurationToAdd.addContent(new Element("attachClasses", rootNode.getNamespace()).setText("true"));
				pluginToAdd.addContent(configurationToAdd);

				pluginsElement.addContent(pluginToAdd);
			} else {
				List<Element> pluginElements =  pluginsElement.getChildren("plugin", rootNode.getNamespace());
				Element mvnWarPluginElement = pluginElements.stream().filter(pluginChild -> pluginChild.getChildText("artifactId", rootNode.getNamespace()).equals("maven-war-plugin")).findAny().orElse(null);
				Element configurationToAdd = mvnWarPluginElement.getChild("configuration", rootNode.getNamespace());
				if (configurationToAdd==null) {
					configurationToAdd = new Element("configuration", rootNode.getNamespace());
					mvnWarPluginElement.addContent(configurationToAdd);
				}
				Element failOnMissingWebXml = configurationToAdd.getChild("failOnMissingWebXml", rootNode.getNamespace());
				if (failOnMissingWebXml==null) {
					failOnMissingWebXml = new Element("failOnMissingWebXml", rootNode.getNamespace()).setText("false");
					configurationToAdd.addContent(failOnMissingWebXml);
				}
				Element attachClasses = configurationToAdd.getChild("attachClasses", rootNode.getNamespace());
				if (attachClasses==null) {
					attachClasses = new Element("attachClasses", rootNode.getNamespace()).setText("true");
					configurationToAdd.addContent(attachClasses);
				}
			}
				
			XMLOutputter xmlOutput = new XMLOutputter();
			xmlOutput.setFormat(Format.getPrettyFormat());
			xmlOutput.output(doc, new FileWriter(xmlFile));
		}catch (JDOMException | IOException e) {
			e.printStackTrace();
		}
	}
	
	public static Boolean checkIfPluginExistsInProjectPOM(Element pluginsElement, Element rootNode, String groupID, String artifactID) {
		Boolean pluginPresent = false;
		List<Element> pluginElementChidren = pluginsElement.getChildren("plugin", rootNode.getNamespace());
		for (Element pluginChild : pluginElementChidren) {
			if (pluginChild.getChild("groupId", rootNode.getNamespace()) != null && pluginChild.getChild("artifactId", rootNode.getNamespace()) != null
				&& pluginChild.getChildText("groupId", rootNode.getNamespace()).equals(groupID) && pluginChild.getChildText("artifactId", rootNode.getNamespace()).equals(artifactID)) {
				pluginPresent = true;
			}
		}
		return pluginPresent;
	}

	public static void mvnInstallProjects(File pomFile) {
		InvocationRequest request = new DefaultInvocationRequest();
		request.setPomFile(pomFile);
		request.setGoals(Arrays.asList("clean", "install"));
		
		request.setMavenOpts("-Dlicense.skip=true -DskipTests=true -Dcheckstyle.skip=true");

		request.setJavaHome(new File("/usr/lib/jvm/java-8-openjdk-amd64/jre/"));
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
		properties.setProperty("argLine", "-javaagent:/home/vishal/Documents/Waterloo/PL/calls-across-libs/libs-info-agent/target/libs-info-agent-1.0-SNAPSHOT.jar -Xbootclasspath/p:/home/vishal/.m2/repository/org/javassist/javassist/3.27.0-GA/javassist-3.27.0-GA.jar:/home/vishal/Documents/Waterloo/PL/calls-across-libs/libs-info-agent/target/libs-info-agent-1.0-SNAPSHOT.jar");
		request.setProperties(properties);
		request.setJavaHome(new File("/usr/lib/jvm/java-8-openjdk-amd64/jre/"));
		System.setProperty("maven.home", "/usr/share/maven"); //System.getenv("MAVEN_HOME")) - windows; //TODO - pick it up based on system

		Invoker invoker = new DefaultInvoker();
		try {
			invoker.execute( request );
		} catch (MavenInvocationException e) {
			e.printStackTrace();
		}
	}
	
	public static void runGradleProjects() {
		//"/home/vishal/Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/projects/nextflow"
		installGradleProjects("tmp");
		// get total API counts for projects and packages in each project
		JarUtility.initLibsToCountsAndClasses("gradle");
		runGradleProjectUnitTests("tmp");
	}
	
	public static void installGradleProjects(String PATH_TO_GRADLE_PROJECT) {
		String warInitScriptPathString = new File(".").getAbsolutePath()+File.separator+"gradle-init-scripts"+File.separator+"war.gradle";
		executeCommand(" ./gradlew clean build -x test --stacktrace", PATH_TO_GRADLE_PROJECT, false); // jar
		executeCommand(" ./gradlew build -I "+warInitScriptPathString+" -x test --stacktrace", PATH_TO_GRADLE_PROJECT, false); // war
	}
	
	public static void runGradleProjectUnitTests(String PATH_TO_GRADLE_PROJECT) {
		String javaAgentInitScriptPathString = new File(".").getAbsolutePath()+File.separator+"gradle-init-scripts"+File.separator+"war.gradle";
		executeCommand(" ./gradlew test -I "+javaAgentInitScriptPathString+"--stacktrace", PATH_TO_GRADLE_PROJECT, true);
	}
	
	public static String executeCommand(String command, String dir, boolean instrument) {
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
			if (instrument)
				envMap.put("JAVA_OPTS", JAVA_OPTS);

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
