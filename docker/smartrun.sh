#!/bin/bash

java -cp /opt/BFT-SMaRt.jar:/opt/lib/* "$@"
java -DNODE_ID=$1 -cp /opt/BFT-SMaRt.jar:/opt/lib/* bft.BFTNode $@
