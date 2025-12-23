package com.example.fishy.utils

object ContainerWagonValidator {

    // Проверка номера контейнера по ISO 6346
    fun isValidContainerNumber(containerNumber: String): Boolean {
        // Очищаем от пробелов и переводим в верхний регистр
        val cleanNumber = containerNumber.trim().uppercase()

        // Базовая проверка формата: 4 буквы + 6 цифр + 1 контрольная цифра
        if (cleanNumber.length != 11) return false
        if (!cleanNumber.matches(Regex("^[A-Z]{4}\\d{7}$"))) return false

        // Проверяем контрольную цифру
        val calculatedCheckDigit = calculateContainerCheckDigit(cleanNumber)
        val actualCheckDigit = cleanNumber[10].toString().toInt()

        return calculatedCheckDigit == actualCheckDigit
    }

    // Проверка номера вагона (8 цифр)
    fun isValidWagonNumber(wagonNumber: String): Boolean {
        // Очищаем от пробелов
        val cleanNumber = wagonNumber.trim()

        // Проверяем, что это 8 цифр
        if (!cleanNumber.matches(Regex("^\\d{8}$"))) return false

        // Проверяем контрольную цифру
        val calculatedCheckDigit = calculateWagonCheckDigit(cleanNumber)
        val actualCheckDigit = cleanNumber[7].toString().toInt()

        return calculatedCheckDigit == actualCheckDigit
    }

    // Вспомогательная функция для контейнеров
    fun getContainerCheckDigit(containerNumber: String): Int? {
        val cleanNumber = containerNumber.trim().uppercase()
        if (cleanNumber.length < 10 || !cleanNumber.matches(Regex("^[A-Z]{4}\\d{6}"))) return null
        return calculateContainerCheckDigit(cleanNumber + "0") // Добавляем временную цифру
    }

    // Вспомогательная функция для вагонов: рассчитывает контрольную цифру ДЛЯ 7 цифр
    fun getWagonCheckDigit(wagonNumberWithoutCheckDigit: String): Int? {
        val cleanNumber = wagonNumberWithoutCheckDigit.trim()
        // Проверяем, что на входе ровно 7 цифр
        if (cleanNumber.length != 7 || !cleanNumber.matches(Regex("^\\d{7}$"))) return null
        // Передаём в алгоритм расчёта 7 цифр. Алгоритм сам возьмёт все 7.
        return calculateWagonCheckDigit(cleanNumber)
    }


    // Приватные функции расчета

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
        // Обрабатываем первые 10 символов
        for (i in 0 until 10) {
            val char = containerNumber[i]
            val numericValue = if (char.isLetter()) {
                letterValues[char] ?: 0
            } else {
                char.digitToInt() // Более безопасное преобразование цифры
            }
            // Умножаем на 2 в степени i (вес позиции по стандарту)
            sum += numericValue * (1 shl i) // shl - побитовый сдвиг влево, эквивалент 2^i
        }
        // Контрольная цифра — остаток от деления sum на 11, затем на 10
        return (sum % 11) % 10
    }

    private fun calculateWagonCheckDigit(firstSevenDigits: String): Int {
        val weights = listOf(2, 1, 2, 1, 2, 1, 2)
        var sum = 0

        for (i in 0 until 7) {
            val digit = firstSevenDigits[i].digitToInt() // Безопасное получение цифры
            var product = digit * weights[i]
            // Суммируем цифры произведения, если оно > 9 (т.е. двузначное)
            while (product > 0) {
                sum += product % 10
                product /= 10
            }
        }

        val remainder = sum % 10
        return if (remainder == 0) 0 else 10 - remainder
    }

    // Функция для проверки в процессе ввода (возвращает состояние)
    fun validateContainerNumberLive(input: String): ValidationState {
        val clean = input.trim().uppercase()

        if (clean.isEmpty()) return ValidationState.EMPTY
        if (clean.length > 11) return ValidationState.INVALID

        // Проверяем формат по мере ввода
        val isValidFormat = clean.matches(Regex("^[A-Z]{0,4}\\d{0,7}$"))
        if (!isValidFormat) return ValidationState.INVALID

        if (clean.length == 11) {
            return if (isValidContainerNumber(clean)) {
                ValidationState.VALID
            } else {
                // Предлагаем правильную контрольную цифру
                val correctDigit = getContainerCheckDigit(clean.substring(0, 10))
                ValidationState.INVALID_WITH_SUGGESTION(correctDigit?.toString() ?: "")
            }
        }

        return ValidationState.IN_PROGRESS
    }

    fun validateWagonNumberLive(input: String): ValidationState {
        val clean = input.trim()

        if (clean.isEmpty()) return ValidationState.EMPTY
        if (clean.length > 8) return ValidationState.INVALID

        // Проверяем, что вводятся только цифры
        if (!clean.matches(Regex("^\\d{0,8}$"))) return ValidationState.INVALID

        if (clean.length == 8) {
            return if (isValidWagonNumber(clean)) {
                ValidationState.VALID
            } else {
                // Предлагаем правильную контрольную цифру
                val correctDigit = getWagonCheckDigit(clean.substring(0, 7))
                ValidationState.INVALID_WITH_SUGGESTION(correctDigit?.toString() ?: "")
            }
        }

        return ValidationState.IN_PROGRESS
    }
}

// Состояния валидации для отображения в UI
sealed class ValidationState {
    object EMPTY : ValidationState()
    object IN_PROGRESS : ValidationState()
    object VALID : ValidationState()
    object INVALID : ValidationState()
    data class INVALID_WITH_SUGGESTION(val suggestion: String) : ValidationState()
}