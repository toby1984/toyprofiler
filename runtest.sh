#!/bin/bash
mvn clean package
# java -server -XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining -javaagent:profiler/target/toyprofiler.jar=include=de.codesourcery.sampleapp.*,file=profile.xml -cp sampleapp/target/classes:profiler/target/toyprofiler.jar de.codesourcery.sampleapp.TestApplication
java -javaagent:profiler/target/toyprofiler.jar=print=true,include=de.codesourcery.sampleapp*,exclude=*Lambda*,file=profile.xml,mode=request -cp sampleapp/target/classes:profiler/target/toyprofiler.jar de.codesourcery.sampleapp.TestApplication

# java -jar profiler/target/toyprofiler.jar profile.xml
