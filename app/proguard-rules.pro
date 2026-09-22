-dontobfuscate

# androidx.security-crypto pulls in Google Tink, which references compile-only annotations
# (errorprone / javax.annotation) that are not packaged. Without these, R8 full-mode shrinking
# for the release build aborts on "Missing class ...". Ignore the absent annotations and keep
# Tink itself so its reflection/registration paths (EncryptedSharedPreferences) survive shrinking.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.protobuf.**
-keep class com.google.crypto.tink.** { *; }
# Tink's KeysDownloader references optional google-http-client / joda-time deps we don't ship.
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
