# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepclassmembers class * {
    @dagger.hilt.android.internal.lifecycle.HiltViewModelFactory <fields>;
}
-keep class com.yourapp.wlm.data.remote.soap.** { *; }
-keepclassmembers class com.yourapp.wlm.data.remote.soap.** { *; }
