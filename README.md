# calls-across-libs
This project studies the usage and surfaces of Java components by instrumenting them with Java agents.

**calls-across-libs/libs-info-agent** : Java agent that performs the analysis

**calls-across-libs/libs-info-project-runner** : Runs unit tests of Java components and instruments them using the agent

### Usage
These commands run the tool on the Java components listed in calls-across-libs/libs-info-project-runner/projects/projects-list.json

###### 1. cd /calls-across-libs/docker

###### 2. docker build -t *image-name* .

###### 3. docker run -v /path/to/this/repo/calls-across-libs:/calls-across-libs *image-name*      &#35; writes data to this repo's path
OR
###### 3. docker run -it *image-name*       &#35; writes data to the default Docker location

This should generate the data in /calls-across-libs/libs-info-project-runner/api-surface-data. Use

###### docker run -it *image-name* sh

to view the data if you used the second command.
