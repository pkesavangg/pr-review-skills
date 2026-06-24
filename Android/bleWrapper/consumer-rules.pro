# bleWrapper module consumer rules
# Keep all public classes/interfaces exposed to the app module
-keep class com.greatergoods.blewrapper.** { *; }

# Keep the external GG Bluetooth library (exposed via api dependency)
-keep class com.dmdbrands.library.ggbluetooth.** { *; }
-dontwarn com.dmdbrands.library.ggbluetooth.**
