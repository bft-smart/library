#!/bin/bash

java -Dlog4j.configurationFile=/opt/config/logback.xml -DNODE_ID=\$1 -cp /opt/BFT-SMaRt.jar:/opt/lib/* \$@


