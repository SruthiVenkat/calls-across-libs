FROM ubuntu:21.04

# prepare , install some utils
RUN apt-get --yes --force-yes update && \
apt-get --yes --force-yes install software-properties-common && \
add-apt-repository ppa:linuxuprising/java && \
apt-get --yes --force-yes update && \
apt install unzip && \
apt install zip && \
apt-get --yes --force-yes install jq

# install Java 11 and 8
RUN apt-get update && apt-get install -y openjdk-11-jdk && apt-get install -y openjdk-8-jdk

# install git and build tools
RUN apt-get --yes --force-yes install maven && \
apt-get --yes --force-yes install gradle
RUN apt-get --yes --force-yes  install git

# set up Maven local repo
RUN mkdir /root/.m2 && mkdir /root/.m2/repository && echo \
    "<settings xmlns='http://maven.apache.org/SETTINGS/1.0.0\' \
    xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' \
    xsi:schemaLocation='http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd'> \
        <localRepository>/root/.m2/repository</localRepository> \
        <interactiveMode>true</interactiveMode> \
        <usePluginRegistry>false</usePluginRegistry> \
        <offline>false</offline> \
    </settings>" \
    > /root/.m2/settings.xml

# clone and checkout calls-across-libs
RUN git clone https://github.com/SruthiVenkat/calls-across-libs.git && cd calls-across-libs && mvn clean install && cd libs-info-project-runner && sh getrepos.sh

# tweaks for some benchmarks
RUN cd /calls-across-libs/libs-info-project-runner && sh tweak.sh && apt-get install -y npm

# tweak for fastjson benchmark which needs UTF-8 because it has Chinese characters
RUN apt-get -y install locales && sed -i '/en_US.UTF-8/s/^# //g' /etc/locale.gen && \
    locale-gen
ENV LANG en_US.UTF-8  
ENV LANGUAGE en_US:en  
ENV LC_ALL en_US.UTF-8

ENV JAVA_OPTS="-Xms1024M -Xmx1024M"

CMD cd /calls-across-libs/libs-info-project-runner && mvn compile && mvn exec:java -Dexec.mainClass=runner.DependentTestRunner > output.txt