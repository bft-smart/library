#!/bin/bash 


> hosts.config

IP=100
for replica in {0..101};
    do
        echo $replica" 10.1.1."$IP" 12000 12001" >> hosts.config
        ((IP++))
    done




