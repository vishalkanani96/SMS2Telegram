package com.tigerworkshop.sms2telegram.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Security utility functions for handling sensitive data safely
 */
object SecurityUtils {
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ssZ")
        .withZone(ZoneId.systemDefault())

    /**
     * Formats a date/time in a thread-safe manner
     */
    fun formatTime(timestamp: Long): String {
        return dateFormatter.format(Instant.ofEpochMilli(timestamp))
    }

    /**
     * Sanitizes SMS content for logging/storage by removing sensitive information
     */
    fun sanitizeSmsContent(message: String): String {
        // Limit message length
        val limited = message.take(4000)
        
        // Remove null bytes and control characters (except newline/tab/carriage return)
        return limited
            .replace("\u0000", "")  // Remove null bytes
            .filter { it.code > 31 || it in arrayOf('\n', '\r', '\t') }
    }

    /**
     * Masks phone number for logging purposes
     */
    fun maskPhoneNumber(phone: String): String {
        return if (phone.length > 4) {
            phone.take(2) + "***" + phone.takeLast(2)
        } else {
            "***"
        }
    }

    /**
     * Creates a safe log message without exposing message content
     */
    fun createSafeLogMessage(sender: String, messageLength: Int): String {
        val maskedSender = maskPhoneNumber(sender)
        return "From $maskedSender [Message: $messageLength chars]"
    }

    /**
     * Validates Telegram API token format
     * Expected format: <bot_id>:<api_key>
     * Example: 123456789:ABCDefGHIjklmnoPQRSTuvwxyz
     */
    fun validateTelegramToken(token: String): ValidationResult {
        if (token.isBlank()) {
            return ValidationResult(false, "Token cannot be empty")
        }

        if (token.length < 25) {
            return ValidationResult(false, "Token is too short")
        }

        if (token.length > 150) {
            return ValidationResult(false, "Token is too long")
        }

        val parts = token.split(":")
        if (parts.size != 2) {
            return ValidationResult(false, "Token must contain exactly one colon (:)")
        }

        val (botId, apiKey) = parts

        // Bot ID should be numeric
        if (!botId.all { it.isDigit() }) {
            return ValidationResult(false, "Bot ID must be numeric")
        }

        if (botId.length < 5 || botId.length > 15) {
            return ValidationResult(false, "Bot ID has invalid length")
        }

        // API key should be alphanumeric plus hyphen and underscore
        if (!apiKey.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            return ValidationResult(false, "API key contains invalid characters")
        }

        if (apiKey.length < 20 || apiKey.length > 50) {
            return ValidationResult(false, "API key has invalid length")
        }

        return ValidationResult(true, "")
    }

    /**
     * Validates Telegram Chat ID format
     * Can be numeric (personal chat) or negative number (group chat)
     */
    fun validateChatId(chatId: String): ValidationResult {
        if (chatId.isBlank()) {
            return ValidationResult(false, "Chat ID cannot be empty")
        }

        val id = chatId.trim().toLongOrNull()
        if (id == null) {
            return ValidationResult(false, "Chat ID must be a valid number")
        }

        // Chat IDs can be positive or negative, but not zero
        if (id == 0L) {
            return ValidationResult(false, "Chat ID cannot be zero")
        }

        return ValidationResult(true, "")
    }

    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String
    )
}
