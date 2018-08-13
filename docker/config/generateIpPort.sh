#!/bin/bash 


> hosts.config

IP=100
for replica in {0..3};
    do
        echo $replica" 10.1.1."$IP" 12000" >> hosts.config
        ((IP++))
    done




