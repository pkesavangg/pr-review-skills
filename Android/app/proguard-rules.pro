# ============================================================================
# ProGuard/R8 Rules for MeApp (Weight Gurus)
# ============================================================================

# ----------------------------------------------------------------------------
# General Settings
# ----------------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ----------------------------------------------------------------------------
# Kotlin
# ----------------------------------------------------------------------------
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-keep class kotlin.reflect.** { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }
-dontwarn kotlin.reflect.jvm.internal.**

# Keep Kotlin property intrinsics for KProperty1 reflection (used by GraphUtil)
-keep class kotlin.jvm.internal.** { *; }

# Kotlin coroutines
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembernames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepclassmembernames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ----------------------------------------------------------------------------
# Kotlinx Serialization
# ----------------------------------------------------------------------------
-keepattributes RuntimeVisibleAnnotations
-keep,includedescriptorclasses class com.dmdbrands.gurus.weight.**$$serializer { *; }
-keepclassmembers class com.dmdbrands.gurus.weight.** {
    *** Companion;
}
-keepclasseswithmembers class com.dmdbrands.gurus.weight.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# Keep all @Serializable classes (Navigation routes, models, etc.)
-if @kotlinx.serialization.Serializable class **
-keep class <1> {
    static <1>$Companion Companion;
    <init>(...);
    <fields>;
}
-if @kotlinx.serialization.Serializable class ** {
    static ** INSTANCE;
}
-keep class <1> {
    static <1>$Companion Companion;
    static ** INSTANCE;
}

# ----------------------------------------------------------------------------
# Navigation Routes (critical for type-safe Navigation3)
# ----------------------------------------------------------------------------
-keep class com.dmdbrands.gurus.weight.core.navigation.AppRoute { *; }
-keep class com.dmdbrands.gurus.weight.core.navigation.AppRoute$** { *; }
-keep class com.example.nav3integration.PublicRoute { *; }

# ----------------------------------------------------------------------------
# Retrofit + OkHttp
# ----------------------------------------------------------------------------
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Keep all Retrofit API interfaces AND data classes in the api package
# (e.g. OperationsResponse, HealthConnectIntegrationRequest, HealthConnectSyncEntry)
-keep class com.dmdbrands.gurus.weight.data.api.** { *; }

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keep class okhttp3.internal.publicsuffix.PublicSuffixDatabase { *; }

# ----------------------------------------------------------------------------
# Gson
# ----------------------------------------------------------------------------
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class com.google.gson.stream.** { *; }

# Keep TypeToken and subclasses (used by JsonConverter for Room)
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Keep all API model classes (request/response bodies for Gson serialization)
-keep class com.dmdbrands.gurus.weight.domain.model.api.** { *; }

# Keep migration models (@SerializedName)
-keep class com.dmdbrands.gurus.weight.migration.model.** { *; }

# ----------------------------------------------------------------------------
# Room Database
# ----------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.dmdbrands.gurus.weight.data.storage.db.** { *; }

# ----------------------------------------------------------------------------
# Protobuf (javalite)
# ----------------------------------------------------------------------------
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keep class com.dmdbrands.gurus.weight.data.storage.datastore.** { *; }

# ----------------------------------------------------------------------------
# DataStore
# ----------------------------------------------------------------------------
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# ----------------------------------------------------------------------------
# Hilt / Dagger
# Hilt ships its own consumer rules for annotations, but javax.inject does not.
# ----------------------------------------------------------------------------
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager { *; }
-dontwarn dagger.hilt.internal.aggregatedroot.codegen.**
-dontwarn hilt_aggregated_deps.**

# Keep Hilt DI modules
-keep class com.dmdbrands.gurus.weight.core.di.** { *; }

# Keep annotated classes
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @dagger.Module class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }

# Dagger Assisted Injection (GraphViewModel, HistoryDetailViewModel, etc.)
-keep class dagger.assisted.** { *; }
-keep @dagger.assisted.AssistedFactory class * { *; }
-keepclassmembers @dagger.assisted.AssistedInject class * { <init>(...); }

# ----------------------------------------------------------------------------
# Sealed Entry hierarchy (Entry, ScaleEntry, BpmEntry) - used in EntryService
# when() and toPeriodBodyScaleSummary(); must keep for runtime type checks
# ----------------------------------------------------------------------------
-keep class com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry { *; }
-keep class com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry$* { *; }
-keep class com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry { *; }
-keep class com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry { *; }
-keepclassmembers class com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry { *; }
-keepclassmembers class com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry { *; }

