package runner;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import db.DatabaseConnector;

public class JarUtility {
	
	public static void initLibsToCountsAndClasses(DatabaseConnector connector) {
		Map<String, ArrayList<Object>> libsToCountsAndClasses = new HashMap<String, ArrayList<Object>>();
		libsToCountsAndClasses.put("unknownLib", new ArrayList<Object>(Arrays.asList(0, "")));
		// get wars and jars for projects, initialize counts and packages
		JSONParser jsonParser = new JSONParser();
        try (FileReader reader = new FileReader(new File(".").getAbsolutePath()+File.separator
				+"projects"+File.separator+"projects-list.json"))
        {
            Object obj = jsonParser.parse(reader);
            JSONArray projects = (JSONArray) obj;
            Iterator<JSONObject> iterator = projects.iterator();
            while (iterator.hasNext()) {
            	JSONObject projectObject = (JSONObject)iterator.next();
            	String generatedWarJarName = new File(".").getAbsolutePath()+File.separator
        				+"projects"+File.separator+projectObject.get("folderName")+File.separator
        				+"target"+File.separator+projectObject.get("generatedWarJarName");
            	String tmpFolder = new File(".").getAbsolutePath()+File.separator
        				+"projects"+File.separator+projectObject.get("folderName")
        				+File.separator+"tmp";
            	libsToCountsAndClasses.put((String)projectObject.get("libName"), 
            			getPublicProtectedMethodsCountAndClasses(generatedWarJarName+".war", 
            					generatedWarJarName+".jar", tmpFolder));

            }
        } catch (Exception e) {
			System.out.println("Error while reading file with project list" + e.toString());		
		}

		connector.addToLibsInfoTable(libsToCountsAndClasses);
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
		return new ArrayList<Object>(Arrays.asList(count, String.join(":", classNames)));
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
				// if the entry is a file, extracts it
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