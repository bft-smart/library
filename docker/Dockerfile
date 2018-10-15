FROM ubuntu:latest
#FROM tulioribeiro/bft-smart:v1_f1

# Set the working directory to /opt
WORKDIR /opt

ADD ./BFT-SMaRt.jar /opt/
ADD ./config /opt/config/
ADD ./lib /opt/lib/

#ADD ./startReplicaClient.sh /opt/

ADD ./provision.sh /opt/
RUN ["bash","/opt/provision.sh"]

RUN echo "#!/bin/bash" > startRC.sh
RUN echo "java -Dlogback.configurationFile=config/logback.xml -DNODE_ID=\$1 -cp /opt/BFT-SMaRt.jar:/opt/lib/* \$@" >> startRC.sh

RUN chmod +x ./startRC.sh

ENTRYPOINT ["./startRC.sh"]
CMD [""]