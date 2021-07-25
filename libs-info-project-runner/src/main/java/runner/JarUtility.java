package runner;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class JarUtility {
	public static List<String> addedLibs = new ArrayList<String>();
	public static Map<String, ArrayList<Object>> libsToCountsAndClasses = new HashMap<String, ArrayList<Object>>();
	public static String configPath = Paths.get(new File(".").getAbsolutePath()).getParent().getParent().toString()+"/src/main/resources/config.properties";
	public static String libsInfoPath;
	
	public static void initLibsToCountsAndClasses(String build, JSONArray projects) {
		libsToCountsAndClasses.clear();
		populateAddedLibs();

		Iterator<JSONObject> iterator = projects.iterator();
        while (iterator.hasNext()) {
        	JSONObject projectObject = (JSONObject)iterator.next();
        	if (!addedLibs.contains(projectObject.get("libName")) && projectObject.get("build").equals(build)) {
        		String pathToJar="", pathToRootPrj = "";
        		if (build.equals("maven")) {
        			pathToJar = File.separator+"target"+File.separator+projectObject.get("generatedJarName")+".jar";
        		}
        		else if (build.equals("gradle")) {
        			pathToJar = File.separator+"build/libs"+File.separator+projectObject.get("generatedJarName")+".jar";
        			pathToRootPrj = new File(".").getAbsolutePath()+File.separator+"projects"+File.separator+projectObject.get("rootDir");
        		}
        		String pathToProject = new File(".").getAbsolutePath()+File.separator+"projects"+File.separator+projectObject.get("execDir");
            	String generatedJarName = pathToProject+File.separator+pathToJar;
            	String tmpFolder = new File(".").getAbsolutePath()+File.separator+"projects"+File.separator+projectObject.get("execDir")+File.separator+"tmp";
            	String libName = (String)projectObject.get("libName");
            	ArrayList<Object> countsAndClasses = getPublicProtectedMethodsCountAndClasses(generatedJarName, tmpFolder, pathToProject, pathToRootPrj, build);
            	libsToCountsAndClasses.putIfAbsent(libName, new ArrayList<Object>(Arrays.asList(0, 0, "")));
            	ArrayList<Object> libVals = libsToCountsAndClasses.get(libName);
            	libVals.set(0, (Integer)libVals.get(0) + (Integer)countsAndClasses.get(0));
            	libVals.set(1, (Integer)libVals.get(1) + (Integer)countsAndClasses.get(1));
            	libVals.set(2, ((String)libVals.get(2)).concat((String)countsAndClasses.get(2)));
        	}
        }
        addToLibsInfo(libsToCountsAndClasses);
	}
	
	public static void populateAddedLibs() {
		try (FileReader input = new FileReader(configPath)) {
            Properties prop = new Properties();
            prop.load(input);
            libsInfoPath = prop.getProperty("libsInfoPath");
            if (new File(libsInfoPath).exists()) {
				String row;
				BufferedReader reader = new BufferedReader(new FileReader(libsInfoPath));
				while ((row = reader.readLine()) != null) {
				    String[] data = row.split(",");
				    addedLibs.add(data[0]);
				}
				reader.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
	}
	
	public static void addToLibsInfo(Map<String, ArrayList<Object>> libsToCountsAndClasses) {
		try (FileReader input = new FileReader(configPath))
		{
		    Properties prop = new Properties();
			prop.load(input); 
			FileWriter writer = new FileWriter(libsInfoPath, true);	
			if (new File(libsInfoPath).length() == 0) {
				writer.write("Library Name,No. of Public/Protected Methods,No. of Methods Called By Tests,Classes\n");
				writer.write("unknownLib,0,0, \n");
			}
			for (String lib: libsToCountsAndClasses.keySet()) {
				writer.write(lib.trim()+","+libsToCountsAndClasses.get(lib).get(0)+","+libsToCountsAndClasses.get(lib).get(1)+","+libsToCountsAndClasses.get(lib).get(2)+"\n");
		    }
			writer.flush();
			writer.close();
		} catch (IOException ex) {
		    ex.printStackTrace();
		}
	}
	
	public static ArrayList<Object> getPublicProtectedMethodsCountAndClasses(String crunchifyJarName, String tmpFolder, String pathToProject, String pathToRootPrj, String build) {
		int count = 0;
		Set<String> classNames = new HashSet<String>();

		try {
			JarInputStream crunchifyJarFile = new JarInputStream(new FileInputStream(crunchifyJarName));
			JarEntry crunchifyJar;
			
			File destDir = new File(tmpFolder);
			if (!destDir.exists()) {
				destDir.mkdir();
			}
			HashMap<String, String> deps = new HashMap<String, String>();
			if (build.equals("maven"))
				deps = mvnGetDependencies(destDir);
			else if (build.equals("gradle"))
				deps = gradleGetDependencies(destDir, pathToProject, pathToRootPrj);

			List<URL> classLoaderURLs = new ArrayList<>();
			classLoaderURLs.add(new File(crunchifyJarName).toURI().toURL());

			for (String dependency : deps.keySet()) {
				classLoaderURLs.add(new File(deps.get(dependency)).toURI().toURL());
			}

			URL[] classLoaderURLsAsArray = new URL[classLoaderURLs.size()];
			URLClassLoader child = new URLClassLoader(classLoaderURLs.toArray(classLoaderURLsAsArray),
					JarUtility.class.getClass().getClassLoader());

			for (String dependency : deps.keySet()) {
				addDependencyToLibsInfo(dependency, new File(deps.get(dependency)), child);
			}

			while (true) {
				crunchifyJar = crunchifyJarFile.getNextJarEntry();
				if (crunchifyJar == null) {
					break;
				}
				if ((crunchifyJar.getName().endsWith(".class"))) {
					String completeClassName = crunchifyJar.getName().replaceAll("/", "\\.");
					String className = completeClassName.substring(0, completeClassName.lastIndexOf('.'));
					classNames.add(className);
					
					// get counts
					try {
						Class<?> c = Class.forName(className, false, child);
						Method[] classMethods = c.getDeclaredMethods();
						for (Method m : classMethods) {
							int modifier = m.getModifiers();
							if (Modifier.isPublic(modifier) || Modifier.isProtected(modifier))
								count++;
						}
					} catch (NoClassDefFoundError e) {
						System.out.println("No class def found "+ e);
					} catch (UnsupportedClassVersionError e) {
						System.out.println("No class def found "+ e);
					} catch (Exception e) {
						System.out.println("Error while parsing jar " + e);
					}
				}
			}
			crunchifyJarFile.close();
			deleteDirectory(destDir);
		} catch (Exception e) {
			System.out.println("Error while parsing jar" + e.toString());
		}
		if (classNames.isEmpty()) classNames.add("");
		return new ArrayList<Object>(Arrays.asList(count, 0, String.join(":", classNames)));
	}
	
	public static HashMap<String, String> mvnGetDependencies(File tmpDir) {
		HashMap<String, String> dependencies = new HashMap<String, String>();
		InvocationRequest request = new DefaultInvocationRequest();
		File pomFile = new File(tmpDir.getParent()+File.separator+"pom.xml"); 
		request.setPomFile(pomFile);
		request.setGoals(Arrays.asList("dependency:list"));
		request.setMavenOpts("-DincludeScope=runtime -DoutputFile="+tmpDir.getPath()+File.separator+"deps-output.txt -DoutputAbsoluteArtifactFilename=true");
		request.setJavaHome(new File("/usr/lib/jvm/java-8-openjdk-amd64/jre/"));
		System.setProperty("maven.home", "/usr/share/maven"); //System.getenv("MAVEN_HOME")) - windows; //TODO - pick it up based on system
		
		Invoker invoker = new DefaultInvoker();
		try {
			invoker.execute( request );
		} catch (MavenInvocationException e) {
			e.printStackTrace();
		}
		try {
			String[] rows = new String[(int) new File(tmpDir.getPath()+File.separator+"deps-output.txt").length()]; String row; int count = 0;
			BufferedReader reader = new BufferedReader(new FileReader(tmpDir.getPath()+File.separator+"deps-output.txt"));
			while ((row = reader.readLine()) != null) {
				rows[count++] = row;
			}
			reader.close();
			String[] depsData = new String[10];
			for (String rowString : rows) {
				if (rowString!=null)
					depsData = rowString.split(":");
				if (depsData.length>0 && new File(depsData[depsData.length-1]).exists()) {
					depsData[0].trim();
					dependencies.put(String.join(":", depsData[0], depsData[1], depsData[3]), depsData[depsData.length-1]);
				}
			}
		} catch (IOException ex) {
		    ex.printStackTrace();
		}
		return dependencies;
	}
	
	public static HashMap<String, String> gradleGetDependencies(File tmpDir, String pathToProject, String pathToRootPrj) {
		HashMap<String, String> dependencies = new HashMap<String, String>();
		String getDepsInitScriptPathString = new File(".").getAbsolutePath()+File.separator+"gradle-init-scripts"+File.separator+"get-dependencies.gradle";
		DependentTestRunner.executeCommand(" ./gradlew -I "+getDepsInitScriptPathString+" -p "+pathToProject+" getDeps --stacktrace > "+tmpDir.getPath()+File.separator+"deps-output.txt", pathToRootPrj);
		
		try {
			String[] rows = new String[(int) new File(tmpDir.getPath()+File.separator+"deps-output.txt").length()]; String row; int count = 0;
			BufferedReader reader = new BufferedReader(new FileReader(tmpDir.getPath()+File.separator+"deps-output.txt"));
			while ((row = reader.readLine()) != null) {
				rows[count++] = row;
			}
			reader.close();
			String libName="", dep=""; count = -1;
			for (String rowString : rows) {
				if (rowString!=null && !rowString.isEmpty() && new File(rowString).exists()) {
					dep = rowString;
					count = 0;
				}
				if (count==1 || count==2) {libName+=rowString+":";}
				if (count==3) {
					libName+=rowString;
					if (new File(dep).exists())
						dependencies.put(libName, dep);
					libName = "";
					dep = "";
				}
				count++;
			}
		} catch (IOException ex) {
		    ex.printStackTrace();
		}
		return dependencies;
	}
	
	public static void addDependencyToLibsInfo(String dependencyName, File dependency, URLClassLoader child) {
		if (!dependency.getName().endsWith(".jar"))
			return;
		if (libsToCountsAndClasses.containsKey(dependencyName))
			return;
		int count = 0;
		Set<String> classNames = new HashSet<String>();
		try {
			JarInputStream crunchifyJarFile = new JarInputStream(new FileInputStream(dependency));
			JarEntry crunchifyJar;
			while (true) {
				crunchifyJar = crunchifyJarFile.getNextJarEntry();
				if (crunchifyJar == null) {
					break;
				}
				if ((crunchifyJar.getName().endsWith(".class"))) {
					String completeClassName = crunchifyJar.getName().replaceAll("/", "\\.");
					String className = completeClassName.substring(0, completeClassName.lastIndexOf('.'));
					classNames.add(className);
					
					// get counts
					try {
						Class<?> c = Class.forName(className, false, child);
						Method[] classMethods = c.getDeclaredMethods();
						for (Method m : classMethods) {
							int modifier = m.getModifiers();
							if (Modifier.isPublic(modifier) || Modifier.isProtected(modifier))
								count++;
						}
					} catch (NoClassDefFoundError e) {
						System.out.println("No class def found "+ e);
					} catch (UnsupportedClassVersionError e) {
						System.out.println("Unsupported class version "+ e);
					} catch (IllegalAccessError e) {
						System.out.println("Illegal Access "+ e);
					} catch (Exception e) {
						System.out.println("Error while parsing jar " + e);
					}
				}
			}
			crunchifyJarFile.close();
			libsToCountsAndClasses.putIfAbsent(dependencyName, new ArrayList<Object>(Arrays.asList(count, 0, String.join(":", classNames))));
		} catch (Exception e) {
			System.out.println(e);
		}
	}

	private static void deleteDirectory(File fileOrDirectory) {
		if (fileOrDirectory.isDirectory())
			for (File child : fileOrDirectory.listFiles())
				deleteDirectory(child);

		fileOrDirectory.delete();
	}
}