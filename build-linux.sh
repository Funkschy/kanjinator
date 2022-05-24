#!/bin/sh

output_dir="linux-dist"
rm -rf "./$output_dir"

echo "-> building custom jre"
jlink \
    --add-modules "java.sql,java.datatransfer,java.desktop,java.logging,java.prefs,java.xml,jdk.unsupported" \
    --output "$output_dir" \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=2

echo "-> building application"
lein clean
lein uberjar
cp ./target/uberjar/*-standalone.jar "$output_dir/kanjinator.jar"
# remove windows/mac stuff from jar
zip -d "$output_dir/kanjinator.jar" nu/pattern/opencv/windows/\*
zip -d "$output_dir/kanjinator.jar" nu/pattern/opencv/osx/\*
zip -d "$output_dir/kanjinator.jar" win32-x86-64/\*
zip -d "$output_dir/kanjinator.jar" win32-x86/\*

echo "-> creating startup script"
startup="
#!/bin/sh
dir=\"\$(dirname \"\$0\")\"
\"\$dir/bin/java\" -jar \"\$dir/kanjinator.jar\"
"
echo "$startup" > "$output_dir/kanjinator.sh"
chmod +x "$output_dir/kanjinator.sh"

echo "-> creating tar ball"
tar -czvf kanjinator.tar.gz -C "$output_dir" .
rm -rf $output_dir
