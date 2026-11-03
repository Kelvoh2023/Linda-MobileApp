"""Edits android/app/build.gradle: release signing, version numbers, and the QuickJS engine the background scanner uses.
Reads LINDA_KEYSTORE, LINDA_STORE_PASS, LINDA_KEY_ALIAS, LINDA_KEY_PASS and LINDA_BUILD at build time.
If no keystore is given the release build is not signed (the workflow then falls back to a debug build)."""
import re, sys
path = sys.argv[1]
s = open(path, encoding="utf-8").read()

if "LINDA_SIGNING" not in s:
    signing = '''
    // LINDA_SIGNING: one fixed key for every release, so new versions install over old ones.
    signingConfigs {
        release {
            def ks = System.getenv("LINDA_KEYSTORE")
            if (ks) {
                storeFile file(ks)
                storePassword System.getenv("LINDA_STORE_PASS")
                keyAlias System.getenv("LINDA_KEY_ALIAS")
                keyPassword System.getenv("LINDA_KEY_PASS")
            }
        }
    }
'''
    s, n = re.subn(r"(\n    buildTypes \{)", signing + r"\1", s, count=1)
    assert n == 1, "buildTypes not found"
    s, n = re.subn(r"(release \{\n)(\s+)(minifyEnabled false)", r"\1\2signingConfig signingConfigs.release\n\2\3", s, count=1)
    assert n == 1, "release block not found"
    s, n = re.subn(r"versionCode \d+", 'versionCode Integer.parseInt(System.getenv("LINDA_BUILD") ?: "1")', s, count=1)
    assert n == 1, "versionCode not found"
    s, n = re.subn(r'versionName "[^"]*"', 'versionName "1.0." + (System.getenv("LINDA_BUILD") ?: "0")', s, count=1)
    assert n == 1, "versionName not found"

if "quickjs-android" not in s:
    s, n = re.subn(r"(\n    implementation project\(':capacitor-android'\))", r"\1\n    implementation 'app.cash.quickjs:quickjs-android:0.9.2'", s, count=1)
    assert n == 1, "capacitor-android dependency line not found"

open(path, "w", encoding="utf-8").write(s)
if "androidx.work:work-runtime" not in s:
    s, n2 = re.subn(r"(\n    implementation project\(':capacitor-android'\))", r"\1\n    implementation 'androidx.work:work-runtime:2.10.0'", s, count=1)
    open(path, "w").write(s)
print("gradle patched: signing, version, quickjs")
