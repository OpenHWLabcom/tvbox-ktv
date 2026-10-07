#!/bin/bash
# 构建 KTV 点歌台 APK (com.openhardwarelab.ktv)
set -e
cd "$(dirname "$0")"
SRC=$HOME/.openclaw/workspace/ktv/android/ktv
PKG=com/openhardwarelab/ktv
KS=ktv.keystore
export JAVA_HOME=$PWD/jdk8u504-b01
export PATH=$JAVA_HOME/bin:$PATH
export LD_LIBRARY_PATH=$PWD/aapt81/usr/lib/aarch64-linux-gnu:$PWD/aapt81/usr/lib/aarch64-linux-gnu/android
AAPT=$PWD/aapt81/usr/bin/aapt
AJ=$PWD/plat/android-4.4.2/android.jar

rm -rf ktvclasses ktvout ktv.unsigned.apk ktv.apk
mkdir -p ktvclasses ktvout

echo "[1/5] javac"
javac -source 7 -target 7 -bootclasspath "$AJ" -d ktvclasses "$SRC/$PKG/"*.java

echo "[2/5] d8"
java -cp bt/android-11/lib/d8.jar com.android.tools.r8.D8 --min-api 19 --lib "$AJ" \
     --output ktvout ktvclasses/$PKG/*.class

echo "[3/5] aapt package"
$AAPT package -f -M "$SRC/AndroidManifest.xml" -I "$AJ" -F ktv.unsigned.apk

echo "[4/5] add classes.dex"
( cd ktvout && $AAPT add ../ktv.unsigned.apk classes.dex )

echo "[5/5] sign"
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -alias k -keyalg RSA \
  -keysize 2048 -validity 10000 -storepass android -keypass android \
  -dname "CN=OpenHW Lab, OU=dev, O=OpenHW Lab, L=Singapore, S=Singapore, C=SG"
jarsigner -keystore "$KS" -storepass android -keypass android ktv.unsigned.apk k
cp -f ktv.unsigned.apk ktv.apk
echo "=== OK ==="
ls -l ktv.apk
$AAPT list -v ktv.apk 2>/dev/null | head -4
