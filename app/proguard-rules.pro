# R8 规则：release 开启代码压缩与资源裁剪后，只为三个带反射/JNI 的第三方库保留符号。
# 应用自身没有反射、序列化框架或动态类加载；Compose、协程、WorkManager、Lifecycle 自带 consumer 规则。

# Apache POI（只用 HSSF 事件流读 .xls）：RecordFactory 通过反射构造 Record 子类，
# 且 POI 引用了大量不在 APK 中的可选依赖（XSSF/XMLBeans、log4j、commons）。
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.apache.commons.**
-dontwarn org.apache.xmlbeans.**
-dontwarn com.zaxxer.sparsebits.**
-dontwarn org.osgi.**
-dontwarn aQute.bnd.annotation.**
-dontwarn javax.xml.stream.**
-dontwarn org.w3c.dom.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**

# ONNX Runtime：JNI 侧按类名与方法名回调。
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# OpenCV：JNI。
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**
