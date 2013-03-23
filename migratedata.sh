#!/bin/sh
#
#  Copyright 2013 the Trustees of Princeton University
#  Author:  Mark Ratliff
#
#  This script should be used to run the DataMigrator application
#

#
#  The JAR file containing the application and the modifications to 
#   dependent library files must come first in the class path
#

java_libs="target/cloudmigration-0.0.1.jar:./config"

#
# Now add all of the dependent libraries
#

for lib in `ls target/lib/*.jar`
do
  java_libs="$java_libs:$lib"
done

#echo $java_libs

#
#  Run the application
#

java -classpath $java_libs edu.princeton.cloudmigration.DataMigrator
