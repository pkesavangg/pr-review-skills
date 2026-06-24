# appsync module consumer rules
# Keep all public classes and models
-keep class com.greatergoods.libs.appsync.** { *; }

# Keep JNI callback (called from native code)
-keep class com.dmdbrands.appsync.CameraHandlerCallback { *; }
