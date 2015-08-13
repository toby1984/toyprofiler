# toyprofiler

This is a little toy profiler that gathers method execution times by bytecode instrumentation and generates an XML file containing the execution statistics when the program terminates. It also comes with a toy JavaFX viewer application to visualize the profiling results.

![Image](https://raw.githubusercontent.com/toby1984/toyprofiler/master/screenshot.png)

This is a Java-only JVM agent (=no native code) that - depending on which parts of your application you instrument - may introduce a *massive* slowdown.

__Use at your own risk__ *I wrote this in a few hours and especially the bytecode generation is really crude and might break your code* ... __if you're using this on production code, you're crazy.__

# Requirements

* JDK >= 1.8
* Maven 3.x

# Building

Running

    mvn clean package

on the top-level project will generate a JAR in profiler/target/toyprofiler.jar
The JAR contains both the JVM agent and the viewer application (the jar is self-executable , java -jar toyprofiler.jar will run the viewer).

# Example profiler usage

The following line will instrument all classes within the 'my.package' package and write the profiling statistics to profile.xml once the application terminates

java -javaagent:profiler/target/toyprofiler.jar=include=my.package.*,file=profile.xml  my.package.Main

Available agent parameters:

 * file = XML file to write profiling data to
 * include = Comma-separated list of fully-qualified classnames or package names ending with a '*' (to perform prefix matching)
 * exclude = Comma-separated list of fully-qualified classnames or package names ending with a '*' (to perform prefix matching)
 * debug = true,false (enable debug output)
 * print = true,false (dump profiling statistics as ascii art after the program terminates)

# Viewing the results

java -jar profiler/target/toyprofiler.jar profile.xml
