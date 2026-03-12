# wificonnect module consumer rules
# Keep all public classes and models
-keep class com.greatergoods.lib.wificonnect.** { *; }

# Keep JAR library classes (SmartConnection.jar)
-keep class ogemray.android.smartconnection.** { *; }
-dontwarn ogemray.android.smartconnection.**

# Keep JAR library classes (ggesptouchlib)
-keep class com.greatergoods.ggesptouchlib.** { *; }
-dontwarn com.greatergoods.ggesptouchlib.**
