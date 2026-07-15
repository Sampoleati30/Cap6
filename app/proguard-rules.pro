# MapLibre keeps JNI entry points through its consumer rules. CAP 6 models are kept
# because future offline data updates may deserialize them reflectively.
-keep class fr.cap6.app.** { *; }
-dontwarn org.maplibre.**
