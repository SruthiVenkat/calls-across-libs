# calls-across-libs
This project studies the usage and surfaces of Java components by instrumenting them with Java agents.

**calls-across-libs/libs-info-agent** : Java agent that performs the analysis

**calls-across-libs/libs-info-project-runner** : Runs unit tests of Java components and instruments them using the agent

### Usage
These commands run the tool on the Java components listed in calls-across-libs/libs-info-project-runner/projects/projects-list.json
#####cd /calls-across-libs/docker

#####sudo docker build -t <image-name> .

#####sudo docker run -it <image-name>

This should generate the data in /calls-across-libs/libs-info-project-runner/api-surface-data. Use

#####sudo docker run -it <image-name> sh

to view the data.