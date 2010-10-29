cd ..
start runscripts\smartrun.bat navigators.smart.tom.demo.ThroughputTestServer 0 1000
ping 127.0.0.1
start runscripts\smartrun.bat navigators.smart.tom.demo.ThroughputTestServer 1 1000
ping 127.0.0.1
start runscripts\smartrun.bat navigators.smart.tom.demo.ThroughputTestServer 2 1000
ping 127.0.0.1
start runscripts\smartrun.bat navigators.smart.tom.demo.ThroughputTestServer 3 1000
ping 127.0.0.1
ping 127.0.0.1
ping 127.0.0.1
start runscripts\smartrun.bat navigators.smart.tom.demo.ThroughputTestClient 1001 20000 0 1 20