# R8 rules for release builds, on top of the ones the libraries bring.

# jmdns logs through slf4j, which looks for a logging backend the app doesn't ship (it then
# logs nothing).
-dontwarn org.slf4j.impl.StaticLoggerBinder

# protobuf-lite reads each message's fields by name, through reflection (title_ for title),
# and R8 would otherwise rename or drop them. The library doesn't bring this rule itself.
-keep class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
