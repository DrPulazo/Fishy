package com.example.fishy.domain.validation

sealed class ValidationState {
    data object Empty : ValidationState()
    data object InProgress : ValidationState()
    data object Valid : ValidationState()
    data object Invalid : ValidationState()
    data class InvalidWithSuggestion(val suggestion: String) : ValidationState()
}

object ContainerWagonValidator {

    fun normalizeContainer(raw: String): String =
        raw.trim().uppercase().replace(" ", "")

    fun isValidContainerNumber(containerNumber: String): Boolean {
        val cleanNumber = normalizeContainer(containerNumber)
        if (cleanNumber.length != 11) return false
        if (!cleanNumber.matches(Regex("^[A-Z]{4}\\d{7}$"))) return false
        val calculated = calculateContainerCheckDigit(cleanNumber)
        val actual = cleanNumber[10].digitToInt()
        return calculated == actual
    }

    fun isValidWagonNumber(wagonNumber: String): Boolean {
        val cleanNumber = wagonNumber.trim().replace(" ", "")
        if (!cleanNumber.matches(Regex("^\\d{8}$"))) return false
        val calculated = calculateWagonCheckDigit(cleanNumber)
        val actual = cleanNumber[7].digitToInt()
        return calculated == actual
    }

    fun getContainerCheckDigit(containerNumber: String): Int? {
        val cleanNumber = normalizeContainer(containerNumber)
        if (cleanNumber.length < 10 || !cleanNumber.matches(Regex("^[A-Z]{4}\\d{6}"))) return null
        return calculateContainerCheckDigit(cleanNumber.take(10) + "0")
    }

    fun getWagonCheckDigit(wagonNumberWithoutCheckDigit: String): Int? {
        val cleanNumber = wagonNumberWithoutCheckDigit.trim().replace(" ", "")
        if (cleanNumber.length != 7 || !cleanNumber.matches(Regex("^\\d{7}$"))) return null
        return calculateWagonCheckDigit(cleanNumber)
    }

    private fun calculateContainerCheckDigit(containerNumber: String): Int {
        val letterValues = mapOf(
            'A' to 10, 'B' to 12, 'C' to 13, 'D' to 14, 'E' to 15,
            'F' to 16, 'G' to 17, 'H' to 18, 'I' to 19, 'J' to 20,
            'K' to 21, 'L' to 23, 'M' to 24, 'N' to 25, 'O' to 26,
            'P' to 27, 'Q' to 28, 'R' to 29, 'S' to 30, 'T' to 31,
            'U' to 32, 'V' to 34, 'W' to 35, 'X' to 36, 'Y' to 37,
            'Z' to 38
        )
        var sum = 0
        for (i in 0 until 10) {
            val char = containerNumber[i]
            val numericValue = if (char.isLetter()) {
                letterValues[char] ?: 0
            } else {
                char.digitToInt()
            }
            sum += numericValue * (1 shl i)
        }
        return (sum % 11) % 10
    }

    private fun calculateWagonCheckDigit(firstSevenDigits: String): Int {
        val weights = listOf(2, 1, 2, 1, 2, 1, 2)
        var sum = 0
        for (i in 0 until 7) {
            val digit = firstSevenDigits[i].digitToInt()
            var product = digit * weights[i]
            while (product > 0) {
                sum += product % 10
                product /= 10
            }
        }
        val remainder = sum % 10
        return if (remainder == 0) 0 else 10 - remainder
    }

    fun validateContainerNumberLive(input: String): ValidationState {
        val clean = normalizeContainer(input)
        if (clean.isEmpty()) return ValidationState.Empty
        if (clean.length > 11) return ValidationState.Invalid
        if (!clean.matches(Regex("^[A-Z]{0,4}\\d{0,7}$"))) return ValidationState.Invalid
        if (clean.length == 11) {
            return if (isValidContainerNumber(clean)) {
                ValidationState.Valid
            } else {
                val correctDigit = getContainerCheckDigit(clean.substring(0, 10))
                ValidationState.InvalidWithSuggestion(correctDigit?.toString() ?: "")
            }
        }
        return ValidationState.InProgress
    }

    fun validateWagonNumberLive(input: String): ValidationState {
        val clean = input.trim().replace(" ", "")
        if (clean.isEmpty()) return ValidationState.Empty
        if (clean.length > 8) return ValidationState.Invalid
        if (!clean.matches(Regex("^\\d{0,8}$"))) return ValidationState.Invalid
        if (clean.length == 8) {
            return if (isValidWagonNumber(clean)) {
                ValidationState.Valid
            } else {
                val correctDigit = getWagonCheckDigit(clean.substring(0, 7))
                ValidationState.InvalidWithSuggestion(correctDigit?.toString() ?: "")
            }
        }
        return ValidationState.InProgress
    }
}
