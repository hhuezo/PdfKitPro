# Crash reports stay useful after R8 renaming.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# PdfBox-Android instantiates filters, fonts, and security handlers by reflection.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.tom_roush.**
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn org.spongycastle.**
-dontwarn javax.imageio.**
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn sun.**
