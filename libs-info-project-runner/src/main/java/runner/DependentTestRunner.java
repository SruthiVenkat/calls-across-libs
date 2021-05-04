package runner;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

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

import db.DatabaseConnector;

public class DependentTestRunner {
	public static final String agentPath = "/home/vishal/Documents/Waterloo/PL/calls-across-libs/libs-info-agent/target/libs-info-agent-1.0-SNAPSHOT.jar";
	public static final String javassistJarPath = "/home/vishal/.m2/repository/org/javassist/javassist/3.27.0-GA/javassist-3.27.0-GA.jar";
	public static final String dbPath = "/home/vishal/Documents/Waterloo/PL/calls-across-libs/libs-info-db/target/libs-info-db-1.0-SNAPSHOT.jar";

	public static void main(String[] args) {
		// connect to database and create the required procedures
		DatabaseConnector connector = DatabaseConnector.buildDatabaseConnector();
		connector.connect();
		connector.createSQLProcForAPIProportionCalled(); 
		connector.createSQLProcForJaccardSimilarity();
		connector.createSQLProcForFetchingCallsToALibrary();
		
		// get poms for projects
		List<String> pomList = new ArrayList<>();
		JSONParser jsonParser = new JSONParser();
        try (FileReader reader = new FileReader(new File(".").getAbsolutePath()+File.separator
				+"projects"+File.separator+"projects-list.json"))
        {
            Object obj = jsonParser.parse(reader);
            JSONArray projects = (JSONArray) obj;
            Iterator<JSONObject> iterator = projects.iterator();
            while (iterator.hasNext()) {
            	JSONObject projectObject = (JSONObject)iterator.next();
            	pomList.add(new File(".").getAbsolutePath()+File.separator
        				+"projects"+File.separator+projectObject.get("folderName")+File.separator+"pom.xml");
            }
        } catch (Exception e) {
			System.out.println("Error while reading file with project list" + e.toString());		
		}

		for (String pomFilePath: pomList) {
			File pomFile = new File(pomFilePath);
			writeXMLToProjectPOM(pomFile, false);
			mvnInstallProjects(pomFile, true); // generate jars
			writeXMLToProjectPOM(pomFile, true);
			mvnInstallProjects(pomFile, false); // generate wars
		}
		
		// get total API counts for projects and packages in each project
		JarUtility.initLibsToCountsAndClasses(connector);
		
		// run unit tests to get data
		for (String pomFilePath: pomList)
			runProjectUnitTests(new File(pomFilePath));
	}
	
