#!/bin/bash


BASE_DIR="/opt"


# Generic provisioning actions
#------------------------------------------------------------------------------
#apt-get update && apt-get install -y git mc openjdk-8-jdk net-tools

apt-get update 
apt-get install -y software-properties-common
apt-get install -y git 
apt-get install -y mc


#apt-get install software-properties-common
#add-apt-repository ppa:webupd8team/java
#apt-get update
#apt-get install oracle-java8-installer
#apt-get install oracle-java8-set-default

#apt-get update 
#apt-get install -y openjdk-8-jdk-headless
#apt-get install oracle-java8-installer
#apt-get install oracle-java8-set-default

apt-get install -y net-tools
apt-get install -y iputils-ping
        
# This step is required to run jobs with any user
#------------------------------------------------------------------------------
chmod 777 -R $BASE_DIR
