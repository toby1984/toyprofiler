# toyprofiler

This is a JVM profiler that gathers method execution times by bytecode instrumentation and writes the profiling results to an XML file when the program terminates.

It comes with a (more-or-less) fully-featured viewer application that renders the profiling data as flame graphs and allows interactive exploration of call flows.

![Image](https://raw.githubusercontent.com/toby1984/toyprofiler/master/screenshot1.png)

It also provides a visual comparison mode that compares profiling results from two different results, highlighting the method invocations that were either longer or shorter than the reference:

![Image](https://raw.githubusercontent.com/toby1984/toyprofiler/master/screenshot2.png)

# Viewer features

* ...did I mention flame graphs ? :)
* Customizable color schemes
* Supports loading profiling results from different runs for easy comparison (see History menu item) 
* Visually compare profiling results from different runs (History -> Compare)
* Zoom into a specific method by left double-click
* Zoom out to the parent stackframe by right-click
* Switch between the results from multiple profiling runs using cursor-left / cursor-right
* Easy to use , most commands have keyboard shortcuts

# Profiler features

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
