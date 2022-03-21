package runner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
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
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.regex.Pattern;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class JarUtility {
	public static List<String> addedLibs = new ArrayList<String>();
	public static Map<String, ArrayList<Object>> libsToCountsAndClasses = new HashMap<String, ArrayList<Object>>();
	public static Map<String, HashSet<String>> servicesInfo = new HashMap<String, HashSet<String>>();
	public static String configPath = Paths.get(new File(".").getAbsolutePath()).getParent().getParent().toString()+"/src/main/resources/config.properties";
	public static String libsInfoPath;
	public static String servicesInfoPath;
	
	public static void initLibsToCountsAndClasses(String build, JSONArray projects) {
		libsToCountsAndClasses.clear();
		populateAddedServices();

		Iterator<JSONObject> iterator = projects.iterator();
        while (iterator.hasNext()) {
        	JSONObject projectObject = (JSONObject)iterator.next();
        	populateAddedLibs((String) projectObject.get("libName"));
        	DependentTestRunner.setJavaVersion((long) projectObject.get("javaVersion"));
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
            	ArrayList<Object> countsAndClasses = getPublicProtectedMethodsCountAndClasses(generatedJarName, tmpFolder, pathToProject, pathToRootPrj, build, libName, (long) projectObject.get("javaVersion"));
            	libsToCountsAndClasses.putIfAbsent(libName, new ArrayList<Object>(Arrays.asList(0, 0, "")));
            	ArrayList<Object> libVals = libsToCountsAndClasses.get(libName);
            	libVals.set(0, (Integer)libVals.get(0) + (Integer)countsAndClasses.get(0));
            	libVals.set(1, 0);
            	libVals.set(2, ((String)libVals.get(2)).concat((String)countsAndClasses.get(2)));
        	}
        	addToLibsInfo((String) projectObject.get("libName"));
        }
        addToServicesInfo();
	}
	
	public static void populateAddedLibs(String lib) {
		addedLibs.clear();
		try (FileReader input = new FileReader(configPath)) {
            Properties prop = new Properties();
            prop.load(input);
            new File(prop.getProperty("outputPath")+File.separator+lib).mkdir();
            libsInfoPath = prop.getProperty("outputPath")+File.separator
            		+lib+File.separator+lib+"-libsInfo.tsv";
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
	
	public static void populateAddedServices() {
		try (FileReader input = new FileReader(configPath)) {
            Properties prop = new Properties();
            prop.load(input);
            servicesInfoPath = prop.getProperty("servicesInfoPath");
            if (new File(servicesInfoPath).exists()) {
            	String row;
    			BufferedReader reader = new BufferedReader(new FileReader(servicesInfoPath));
    			row = reader.readLine();
				while ((row = reader.readLine()) != null) {
				    String[] data = row.split("\t");
				    servicesInfo.putIfAbsent(data[0], new HashSet<String>());
				    servicesInfo.get(data[0]).add(data[1]);
				}
				servicesInfo.remove("SPI");
				reader.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
	}
	
	public static void addToLibsInfo(String library) {
		try (FileReader input = new FileReader(configPath)) {
			File file = new File(libsInfoPath);
			if (!file.exists()) file.createNewFile();
			FileWriter writer = new FileWriter(libsInfoPath, true);	
			if (new File(libsInfoPath).length() == 0) {
				writer.write("Library Name\tNo. of Public/Protected Methods\tNo. of Methods Called By Tests\tClasses\n");
				writer.write("unknownLib\t0\t0\t \n");
			}
			for (String lib: libsToCountsAndClasses.keySet()) {
				writer.write(lib.trim()+"\t"+libsToCountsAndClasses.get(lib).get(0)+"\t"+libsToCountsAndClasses.get(lib).get(1)+"\t"+libsToCountsAndClasses.get(lib).get(2)+"\n");
		    }
			writer.flush();
			writer.close();
			libsToCountsAndClasses.clear();
		} catch (IOException ex) {
		    ex.printStackTrace();
		}
	}
	
	public static void addToServicesInfo() {
		try (FileReader input = new FileReader(configPath)) {
		    Properties prop = new Properties();
			prop.load(input); 
			servicesInfoPath = prop.getProperty("servicesInfoPath");
			File file = new File(servicesInfoPath);
			if (!file.exists()) file.createNewFile();
			FileWriter writer = new FileWriter(servicesInfoPath, false);	
			if (new File(servicesInfoPath).length() == 0) {
				writer.write("SPI\tSPI Implementations\n");
			}
			for (String spi: servicesInfo.keySet()) {
				for (String impl: servicesInfo.get(spi))
					writer.write(spi+"\t"+impl+"\n");
		    }
			writer.flush();
			writer.close();
		} catch (IOException ex) {
		    ex.printStackTrace();
		}
	}
	
	public static ArrayList<Object> getPublicProtectedMethodsCountAndClasses(String crunchifyJarName, String tmpFolder, 
			String pathToProject, String pathToRootPrj, String build, String libName, long javaVersion) {
			File destDir = new File(tmpFolder);
			if (!destDir.exists()) {
				destDir.mkdir();
			}
			HashMap<String, String> deps = new HashMap<String, String>();
			if (build.equals("maven"))
				deps = mvnGetDependencies(destDir, javaVersion);
			else if (build.equals("gradle"))
				deps = gradleGetDependencies(destDir, pathToProject, pathToRootPrj);

			List<URL> classLoaderURLs = new ArrayList();//new ArrayList<>();
			try {
				classLoaderURLs.add(new File(crunchifyJarName).toURI().toURL());
				for (String dependency : deps.keySet()) {
					classLoaderURLs.add(new File(deps.get(dependency)).toURI().toURL());
				}
			} catch (MalformedURLException e) {
			}

			URL[] classLoaderURLsAsArray = new URL[classLoaderURLs.size()];
			URLClassLoader child = new URLClassLoader(classLoaderURLs.toArray(classLoaderURLsAsArray),
					JarUtility.class.getClass().getClassLoader());

			for (String dependency : deps.keySet()) {
				addDependencyToLibsInfo(dependency.trim(), new File(deps.get(dependency)), child);
			}

			deleteDirectory(destDir);
			return getDatafromJar(new File(crunchifyJarName), child, libName.trim());
	}
	
	public static HashMap<String, String> mvnGetDependencies(File tmpDir, long javaVersion) {
		HashMap<String, String> dependencies = new HashMap<String, String>();
		InvocationRequest request = new DefaultInvocationRequest();
		File pomFile = new File(tmpDir.getParent()+File.separator+"pom.xml"); 
		request.setPomFile(pomFile);
		request.setGoals(Arrays.asList("dependency:list"));
		request.setMavenOpts("-DincludeScope=compile -DoutputFile="+tmpDir.getPath()+File.separator+"deps-output.txt -DoutputAbsoluteArtifactFilename=true");
		request.setJavaHome(new File(DependentTestRunner.javaHomes.get(javaVersion)));
		
		//System.setProperty("maven.home", "/usr/share/maven");
		System.setProperty("maven.home", DependentTestRunner.mavenHome);
		Invoker invoker = new DefaultInvoker();
		try {
			invoker.execute( request );
		} catch (MavenInvocationException e) {
			e.printStackTrace();
		}
		
		request.setMavenOpts("-DincludeScope=runtime -DappendOutput=true -DoutputFile="+tmpDir.getPath()+File.separator+"deps-output.txt -DoutputAbsoluteArtifactFilename=true");
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
		ArrayList<Object> countsAndClasses = getDatafromJar(dependency, child, dependencyName);
		libsToCountsAndClasses.putIfAbsent(dependencyName, new ArrayList<Object>(Arrays.asList((Integer)countsAndClasses.get(0), 
				(Integer)countsAndClasses.get(1), (String)countsAndClasses.get(2))));
	}
	
	private static ArrayList<Object> getDatafromJar(File dependency, URLClassLoader child, String dependencyName) {
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
					} catch (Error e) {
						System.out.println("Error while parsing jar " + e);
					}
				} else if (Pattern.matches("META-INF"+File.separator+"services"+File.separator+".+", crunchifyJar.getName())) {
					String key = crunchifyJar.getName().replace("META-INF"+File.separator+"services"+File.separator, "");
					BufferedReader reader = new BufferedReader(new InputStreamReader(new JarFile(dependency).getInputStream(crunchifyJar)));
					String row = "";
					while ((row = reader.readLine()) != null) {
						if (!row.trim().startsWith("#"))
							break;
					}
						
					reader.close();
					servicesInfo.putIfAbsent(key, new HashSet<String>());
					if (!servicesInfo.get(key).contains(row+","+dependencyName))
						servicesInfo.get(key).add(row+","+dependencyName);
				}
			}
			crunchifyJarFile.close();
		} catch (Exception e) {
			System.out.println(e);
		}

		return new ArrayList<Object>(Arrays.asList(count, 0, String.join(":", classNames)));
	}

	private static void deleteDirectory(File fileOrDirectory) {
		if (fileOrDirectory.isDirectory())
			for (File child : fileOrDirectory.listFiles())
				deleteDirectory(child);

		fileOrDirectory.delete();
	}
}