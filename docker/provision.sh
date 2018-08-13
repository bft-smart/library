#!/bin/bash


BASE_DIR="/opt"


# Generic provisioning actions
#------------------------------------------------------------------------------
#apt-get update && apt-get install -y git mc openjdk-8-jdk net-tools

apt-get update 
apt-get install -y git 
apt-get install -y mc
apt-get install -y openjdk-8-jdk-headless
apt-get install -y net-tools
apt-get install -y iputils-ping
    
#cd /opt
#git clone https://github.com/tulioalberton/BFT-SMaRt.git

#git --git-dir=$BASE_DIR/BFT-SMaRt/.git --work-tree=$BASE_DIR/BFT-SMaRt checkout -b master


    
# This step is required to run jobs with any user
#------------------------------------------------------------------------------
chmod 777 -R $BASE_DIR
