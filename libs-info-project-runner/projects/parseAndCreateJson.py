#iterate through each folder
#if pom.xml is there in directory, then return a (build,filename.jar)
#if pom.xml is not there in directory, then get all directories in the folder, and recurse one step deeper
# and check if pom.xml is there and return (build,filename.jar)
# If its gradle don't do anything for now
# 
import os
import xml.etree.ElementTree as ET
import json


def seeDirectory(dName):
    res = []
    for dirName in os.listdir(dName):
        dirName = os.path.join(dName,dirName)
        if not os.path.isfile(dirName):
            isMavenSingleModuleDir = os.path.exists(os.path.join(dirName, 'pom.xml'))
            if isMavenSingleModuleDir:
                toReturn = dict()
                toReturn['execDir'] = dirName.split("/")[-1]
                toReturn['generatedJarName'], toReturn['build'] = getJarName(os.path.join(dirName, 'pom.xml')),'maven'
                toReturn['rootDir'] = ""
                toReturn['javaVersion'] = 8
                res.append(toReturn)
            else:
                # handle the gradle stuff and multimodules here
                if (os.path.exists(dirName)):
                    for file in os.listdir(dirName):
                        if not os.path.isfile(os.path.join(dirName,file)):
                            #if its only directory
                            res.extend(seeDirectory(os.path.join(dirName,file)))

    return res


def getJarName(pomFile):
    tree = ET.parse(pomFile)
    ET.register_namespace("", "http://maven.apache.org/POM/4.0.0")
    root = tree.getroot()

    artifactId = root.find('artifactId')
    if not artifactId:
        artifactId = root.findall('.//{http://maven.apache.org/POM/4.0.0}artifactId')
    version = root.find('version')
    if not version:
        version = root.findall('.//{http://maven.apache.org/POM/4.0.0}version')

    buildElems = root.findall('build')
    for buildElem in buildElems:
        finalName = buildElem.find('finalName')
        if finalName is not None: 
          return finalName.text
    if artifactId and version:
        artifactId = artifactId[0]
        version = version[0]
        return artifactId.text + '-'+ version.text
    return ''


def createJson(projectListDict):
    #will keep getting rewritten, make it 'a' if need it to be appended
    fileHandle = open('projects-list.json','w+')
    fileHandle.write(json.dumps(projectListDict,indent=4))
    fileHandle.write('\n')
    fileHandle.close()

createJson(seeDirectory('.'))