	public static void writeXMLToProjectPOM(File xmlFile, Boolean packageAsWar) {
		SAXBuilder builder = new SAXBuilder();
		Document doc;
		try {
			doc = (Document) builder.build(xmlFile);

			Element rootNode = doc.getRootElement();

			Element packagingElement = rootNode.getChild("packaging", rootNode.getNamespace());
			if (packagingElement != null) {
				rootNode.removeChild("packaging", rootNode.getNamespace());
			}
			String packagingString = packageAsWar == true ? "war" : "jar";
			Element packaging = new Element("packaging", rootNode.getNamespace()).setText(packagingString);
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
				pluginToAdd.addContent(
						new Element("groupId", rootNode.getNamespace()).setText("org.apache.maven.plugins"));
				pluginToAdd.addContent(new Element("artifactId", rootNode.getNamespace()).setText("maven-war-plugin"));

				Element configurationToAdd = new Element("configuration", rootNode.getNamespace());
				configurationToAdd
						.addContent(new Element("failOnMissingWebXml", rootNode.getNamespace()).setText("false"));
				pluginToAdd.addContent(configurationToAdd);

				pluginsElement.addContent(pluginToAdd);
			}
			if (!checkIfPluginExistsInProjectPOM(pluginsElement, rootNode, "org.apache.maven.plugins",
					"maven-surefire-plugin")) {
				Element pluginToAdd = new Element("plugin", rootNode.getNamespace());
				pluginToAdd.addContent(
						new Element("groupId", rootNode.getNamespace()).setText("org.apache.maven.plugins"));
				pluginToAdd.addContent(new Element("artifactId", rootNode.getNamespace()).setText("maven-surefire-plugin"));
				pluginToAdd.addContent(new Element("version", rootNode.getNamespace()).setText("2.22.0"));

				Element configurationToAdd = new Element("configuration", rootNode.getNamespace());
				configurationToAdd
						.addContent(new Element("argLine", rootNode.getNamespace()).setText("-Xbootclasspath/p:\""+javassistJarPath+"\":\""+dbPath
								+"\" -javaagent:\""+agentPath+"\""));
				pluginToAdd.addContent(configurationToAdd);

				pluginsElement.addContent(pluginToAdd);
			}

			Element dependencies = rootNode.getChild("dependencies", rootNode.getNamespace());
			if (dependencies == null) {
				dependencies = new Element("dependencies");
				rootNode.addContent(dependencies);
			}
			if (!checkIfDependencyExistsInProjectPOM(dependencies, rootNode, "org.postgresql", "postgresql")) {
				Element dependency1 = new Element("dependency", rootNode.getNamespace());
				dependency1.addContent(new Element("groupId", rootNode.getNamespace()).setText("org.postgresql"));
				dependency1.addContent(new Element("artifactId", rootNode.getNamespace()).setText("postgresql"));
				dependency1.addContent(new Element("version", rootNode.getNamespace()).setText("42.2.14"));
				dependencies.addContent(dependency1);
			}

			XMLOutputter xmlOutput = new XMLOutputter();
			xmlOutput.setFormat(Format.getPrettyFormat());
			xmlOutput.output(doc, new FileWriter(xmlFile));

		} catch (JDOMException | IOException e) {
			e.printStackTrace();
		}
	}
	
	public static Boolean checkIfPluginExistsInProjectPOM(Element pluginsElement, Element rootNode, String groupID, String artifactID) {
		Boolean pluginPresent = false;
		List<Element> pluginElementChidren = pluginsElement.getChildren("plugin", rootNode.getNamespace());
		for (Element pluginChild : pluginElementChidren) {
			if (pluginChild.getChild("groupId", rootNode.getNamespace()) != null
					&& pluginChild.getChild("artifactId", rootNode.getNamespace()) != null
					&& pluginChild.getChildText("groupId", rootNode.getNamespace()).equals(groupID)
					&& pluginChild.getChildText("artifactId", rootNode.getNamespace())
							.equals(artifactID)) {
				pluginPresent = true;
			}
		}
		return pluginPresent;
	}
	
	public static Boolean checkIfDependencyExistsInProjectPOM(Element dependencies, Element rootNode, String groupID, String artifactID) {
		Boolean dependencyPresent = false;
		List<Element> dependencyElementChildren = dependencies.getChildren("dependency", rootNode.getNamespace());
		for (Element dependencyChild : dependencyElementChildren) {
			if (dependencyChild.getChild("groupId", rootNode.getNamespace()) != null
					&& dependencyChild.getChild("artifactId", rootNode.getNamespace()) != null
					&& dependencyChild.getChildText("groupId", rootNode.getNamespace())
							.equals(groupID)
					&& dependencyChild.getChildText("artifactId", rootNode.getNamespace()).equals(artifactID)) {
				dependencyPresent = true;
			}
		}
		return dependencyPresent;
	}

	public static void mvnInstallProjects(File pomFile, boolean cleanInstall) {
		InvocationRequest request = new DefaultInvocationRequest();
		request.setPomFile(pomFile);
		if (cleanInstall)
			request.setGoals(Arrays.asList("clean", "install"));
		else
			request.setGoals(Arrays.asList("install"));
		
		request.setMavenOpts("-Dlicense.skip=true -DskipTests=true -Dcheckstyle.skip=true");

		request.setJavaHome(new File("/usr/lib/jvm/java-8-openjdk-amd64/jre/"));
		System.setProperty("maven.home", "/usr/share/maven"); //System.getenv("MAVEN_HOME")) - windows; //TODO - pick it up based on system
		//System.setProperty("maven.home", System.getenv("MAVEN_HOME")); //TODO - pick it up based on system
		Invoker invoker = new DefaultInvoker();
		try {
			invoker.execute( request );
		} catch (MavenInvocationException e) {
			e.printStackTrace();
		}
		

	}
	
	public static void runProjectUnitTests(File pomFile) {
		InvocationRequest request = new DefaultInvocationRequest();
		request.setPomFile(pomFile);
		request.setGoals(Arrays.asList("test"));
		request.setMavenOpts("-Dlicense.skip=true");

		request.setJavaHome(new File("/usr/lib/jvm/java-8-openjdk-amd64/jre/"));
		System.setProperty("maven.home", "/usr/share/maven"); //System.getenv("MAVEN_HOME")) - windows; //TODO - pick it up based on system
		//System.setProperty("maven.home", System.getenv("MAVEN_HOME")); //TODO - pick it up based on system

		Invoker invoker = new DefaultInvoker();
		try {
			invoker.execute( request );
		} catch (MavenInvocationException e) {
			e.printStackTrace();
		}
	}
}
