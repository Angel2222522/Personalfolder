package com.angel.personalfolder.data

import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId

/** Converts the app's LocalDate contract to the Long timestamps used by Room/WorkManager. */
object ReminderDatePolicy {
    fun dueAt(date: LocalDate, leadDays: Int, zone: ZoneId = ZoneId.systemDefault()): Long = try {
        require(leadDays >= 0) { "Οι ημέρες προειδοποίησης δεν μπορεί να είναι αρνητικές." }
        epochMillis(date.minusDays(leadDays.toLong()), zone)
    } catch (error: DateTimeException) {
        throw IllegalArgumentException("Η ημερομηνία δεν μπορεί να αποθηκευτεί με ασφάλεια.", error)
    } catch (error: ArithmeticException) {
        throw IllegalArgumentException("Η ημερομηνία δεν μπορεί να αποθηκευτεί με ασφάλεια.", error)
    }

    fun deadlineAt(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        epochMillis(date, zone)

    private fun epochMillis(date: LocalDate, zone: ZoneId): Long = try {
        date.atStartOfDay(zone).toInstant().toEpochMilli()
    } catch (error: DateTimeException) {
        throw IllegalArgumentException("Η ημερομηνία δεν μπορεί να αποθηκευτεί με ασφάλεια.", error)
    } catch (error: ArithmeticException) {
        throw IllegalArgumentException("Η ημερομηνία δεν μπορεί να αποθηκευτεί με ασφάλεια.", error)
    }
}
