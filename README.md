completionServer
================

A thrift HTTP JSON completion server based on servlets 

# Dependencies
To run the code you have to have completionProcy installed:
```
git clone git@github.com:Completionary/completionProxy.git
cd completionProxy/thrift
make all lang=java
cd ..
mvn install
```

# Running
The easiest way would be to run jetty:
```
git clone git@github.com:Completionary/completionServer.git
mvn jetty:run
```
