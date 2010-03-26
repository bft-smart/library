cd ..
start runscripts\smartrun_to_logs.bat navigators.smart.tom.demo.RandomServer 0 
ping 127.0.0.1
start runscripts\smartrun_to_logs.bat navigators.smart.tom.demo.RandomServer 1
ping 127.0.0.1
start runscripts\smartrun_to_logs.bat navigators.smart.tom.demo.RandomServer 2
ping 127.0.0.1
start runscripts\smartrun_to_logs.bat navigators.smart.tom.demo.RandomServer 3
ping 127.0.0.1
ping 127.0.0.1
ping 127.0.0.1
start runscripts\smartrun_to_logs.bat navigators.smart.tom.demo.RandomClient 1001 456445
start runscripts\smartrun_to_logs.bat navigators.smart.tom.demo.RandomClient 5001 465566
start runscripts\smartrun_to_logs.bat navigators.smart.tom.demo.RandomClient 6001 987777
start runscripts\smartrun_to_logs.bat navigators.smart.tom.demo.RandomClient 7001 123322