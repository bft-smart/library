cd ..
start runscripts\smartrun.bat navigators.smart.tom.demo.CounterServer 0 
ping 127.0.0.1
start runscripts\smartrun.bat navigators.smart.tom.demo.CounterServer 1
ping 127.0.0.1
start runscripts\smartrun.bat navigators.smart.tom.demo.CounterServer 2
ping 127.0.0.1
start runscripts\smartrun.bat navigators.smart.tom.demo.CounterServer 3
ping 127.0.0.1
ping 127.0.0.1
ping 127.0.0.1
start runscripts\smartrun.bat navigators.smart.tom.demo.CounterClient 1001 5
start runscripts\smartrun.bat navigators.smart.tom.demo.CounterClient 5001 7
start runscripts\smartrun.bat navigators.smart.tom.demo.CounterClient 6001 9 
start runscripts\smartrun.bat navigators.smart.tom.demo.CounterClient 7001 3 