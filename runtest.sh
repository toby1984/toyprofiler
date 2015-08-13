#!/bin/bash
mvn clean package
# java -server -XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining -javaagent:profiler/target/toyprofiler.jar=pattern=de.codesourcery.sampleapp.*,file=profile.xml -cp sampleapp/target/classes:profiler/target/toyprofiler.jar de.codesourcery.sampleapp.TestApplication
java -javaagent:profiler/target/toyprofiler.jar=pattern=de.codesourcery.sampleapp.*,file=profile.xml -cp sampleapp/target/classes:profiler/target/toyprofiler.jar de.codesourcery.sampleapp.TestApplication
