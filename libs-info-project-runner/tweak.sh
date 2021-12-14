# rocketmq
cd /calls-across-libs/libs-info-project-runner/projects/rocketmq 

echo "org.apache.rocketmq" > ./acl/group.txt
echo "org.apache.rocketmq" > ./broker/group.txt
echo "org.apache.rocketmq" > ./client/group.txt
echo "org.apache.rocketmq" > ./common/group.txt
echo "org.apache.rocketmq" > ./remoting/group.txt
echo "org.apache.rocketmq" > ./tools/group.txt
echo "4.9.1" > ./acl/version.txt
echo "4.9.1" > ./broker/version.txt
echo "4.9.1" > ./client/version.txt
echo "4.9.1" > ./common/version.txt
echo "4.9.1" > ./remoting/version.txt
echo "4.9.1" > ./tools/version.txt
echo "rocketmq-acl" > ./acl/artifact.txt
echo "rocketmq-broker" > ./broker/artifact.txt
echo "rocketmq-client" > ./client/artifact.txt
echo "rocketmq-common" > ./common/artifact.txt
echo "rocketmq-remoting" > ./remoting/artifact.txt
echo "rocketmq-tools" > ./tools/artifact.txt

# hygieia-core
cd /calls-across-libs/libs-info-project-runner/projects/hygieia-core 
echo "com.capitalone.dashboard" > ./group.txt
echo "3.6.9-SNAPSHOT" > ./version.txt
echo "core" > ./artifact.txt

cd /calls-across-libs/libs-info-project-runner
python3 tweak.py

# checkstyle
cd /calls-across-libs/libs-info-project-runner/projects/checkstyle && mvn tidy:pom