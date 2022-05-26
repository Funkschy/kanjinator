@echo off

set output_dir="target/uberjar"
set jar_name="kanjinator-0.2.2-SNAPSHOT-standalone.jar"

echo "-> building application"
call lein clean
call lein uberjar

echo "-> packaging application"
call jpackage ^
    -i "%output_dir%" ^
    --type "msi" ^
    --win-dir-chooser ^
    --win-shortcut ^
    --win-menu-group "Productivity" ^
    --main-jar "%jar_name%" ^
    --add-modules "java.sql,java.datatransfer,java.desktop,java.logging,java.prefs,java.xml,jdk.unsupported" ^
    --description "Like yomichan, but outside the browser" ^
    --name "Kanjinator" ^
    --license-file ./LICENSE ^
    --resource-dir resources ^
    --icon resources/kanjinator.ico
