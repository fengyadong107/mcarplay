# Shizuku 在**独立进程**里按类名实例化 UserService（ComponentName(pkg, CarInputServiceImpl::class.java.name)），
# 类名被混淆或成员被裁掉，远端服务直接起不来。
-keep class com.mcarplay.app.shizuku.CarInputServiceImpl { *; }
# AIDL 生成的接口与 Stub/Proxy 走 Binder 反射
-keep interface com.mcarplay.app.shizuku.ICarInput { *; }
-keep class com.mcarplay.app.shizuku.ICarInput$Stub { *; }
-keep class com.mcarplay.app.shizuku.ICarInput$Stub$Proxy { *; }

# 这三家的可选依赖/注解在运行时不会用到，缺了不必报错（否则 R8 直接让构建失败）
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
-dontwarn javax.**
-dontwarn org.jmdns.**

# 只裁剪、不重命名：本项目的验证手段基本是真机日志与字节级抓包，混淆后崩溃栈、
# 反射（JmDNS/slf4j 的 ServiceLoader）与现场排查都会变难，体积收益也不在重命名上。
-dontobfuscate

# 崩溃栈要可读
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
