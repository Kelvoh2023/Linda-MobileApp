"""Adds the SMS and contacts permissions and the SMS receiver to Android's manifest."""
import sys
path = sys.argv[1]
s = open(path, encoding="utf-8").read()
perms = ["android.permission.READ_SMS", "android.permission.RECEIVE_SMS", "android.permission.READ_CONTACTS", "android.permission.POST_NOTIFICATIONS"]
add = "".join('    <uses-permission android:name="%s" />\n' % p for p in perms if p not in s)
if add:
    i = s.index("<application")
    s = s[:i] + add.lstrip() + "\n    " + s[i:]
receiver = '''
        <receiver android:name=".SmsReceiver" android:exported="true" android:permission="android.permission.BROADCAST_SMS">
            <intent-filter android:priority="999">
                <action android:name="android.provider.Telephony.SMS_RECEIVED" />
            </intent-filter>
        </receiver>
'''
if ".SmsReceiver" not in s:
    i = s.rindex("</application>")
    s = s[:i] + receiver + "    " + s[i:]
open(path, "w", encoding="utf-8").write(s)
print("manifest patched")
