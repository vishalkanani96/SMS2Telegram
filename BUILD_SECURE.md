# Secure Build Instructions

## Building with Release Signing

### Prerequisites
- Java Development Kit (JDK 21+)
- Android SDK (with Build-Tools 35+)
- Gradle 8.13+

### Generate Signing Keystore (One-time setup)

```powershell
$JAVA_HOME = "C:\Program Files\Java\jdk-21"
& "$JAVA_HOME\bin\keytool.exe" -genkey -v `
  -keystore sms2telegram.keystore `
  -keyalg RSA -keysize 2048 `
  -validity 10000 `
  -alias sms2telegram-key `
  -dname "CN=SMS2Telegram,OU=Developer,O=Personal,C=US"
```

### Set Environment Variables

**Windows PowerShell:**
```powershell
$env:KEYSTORE_PATH = "C:\path\to\sms2telegram.keystore"
$env:KEYSTORE_PASSWORD = "your-secure-password"
$env:KEY_ALIAS = "sms2telegram-key"
$env:KEY_PASSWORD = "your-secure-password"
```

**Linux/macOS:**
```bash
export KEYSTORE_PATH="/path/to/sms2telegram.keystore"
export KEYSTORE_PASSWORD="your-secure-password"
export KEY_ALIAS="sms2telegram-key"
export KEY_PASSWORD="your-secure-password"
```

### Build Release APK

```powershell
$env:ANDROID_HOME = "C:\Users\YourUsername\AppData\Local\Android\Sdk"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"

cd path\to\SMS2Telegram
.\gradle-8.13\bin\gradle.bat assembleRelease
```

### Output
Signed APK will be available at:
```
app/build/outputs/apk/release/app-release.apk
```

## Security Best Practices

✅ **DO:**
- Store keystore passwords in environment variables or secure key management
- Use different passwords for different environments (dev/prod)
- Keep keystore file in a secure location (NOT in version control)
- Use long, complex passwords (min 16 characters)
- Rotate signing certificates every 1-2 years for public releases

❌ **DON'T:**
- Commit keystore files to Git
- Hardcode passwords in build files
- Share keystore passwords via email/messaging
- Use the same password for multiple certificates
- Upload keystore to cloud storage without encryption

## Verifying APK Signature

```powershell
& "C:\Program Files\Java\jdk-21\bin\jarsigner.exe" `
  -verify -verbose "app\build\outputs\apk\release\app-release.apk"
```

Expected output: "jar verified"

## Security Enhancements Applied

- ✅ Encrypted storage for API tokens and SMS content (AES256-GCM)
- ✅ Backup protection disabled to prevent credential theft
- ✅ HTTPS-only network traffic enforced
- ✅ BroadcastReceiver hidden from external apps
- ✅ Code obfuscation with ProGuard
- ✅ Proper APK signing configuration

## For CI/CD Integration

Use GitHub Secrets or similar CI/CD tools to securely store:
- KEYSTORE_PATH
- KEYSTORE_PASSWORD
- KEY_ALIAS
- KEY_PASSWORD

Example GitHub Actions:
```yaml
- name: Build Release APK
  env:
    KEYSTORE_PATH: ${{ secrets.KEYSTORE_PATH }}
    KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
    KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
    KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
  run: |
    ./gradle-8.13/bin/gradle.bat assembleRelease
```
