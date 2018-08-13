FROM ubuntu:latest
#FROM bft-smart:v-4_f-10

# Set the working directory to /opt
WORKDIR /opt

ADD ./BFT-SMaRt.jar /opt/
ADD ./config /opt/config/
ADD ./lib /opt/lib/
ADD ./startCluster.sh /opt/
ADD ./config/generateIpPort.sh /opt/config/

ADD ./provision.sh /opt/
RUN ["bash","/opt/provision.sh"]

RUN ["ls","/opt"]

