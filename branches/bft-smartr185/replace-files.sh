#!/bin/bash

LIST_DIR=(`grep -l -R "http://bft-smart.googlecode.com" src/* | grep entries`)


for((i=0; i < ${#LIST_DIR[@]} ; i++))
do
	replace com br < ${LIST_DIR[i]} > tmpfile | mv tmpfile ${LIST_DIR[i]}
done
