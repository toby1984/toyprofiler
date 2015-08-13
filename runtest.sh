#!/bin/bash
mvn clean package
java -javaagent:profiler/target/toyprofiler.jar=pattern=de.codesourcery.sampleapp.*,file=/home/tobi/tmp/profile.xml -cp sampleapp/target/classes:profiler/target/toyprofiler.jar de.codesourcery.sampleapp.TestApplication
