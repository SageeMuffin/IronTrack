# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.irontrack.app.**$$serializer { *; }
-keepclassmembers class com.irontrack.app.** { *** Companion; }
-keepclasseswithmembers class com.irontrack.app.** { kotlinx.serialization.KSerializer serializer(...); }

# PdfBox-Android : classes chargées par réflexion, dépendances optionnelles absentes
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.gemalto.jp2.**
-dontwarn com.tom_roush.pdfbox.filter.JPXFilter
-dontwarn org.bouncycastle.**
-dontwarn javax.xml.**
