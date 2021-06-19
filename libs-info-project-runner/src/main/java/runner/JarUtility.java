package runner;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class JarUtility {
	public static List<String> addedLibs = new ArrayList<String>();
	public static Map<String, ArrayList<Object>> libsToCountsAndClasses = new HashMap<String, ArrayList<Object>>();
	public static String configPath = Paths.get(new File(".").getAbsolutePath()).getParent().getParent().toString()+"/src/main/resources/config.properties";
	
	public static void initLibsToCountsAndClasses(String build) {
		libsToCountsAndClasses.clear();
		// get wars and jars for projects, initialize counts and packages
		populateAddedLibs();
		JSONParser jsonParser = new JSONParser();
        try (FileReader reader = new FileReader(new File(".").getAbsolutePath()+File.separator+"projects"+File.separator+"projects-list.json"))
        {
            Object obj = jsonParser.parse(reader);
            JSONArray projects = (JSONArray) obj;
            Iterator<JSONObject> iterator = projects.iterator();
            while (iterator.hasNext()) {
            	JSONObject projectObject = (JSONObject)iterator.next();
            	if (!addedLibs.contains(projectObject.get("libName")) && projectObject.get("build").equals(build)) {
            		String pathToWarJar="", jarName="";
            		if (build.equals("maven")) {
            			pathToWarJar = File.separator+"target"+File.separator+projectObject.get("generatedWarJarName"); jarName = "-classes.jar";
            		}
            		else if (build.equals("gradle")) {
            			pathToWarJar = File.separator+"build/libs"+File.separator+projectObject.get("generatedWarJarName"); jarName = ".jar";
            		}
	            	String generatedWarJarName = new File(".").getAbsolutePath()+File.separator+"projects"+File.separator+projectObject.get("folderName")+pathToWarJar;
	            	String tmpFolder = new File(".").getAbsolutePath()+File.separator+"projects"+File.separator+projectObject.get("folderName")+File.separator+"tmp";
	            	String libName = (String)projectObject.get("libName");
	            	ArrayList<Object> countsAndClasses = getPublicProtectedMethodsCountAndClasses(generatedWarJarName+".war", generatedWarJarName+jarName, tmpFolder);
	            	libsToCountsAndClasses.putIfAbsent(libName, new ArrayList<Object>(Arrays.asList(0, 0, "")));
	            	ArrayList<Object> libVals = libsToCountsAndClasses.get(libName);
	            	libVals.set(0, (Integer)libVals.get(0) + (Integer)countsAndClasses.get(0));
	            	libVals.set(1, (Integer)libVals.get(1) + (Integer)countsAndClasses.get(1));
	            	libVals.set(2, ((String)libVals.get(2)).concat((String)countsAndClasses.get(2)));
            	}
            }
        } catch (Exception e) {
			System.out.println("Error while reading file with project list" + e.toString());		
		}
        addToLibsInfo(libsToCountsAndClasses);
	}
	
	public static void populateAddedLibs() {
		try (FileReader input = new FileReader(configPath)) {
            Properties prop = new Properties();
            prop.load(input);
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
	
	public static void addToLibsInfo(Map<String, ArrayList<Object>> libsToCountsAndClasses) {
		try (FileReader input = new FileReader(configPath))
		{
		    Properties prop = new Properties();
			prop.load(input); 
			String libsInfoPath = prop.getProperty("libsInfoPath");
			FileWriter writer = new FileWriter(libsInfoPath, true);	
			if (new File(libsInfoPath).length() == 0) {
				writer.write("Library Name\tNo. of Public/Protected Methods\tNo. of Methods Called By Tests\tClasses\n");
				writer.write("unknownLib\t0\t0\t \n");
			}
			
			for (String lib: libsToCountsAndClasses.keySet()) {
				writer.write(lib+"\t"+libsToCountsAndClasses.get(lib).get(0)+"\t"+libsToCountsAndClasses.get(lib).get(1)+"\t"+libsToCountsAndClasses.get(lib).get(2)+"\n");
		    }
			writer.flush();
			writer.close();
		} catch (IOException ex) {
		    ex.printStackTrace();
		}
	}
	
	public static ArrayList<Object> getPublicProtectedMethodsCountAndClasses(String warFile, String crunchifyJarName, String tmpFolder) {
		int count = 0;
		List<String> classNames = new ArrayList<>();
		File destDir = new File("");

		try {
			JarInputStream crunchifyJarFile = new JarInputStream(new FileInputStream(crunchifyJarName));
			JarEntry crunchifyJar;
			
			try {
				unzip(warFile, tmpFolder);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

			List<URL> classLoaderURLs = new ArrayList<>();
			classLoaderURLs.add(new File(crunchifyJarName).toURI().toURL());
			File dir = new File(tmpFolder+File.separator+"WEB-INF"+File.separator+"lib");
			File[] directoryListing = dir.listFiles();
			if (directoryListing != null) {
				for (File child : directoryListing) {
					classLoaderURLs.add(new File(child.getAbsolutePath()).toURI().toURL());
				}
			}

			URL[] classLoaderURLsAsArray = new URL[classLoaderURLs.size()];
			URLClassLoader child = new URLClassLoader(classLoaderURLs.toArray(classLoaderURLsAsArray),
					JarUtility.class.getClass().getClassLoader());

			if (directoryListing != null) {
				for (File dependency : directoryListing) {
					addDependencyInWarToLibsInfo(dependency, child);
				}
			}

			destDir = new File(tmpFolder);

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
		} catch (Exception e) {
			System.out.println("Error while parsing jar" + e.toString());
		}
		deleteDirectory(destDir);
		if (classNames.isEmpty()) classNames.add("");
		return new ArrayList<Object>(Arrays.asList(count, 0, String.join(":", classNames)));
	}
	
	public static void addDependencyInWarToLibsInfo(File dependency, URLClassLoader child) {
		System.out.println(dependency.getName()); // TODO add war jars to libs-info
		if (!dependency.getName().endsWith(".jar"))
			return;
		String dependencyName = dependency.getName().substring(0, dependency.getName().indexOf(".jar"));
		if (libsToCountsAndClasses.containsKey(dependencyName))
			return;
		int count = 0;
		List<String> classNames = new ArrayList<>();
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
						System.out.println("No class def found "+ e);
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

	public static void unzip(String zipFilePath, String destDirectory) throws IOException, FileNotFoundException {
		File destDir = new File(destDirectory);
		if (!destDir.exists()) {
			destDir.mkdir();
		}
		JarInputStream zipIn = new JarInputStream(new FileInputStream(zipFilePath));
		ZipEntry entry = zipIn.getNextEntry();
		// iterates over entries in the zip file
		while (entry != null) {
			String filePath = destDirectory + File.separator + entry.getName();
			if (!entry.isDirectory()) {
				// if the entry is a file, extract it
				extractFile(zipIn, filePath);
			} else {
				// if the entry is a directory, make the directory
				File dir = new File(filePath);
				dir.mkdirs();
			}
			zipIn.closeEntry();
			entry = zipIn.getNextEntry();
		}
		zipIn.close();
	}

	private static void extractFile(JarInputStream zipIn, String filePath) throws IOException, FileNotFoundException {
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
		byte[] bytesIn = new byte[4096];
		int read = 0;
		while ((read = zipIn.read(bytesIn)) != -1) {
			bos.write(bytesIn, 0, read);
		}
		bos.close();
	}

	private static void deleteDirectory(File fileOrDirectory) {
		if (fileOrDirectory.isDirectory())
			for (File child : fileOrDirectory.listFiles())
				deleteDirectory(child);

		fileOrDirectory.delete();
	}
}