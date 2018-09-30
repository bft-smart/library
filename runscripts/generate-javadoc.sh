rm -r doc/*
javadoc -cp src -d doc bftsmart.tom bftsmart.tom.server src/bftsmart/tom/util/KeyLoader.java src/bftsmart/tom/util/Extractor.java src/bftsmart/tom/server/defaultservices/DefaultRecoverable.java src/bftsmart/tom/server/defaultservices/DefaultSingleRecoverable.java src/bftsmart/tom/server/defaultservices/durability/DurabilityCoordinator.java src/bftsmart/reconfiguration/VMServices.java
