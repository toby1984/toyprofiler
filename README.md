# toyprofiler

This is a little toy profiler that gathers method execution times by bytecode instrumentation and generates an XML file containing the execution statistics when the program terminates. It also comes with a toy JavaFX viewer application to visualize the profiling results.

![Image](https://raw.githubusercontent.com/toby1984/toyprofiler/master/screenshot.png)

This is a Java-only JVM agent (=no native code) that - depending which parts of your application you instrument - may introduce a *massive* slowdown.

# Requirements

- JDK >= 1.8
- Maven 3.x

# Building

Running

mvn clean package

on the top-level project will generate a self-executable JAR in profiler/target/toyprofiler.jar

# Example profiler usage

The following line will instrument all classes within the 'my.package' package and write the profiling statistics to profile.xml once the application terminates

java -javaagent:profiler/target/toyprofiler.jar=include=my.package.*,file=profile.xml  my.package.Main

Additional command-line options:

 exclude = Comma-separated list of fully-qualified classnames (or package names ending with a '*' to perform prefix matching)
 debug = true,false (enable debug output)
 print = true,false (dump profiling statistics as ascii art after the program terminates)

# Viewing the results

java -jar profiler/target/toyprofiler.jar profile.xml
