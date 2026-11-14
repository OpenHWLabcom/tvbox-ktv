#!/usr/bin/env bash
# 需要: jdk8、aapt、android-4.4.2/android.jar、d8.jar
set -e
cd "$(dirname "$0")"
PKG=com/openhardwarelab/ktv
KS=ktv.keystore
rm -rf out classes ktv.unsigned.apk ktv.apk
mkdir -p out classes
javac -source 7 -target 7 -bootclasspath "$ANDROID_JAR" -d classes $PKG/*.java
java -cp "$D8_JAR" com.android.tools.r8.D8 --min-api 19 --lib "$ANDROID_JAR" --output out classes/$PKG/*.class
"$AAPT" package -f -M AndroidManifest.xml -A assets -I "$ANDROID_JAR" -F ktv.unsigned.apk
( cd out && "$AAPT" add ../ktv.unsigned.apk classes.dex )
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -alias k -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass android -keypass android -dname "CN=OpenHW Lab, O=OpenHW Lab, C=SG"
jarsigner -keystore "$KS" -storepass android -keypass android ktv.unsigned.apk k
cp -f ktv.unsigned.apk ktv.apk
echo "OK -> ktv.apk"
