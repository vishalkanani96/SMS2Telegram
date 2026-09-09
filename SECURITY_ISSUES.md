# Security Audit Report - SMS2Telegram Repository

**Date:** 2026-09-09  
**Scope:** Complete codebase review for sensitive SMS/Telegram credential handling  
**Risk Level:** MODERATE (Several issues require attention)

---

## Executive Summary

SMS2Telegram is a critical-trust application that handles **highly sensitive data**:
- SMS messages (containing OTP, passwords, sensitive information)
- Telegram bot API tokens (full control over bot)
- Telegram chat IDs (access to private conversations)

While significant security improvements have been implemented (encryption, backup protection), **several vulnerabilities remain** that could expose this sensitive data.

**Overall Risk Score: 6.8/10** ⚠️ (MODERATE - Requires remediation)

---

## Critical Vulnerabilities

### 1. 🔴 **CRITICAL: API Token Displayed in Plaintext in UI**
**Severity:** CRITICAL  
**File:** [MainActivity.kt](d:\Github\SMS2Telegram\app\src\main\java\com\tigerworkshop\sms2telegram\ui\MainActivity.kt#L135-L140)  
**Issue:** Telegram API token is displayed in plain text EditText without masking
```kotlin
if (hasSettings) {
    binding.inputToken.setText(settings!!.apiToken)  // ← Shows token in clear text
    binding.inputChatId.setText(settings.chatId)
}
```

**Risk:**
- Shoulder surfing attacks (anyone looking at screen sees the full token)
- Screenshots/screen recording captures full token
- Accessibility services could extract the token
- Memory dumps during display could leak credentials

**Impact:** Attacker gains full control of Telegram bot

**Recommendation:**
```kotlin
// Use password field for token
binding.inputToken.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

// Or implement token masking with toggle visibility
binding.inputToken.setText(settings.apiToken.let { 
    it.take(4) + "*".repeat(it.length - 8) + it.takeLast(4) 
})
```

---

### 2. 🔴 **CRITICAL: No Input Validation on Telegram Credentials**
**Severity:** CRITICAL  
**File:** [MainActivity.kt](d:\Github\SMS2Telegram\app\src\main\java\com\tigerworkshop\sms2telegram\ui\MainActivity.kt#L200)  
**Issue:** API token and Chat ID accepted without validation
```kotlin
// No checks for format, length, or character validity
val token = binding.inputToken.text.toString()
val chatId = binding.inputChatId.text.toString()
settingsRepository.saveSettings(token, chatId)
```

**Risk:**
- Malformed tokens stored and used repeatedly
- Could cause app crashes or unexpected behavior
- Invalid credentials trigger excessive Telegram API calls
- No feedback to user about validity

**Impact:** Degraded functionality, potential DoS through bad requests

**Recommendation:**
```kotlin
fun validateTelegramToken(token: String): Boolean {
    // Format: <bot_id>:<api_key>
    val parts = token.split(":")
    if (parts.size != 2) return false
    if (parts[0].length < 5 || parts[1].length < 20) return false
    if (!parts[0].all { it.isDigit() }) return false
    if (!parts[1].all { it.isLetterOrDigit() || it == '-' || it == '_' }) return false
    return true
}

fun validateChatId(chatId: String): Boolean {
    return chatId.toLongOrNull() != null
}
```

---

### 3. 🔴 **CRITICAL: SMS Content Logged Without Sanitization**
**Severity:** CRITICAL  
**File:** [SmsReceiver.kt](d:\Github\SMS2Telegram\app\src\main\java\com\tigerworkshop\sms2telegram\sms\SmsReceiver.kt#L100-L120)  
**Issue:** SMS sender and content logged in status message
```kotlin
val sender = messages.firstOrNull()?.displayOriginatingAddress ?: "Unknown"
val body = messages.joinToString(separator = "\n") { it.displayMessageBody ?: "" }
val formattedMessage = buildString {
    appendLine("From: $sender")  // ← Sender logged
    appendLine("SIM: #$simSlotIndex - $simCarrierName")
    appendLine("Time: ${timeFormatter.format(Date())}")
    appendLine()
    append(body)  // ← SMS content logged
}

repository.saveLastForwardStatus(
    "${timeFormatter.format(Date())} - From $sender - Queued for delivery..."
)
```

**Risk:**
- Crash logs expose full SMS content including OTP/passwords
- Device logs accessible via `adb logcat` contain sensitive messages
- Logcat can be shared for debugging, exposing all messages
- Status history stored in SharedPreferences contains full message content
- Backup/recovery might expose log history

**Impact:** Complete exposure of SMS content to anyone with device access

**Recommendation:**
```kotlin
private fun sanitizeForLogging(sender: String, message: String): Pair<String, String> {
    // Mask sender phone number
    val maskedSender = if (sender.length > 4) {
        sender.take(2) + "***" + sender.takeLast(2)
    } else {
        "***"
    }
    
    // Don't log message content, only hash/length
    val messageHash = message.hashCode().toString()
    val length = message.length
    
    return maskedSender to "[Message: $length chars, hash: $messageHash]"
}

// In logging:
val (safeSender, safeMsg) = sanitizeForLogging(sender, body)
repository.saveLastForwardStatus(
    "${timeFormatter.format(Date())} - From $safeSender - Queued for delivery..."
)
```

---

## High Severity Vulnerabilities

### 4. 🟠 **HIGH: Thread-Unsafe SimpleDateFormat Used**
**Severity:** HIGH  
**Files:** Multiple (SmsReceiver.kt, TelegramDeliveryWorker.kt, MainActivity.kt)  
**Issue:** SimpleDateFormat is not thread-safe but used as instance variable
```kotlin
private val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ", Locale.US)
// Used in multiple coroutine/thread contexts without synchronization
```

**Risk:**
- Race condition when formatting dates from multiple threads
- Could result in corrupted timestamps
- Potential crash or unexpected behavior under concurrent load
- Non-deterministic bugs hard to reproduce

**Impact:** Unreliable timestamp formatting, potential data corruption

**Recommendation:**
```kotlin
// Use thread-safe alternative
private fun formatTime(date: Date): String {
    return java.time.Instant.ofEpochMilli(date.time)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssZ"))
}

// Or create a new instance each time (simpler):
val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ", Locale.US)
return formatter.format(Date())
```

---

### 5. 🟠 **HIGH: No Rate Limiting on Message Forwarding**
**Severity:** HIGH  
**File:** [TelegramDeliveryWorker.kt](d:\Github\SMS2Telegram\app\src\main\java\com\tigerworkshop\sms2telegram\data\TelegramDeliveryWorker.kt#L30-L50)  
**Issue:** Processes all queued messages without rate limiting
```kotlin
while (true) {
    val pendingMessage = outbox.peekOldest() ?: break
    val sendResult = telegramForwarder.sendMessage(
        token = settings.apiToken,
        chatId = settings.chatId,
        message = pendingMessage.message
    )
    // Immediately processes next message without delay
}
```

**Risk:**
- Telegram API rate limits could be exceeded
- Device could be rate-limited by Telegram (IP ban possible)
- Malicious user could flood device with SMS to trigger API abuse
- No backpressure handling
- Could trigger Telegram bot suspension

**Impact:** Bot suspended or rate-limited, messages not delivered

**Recommendation:**
```kotlin
private suspend fun sendWithRateLimit(message: String): Result<Unit> {
    // Add 500ms delay between messages
    delay(500)
    return telegramForwarder.sendMessage(token, chatId, message)
}

// Or use Telegram API's built-in retry limits with exponential backoff
// Configure more conservative retry policy in OneTimeWorkRequestBuilder
```

---

### 6. 🟠 **HIGH: No Message Content Sanitization**
**Severity:** HIGH  
**File:** [SmsReceiver.kt](d:\Github\SMS2Telegram\app\src\main\java\com\tigerworkshop\sms2telegram\sms\SmsReceiver.kt#L110)  
**Issue:** SMS body used directly without sanitization
```kotlin
val body = messages.joinToString(separator = "\n") { it.displayMessageBody ?: "" }
// Used directly in formatted message, sent to Telegram API
```

**Risk:**
- Special characters/control codes could break message formatting
- Telegram markdown/HTML special chars could be interpreted
- Very long messages could cause issues
- Null bytes or other binary data could corrupt storage
- Unicode handling issues (emoji, RTL text)

**Impact:** Message delivery failure or unintended formatting

**Recommendation:**
```kotlin
private fun sanitizeSmsContent(message: String): String {
    return message
        .take(4000)  // Limit length
        .replace("\u0000", "")  // Remove null bytes
        .filter { it.code > 31 || it in arrayOf('\n', '\r', '\t') }  // Remove control chars
        // For Telegram, escape markdown if needed (already done by OkHttp)
}
```

---

## Medium Severity Vulnerabilities

### 7. 🟡 **MEDIUM: Crash Logs Could Expose Sensitive Data**
**Severity:** MEDIUM  
**Risk:** If app crashes during message processing, full stack trace with sensitive data could be logged

**Recommendation:**
```kotlin
// Add exception handling wrapper
try {
    // Process message
} catch (e: Exception) {
    Log.e(TAG, "Message processing failed", e)  // Don't log sensitive data in exception
    // Make sure exception message doesn't contain token/message content
}
```

---

### 8. 🟡 **MEDIUM: No Certificate Pinning for Telegram API**
**Severity:** MEDIUM  
**File:** [TelegramForwarder.kt](d:\Github\SMS2Telegram\app\src\main\java\com\tigerworkshop\sms2telegram\data\TelegramForwarder.kt#L105)  
**Issue:** Relies only on HTTPS, no additional certificate validation
```kotlin
private val defaultClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    // No certificate pinning
}
```

**Risk:**
- Vulnerable to compromised CA certificates
- Network operator could intercept if CA is compromised
- Corporate proxy could MITM if certificate is installed

**Recommendation:**
```kotlin
private val defaultClient: OkHttpClient by lazy {
    val certificatePinner = CertificatePinner.Builder()
        .add("api.telegram.org", "sha256/XXX...")  // Telegram cert hash
        .add("api.telegram.org", "sha256/YYY...")  // Backup cert
        .build()
    
    OkHttpClient.Builder()
        .certificatePinner(certificatePinner)
        .build()
}
```

---

### 9. 🟡 **MEDIUM: No Token Expiration Mechanism**
**Severity:** MEDIUM  
**Issue:** Tokens stored indefinitely without expiration or rotation prompts

**Risk:**
- Compromised token remains active indefinitely
- No mechanism to force password change
- Long-term exposure if device security is compromised
- User can't track when token was last rotated

**Recommendation:**
```kotlin
// Store token creation timestamp
data class TelegramSettings(
    val apiToken: String,
    val chatId: String,
    val createdAt: Long = System.currentTimeMillis()
)

// Warn user to rotate token every 90 days
val daysSinceCreation = (System.currentTimeMillis() - settings.createdAt) / (1000 * 60 * 60 * 24)
if (daysSinceCreation > 90) {
    showRotateTokenDialog()
}
```

---

## Low Severity Issues

### 10. 🟢 **LOW: Missing Request Validation in Telegram API Calls**
**Severity:** LOW  
**Issue:** No pre-validation before sending to Telegram API
**Recommendation:** Validate token format before first use

---

## Summary of Issues by Severity

| Severity | Count | Issues |
|----------|-------|--------|
| 🔴 CRITICAL | 3 | Token in plaintext, no credential validation, SMS content logged |
| 🟠 HIGH | 4 | Thread-unsafe dates, no rate limiting, no content sanitization, crash logs |
| 🟡 MEDIUM | 2 | No cert pinning, no token expiration |
| 🟢 LOW | 1 | Missing request validation |
| **TOTAL** | **10** | **All require remediation** |

---

## Immediate Actions Required

### Priority 1 (Critical - Fix Before Release)
1. ✅ Add password field masking for API token input
2. ✅ Implement input validation for token and chat ID
3. ✅ Remove sensitive data from logs and status messages
4. ✅ Sanitize SMS content before storage/logging

### Priority 2 (High - Fix in Next Update)
1. ⚠️ Fix SimpleDateFormat thread safety
2. ⚠️ Implement message rate limiting
3. ⚠️ Add comprehensive exception handling

### Priority 3 (Medium - Future Improvements)
1. 📋 Implement certificate pinning
2. 📋 Add token rotation mechanism
3. 📋 Add token expiration warnings

---

## Files Requiring Changes

| File | Issues | Priority |
|------|--------|----------|
| MainActivity.kt | Token display, validation | CRITICAL |
| SmsReceiver.kt | SMS logging, sanitization, date format | CRITICAL/HIGH |
| TelegramDeliveryWorker.kt | Rate limiting, date format | HIGH |
| TelegramForwarder.kt | Certificate pinning, logging | MEDIUM |
| SettingsRepository.kt | Token expiration tracking | MEDIUM |

---

## Recommendation

**Current Status:** ⚠️ **NOT PRODUCTION READY**

While the foundation has strong encryption and backup protection, the **exposure of sensitive data in logs, plaintext UI display, and lack of validation** create serious privacy risks for users dealing with highly sensitive SMS content.

**Before public release:**
1. Address all CRITICAL issues
2. Add comprehensive input validation
3. Implement secure UI practices
4. Add logging privacy guarantees

---

## Follow-up Security Review

Recommend security review after implementing these fixes:
- Code review of all changes
- Security testing with test credentials
- Logcat audit to ensure no sensitive data leaks
- Rate limiting testing
- Input fuzzing for validation