# IUnitProcessable (process() used by HistoryMonth, PeriodBodyScaleSummary, Entry)
-keep interface com.dmdbrands.gurus.weight.domain.model.common.IUnitProcessable { *; }
-keepclassmembers class * implements com.dmdbrands.gurus.weight.domain.model.common.IUnitProcessable { *; }

# History list and graph: HistoryMonth used in StateFlow and reducer
-keepclassmembers class com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth { <fields>; <methods>; }
-keepnames class com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth { *; }

# ----------------------------------------------------------------------------
# Firebase / GMS
# ----------------------------------------------------------------------------
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Push notification service declared in AndroidManifest
-keep class com.dmdbrands.gurus.weight.core.service.pushNotification.PushNotificationService { *; }

# ----------------------------------------------------------------------------
# Domain Enums (used in serialization and navigation)
# ----------------------------------------------------------------------------
-keep class com.dmdbrands.gurus.weight.domain.enums.** { *; }
-keep class com.dmdbrands.gurus.weight.features.common.enums.** { *; }
-keep class com.dmdbrands.gurus.weight.features.ScaleSetup.enums.** { *; }
-keep class com.dmdbrands.gurus.weight.features.metricinfo.MetricInfoSource { *; }
-keep class com.dmdbrands.gurus.weight.features.appPermissions.enum.** { *; }

# ----------------------------------------------------------------------------
# Domain Models used in navigation/serialization/reflection
# ----------------------------------------------------------------------------
-keep class com.dmdbrands.gurus.weight.domain.model.storage.** { *; }
-keep class com.dmdbrands.gurus.weight.features.common.model.** { *; }
-keep class com.dmdbrands.gurus.weight.domain.model.common.** { *; }

# PeriodBodyScaleSummary: properties accessed via KProperty1 reflection in GraphUtil
# Must preserve member names so KProperty1.get() resolves correctly
-keepclassmembers class com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary {
    <fields>;
    <methods>;
}
-keepnames class com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary { *; }

# ----------------------------------------------------------------------------
# FormControl reflection: FormGroup/MultiFormGroup use javaClass.declaredFields
# to iterate form controls by field name. Must preserve field names.
# ----------------------------------------------------------------------------
-keepclassmembers class * {
    com.dmdbrands.gurus.weight.features.common.helper.form.FormControl *;
    com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup *;
}

# ----------------------------------------------------------------------------
# Graph/Chart helper classes (uses KProperty1 reflection)
# ----------------------------------------------------------------------------
-keep class com.dmdbrands.gurus.weight.features.common.helper.graph.** { *; }
-keep class com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper { *; }

# ----------------------------------------------------------------------------
# SegmentButtonGroup uses KProperty1.get() on data class properties
# ----------------------------------------------------------------------------
-keepclassmembers class com.dmdbrands.gurus.weight.features.common.components.SegmentButtonData { <fields>; }
-keepclassmembers class com.dmdbrands.gurus.weight.features.metricinfo.MetricInfoKey { <fields>; }

# ----------------------------------------------------------------------------
# GG Bluetooth library (external AAR)
# ----------------------------------------------------------------------------
-keep class com.dmdbrands.library.ggbluetooth.** { *; }
-dontwarn com.dmdbrands.library.ggbluetooth.**

# ----------------------------------------------------------------------------
# WorkManager workers
# ----------------------------------------------------------------------------
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# ----------------------------------------------------------------------------
# Coil (image loading)
# ----------------------------------------------------------------------------
-dontwarn coil3.**

# ----------------------------------------------------------------------------
# Vico Charts
# ----------------------------------------------------------------------------
-keep class com.patrykandpatrick.vico.** { *; }
-dontwarn com.patrykandpatrick.vico.**

# ----------------------------------------------------------------------------
# Play Services / Play Review
# ----------------------------------------------------------------------------
-keep class com.google.android.play.** { *; }
-dontwarn com.google.android.play.**

# ----------------------------------------------------------------------------
# Application & Activities (declared in AndroidManifest)
# ----------------------------------------------------------------------------
-keep class com.dmdbrands.gurus.weight.MeAppApplication { *; }
-keep class com.dmdbrands.gurus.weight.MainActivity { *; }
-keep class com.dmdbrands.gurus.weight.core.shared.utilities.webview.InAppWebViewActivity { *; }

# ----------------------------------------------------------------------------
# DI interfaces (repository + service contracts)
# ----------------------------------------------------------------------------
-keep interface com.dmdbrands.gurus.weight.domain.repository.** { *; }
-keep interface com.dmdbrands.gurus.weight.domain.services.** { *; }

# ----------------------------------------------------------------------------
# Suppress warnings
# ----------------------------------------------------------------------------
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn org.checkerframework.**
-dontwarn afu.org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.concurrent.**
