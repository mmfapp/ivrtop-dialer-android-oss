# Generated PJSUA2 artefacts (not committed)

Run from the repository root (Linux/macOS/WSL):

```bash
scripts/pjsip/build-pjsip-android.sh
```

Output:

```
java/org/pjsip/pjsua2/*.java        SWIG Java bindings
jniLibs/arm64-v8a/libpjsua2.so
jniLibs/arm64-v8a/libc++_shared.so
jniLibs/armeabi-v7a/...
jniLibs/x86_64/...
```

The Gradle task `verifyPjsua2` fails the build with a clear message when these files are missing.
