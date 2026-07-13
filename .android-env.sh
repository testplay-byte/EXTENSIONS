# Android SDK environment — source this file to get Android SDK on PATH.
# Created: 2026-06-22 (session 05). Installed per user's tested sequence.
export ANDROID_HOME=/home/z/my-project/ANDROID_SDK
export ANDROID_SDK_ROOT=/home/z/my-project/ANDROID_SDK
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# JDK 17 (Temurin) — added session 08 for ext-lib 16 build
export JAVA_HOME=/home/z/my-project/JDK/jdk-17.0.13+11
export PATH="$JAVA_HOME/bin:$PATH"
