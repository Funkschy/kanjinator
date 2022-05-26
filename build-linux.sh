#!/bin/sh

output_dir="target/uberjar"
jar_name="kanjinator-0.2.2-standalone.jar"

echo "-> building application"
lein clean
lein uberjar

cp "$output_dir/$jar_name" target/kanjinator-linux.jar

# remove windows/mac stuff from jar
zip -d "target/kanjinator-linux.jar" nu/pattern/opencv/windows/\*
zip -d "target/kanjinator-linux.jar" nu/pattern/opencv/osx/\*
zip -d "target/kanjinator-linux.jar" win32-x86-64/\*
zip -d "target/kanjinator-linux.jar" win32-x86/\*
