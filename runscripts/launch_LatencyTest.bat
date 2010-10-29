cd ..
start runscripts\smartrun.bat navigators.smart.tom.demo.LatencyTestServer 0 
ping 127.0.0.1
start runscripts\smartrun.bat navigators.smart.tom.demo.LatencyTestServer 1
ping 127.0.0.1
start runscripts\smartrun.bat navigators.smart.tom.demo.LatencyTestServer 2
ping 127.0.0.1
start runscripts\smartrun.bat navigators.smart.tom.demo.LatencyTestServer 3
ping 127.0.0.1
ping 127.0.0.1
ping 127.0.0.1
start runscripts\smartrun.bat navigators.smart.tom.demo.LatencyTestClient 1001 10000 0 1