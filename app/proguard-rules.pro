# WishWait release rules (R8 is on for release builds).
#
# No reflection-based serialization: data is written with org.json by hand, so
# models need no keep rules. Google Play Billing and Google Play services
# (sign-in) ship their own consumer rules inside their libraries.

# Enum names are saved in DataStore and in backup files (category, status, reasons,
# moods, strictness, reminder types). Keep them exactly as written, so data saved
# by one version of the app always loads in the next, and old backups restore.
-keep enum com.karencalulo.wishwait.** { *; }
