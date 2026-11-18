# 保留默认规则；本工程若启用 minify 需要，可在此添加 keep 规则
-keep class app.motrix.android.** { *; }
-keepclassmembers class app.motrix.android.MotrixJsBridge { *; }
