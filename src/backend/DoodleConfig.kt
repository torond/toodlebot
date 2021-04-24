package io.doodlebot.backend

enum class DoodleConfig(
        val description: String,
        val hasConfirmButton: Boolean,
        val calendarIsEditable: Boolean,
        val confirmRecipientUrl: String,
        val isSetup: Boolean) {
    SETUP("Pick dates to add to the Doodle.", true, true, "/setup", true),
    ANSWER("Choose dates you can attend to.", true, true, "/answer", false),
    CLOSE("Select final dates.", true, true, "/close", false),
    VIEW("These dates have been selected.", false, false, "/view", false)
}