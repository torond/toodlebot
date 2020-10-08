package io.doodlebot.backend

import java.time.LocalDate

data class DoodleData(
        val pickedDates: List<LocalDate>,
        val pickableDates: List<LocalDate>,
        val pickableDatesStatus: List<Pair<LocalDate, List<String>>>,
        val numberOfParticipants: Int)