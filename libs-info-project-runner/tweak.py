import xml.etree.ElementTree as ET

# checkstyle
tree = ET.parse('./projects/checkstyle/pom.xml')
ET.register_namespace("", "http://maven.apache.org/POM/4.0.0")
root = tree.getroot()
build = next((x for x in root if x.tag=="{http://maven.apache.org/POM/4.0.0}build"), None)
plugins = next((x for x in build if x.tag=="{http://maven.apache.org/POM/4.0.0}plugins"), None)
for x in plugins:
	for y in x:
		if y.text=="maven-surefire-plugin":
			for z in x:
				if z.tag=="{http://maven.apache.org/POM/4.0.0}configuration":
					for a in z:
						if a.tag=="{http://maven.apache.org/POM/4.0.0}argLine":
							a.text = a.text + " -javaagent:/calls-across-libs/libs-info-agent/target/libs-info-agent-1.0-SNAPSHOT.jar -Xbootclasspath/a:/root/.m2/repository/org/javassist/javassist/3.27.0-GA/javassist-3.27.0-GA.jar:/calls-across-libs/libs-info-agent/target/libs-info-agent-1.0-SNAPSHOT.jar"
tree.write('./projects/checkstyle/pom.xml')


# flink
tree = ET.parse('./projects/flink/pom.xml')
ET.register_namespace("", "http://maven.apache.org/POM/4.0.0")
root = tree.getroot()
build = next((x for x in root if x.tag=="{http://maven.apache.org/POM/4.0.0}build"), None)
plugins = next((x for x in build if x.tag=="{http://maven.apache.org/POM/4.0.0}plugins"), None)
for x in plugins:
	for y in x:
		if y.text=="maven-surefire-plugin":
			for z in x:
				if z.tag=="{http://maven.apache.org/POM/4.0.0}configuration":
					for a in z:
						if a.tag=="{http://maven.apache.org/POM/4.0.0}argLine":
							a.text = a.text + " -javaagent:/calls-across-libs/libs-info-agent/target/libs-info-agent-1.0-SNAPSHOT.jar -Xbootclasspath/a:/root/.m2/repository/org/javassist/javassist/3.27.0-GA/javassist-3.27.0-GA.jar:/calls-across-libs/libs-info-agent/target/libs-info-agent-1.0-SNAPSHOT.jar"
tree.write('./projects/flink/pom.xml')