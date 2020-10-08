package io.doodlebot.backend

import java.time.LocalDate

data class DoodleData(
        val pickableDatesStatus: List<Pair<LocalDate, List<String>>>,
        val numberOfParticipants: Int)