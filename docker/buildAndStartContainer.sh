#!/bin/bash 

docker build -t bft-smart .

docker tag bft-smart tulioribeiro/bft-smart:$@

docker push tulioribeiro/bft-smart:$@

docker run -it tulioribeiro/bft-smart:$@ /bin/bash



