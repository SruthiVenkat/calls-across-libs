FROM ubuntu:21.04

# prepare , install some utils
RUN apt-get --yes --force-yes update && \
apt-get --yes --force-yes install software-properties-common && \
add-apt-repository ppa:linuxuprising/java && \
apt-get --yes --force-yes update && \
apt install unzip && \
apt install zip

# install Java
RUN echo oracle-java16-installer shared/accepted-oracle-license-v1-2 select true | /usr/bin/debconf-set-selections && \
apt-get -y install oracle-java16-installer && \
apt install oracle-java16-set-default

# install git and build tools
RUN apt-get --yes --force-yes install maven && \
apt-get --yes --force-yes install gradle
CMD echo 'Maven version'
CMD mvn --version
CMD echo 'Gradle version'
CMD gradle --version
RUN apt-get --yes --force-yes  install git
CMD echo 'Git version'
CMD git --version


# clone and checkout calls-across-libs
RUN git clone https://github.com/SruthiVenkat/calls-across-libs.git && cd calls-across-libs && mvn clean install

CMD cd calls-across-libs/libs-info-project-runner && mvn compile && mvn exec:java -Dexec.mainClass=runner.DependentTestRunner > output.txt
