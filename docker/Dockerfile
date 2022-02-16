FROM ubuntu:21.04

# prepare , install some utils
RUN apt-get --yes --force-yes update && \
apt-get --yes --force-yes install software-properties-common && \
add-apt-repository ppa:linuxuprising/java && \
apt-get --yes --force-yes update && \
apt install unzip && \
apt install zip && \
apt-get --yes --force-yes install jq && \

# install Java 11 and 8, set to Java 8
apt-get update && apt-get install -y openjdk-11-jdk && apt-get install -y openjdk-8-jdk && \
update-alternatives --set java /usr/lib/jvm/java-8-openjdk-arm64/jre/bin/java && \

# install git and build tools
apt-get --yes --force-yes install maven && \
apt-get --yes --force-yes install gradle && \
apt-get --yes --force-yes install git && \
apt-get install -y npm && \
apt-get -y install locales && \

# set up Maven local repo
mkdir /root/.m2 && mkdir /root/.m2/repository && echo \
    "<settings xmlns='http://maven.apache.org/SETTINGS/1.0.0\' \
    xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' \
    xsi:schemaLocation='http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd'> \
        <localRepository>/root/.m2/repository</localRepository> \
        <interactiveMode>true</interactiveMode> \
        <usePluginRegistry>false</usePluginRegistry> \
        <offline>false</offline> \
    </settings>" \
    > /root/.m2/settings.xml && \

# clone and checkout calls-across-libs
git clone https://SruthiVenkat:@github.com/SruthiVenkat/calls-across-libs.git && cd calls-across-libs && mvn clean install

ENV LANG en_US.UTF-8  
ENV LANGUAGE en_US:en  
ENV LC_ALL en_US.UTF-8
ENV JAVA_OPTS="-Xms4096M -Xmx4096M"

CMD cd /calls-across-libs/libs-info-project-runner && sh getrepos.sh && \

cd /calls-across-libs/libs-info-project-runner/projects && chmod -R 777 . && \

# clearing api-surface-data, new data goes there
cd /calls-across-libs/libs-info-project-runner && rm -rf api-surface-data && \

# tweaks for some benchmarks
cd /calls-across-libs/libs-info-project-runner && sh tweak.sh && \

# tweak for fastjson benchmark which needs UTF-8 because it has Chinese characters
sed -i '/en_US.UTF-8/s/^# //g' /etc/locale.gen && locale-gen && \

cd /calls-across-libs/libs-info-project-runner && mvn compile && mvn exec:java -Dexec.mainClass=runner.DependentTestRunner > output.txt