package io.doodlebot.backend

enum class DoodleConfig(
        val description: String,
        val hasConfirmButton: Boolean,
        val calendarIsEditable: Boolean,
        val confirmRecipientUrl: String) {
    SETUP("Pick dates to add to the Doodle.", true, true, "/setup"),
    EDIT("Choose dates you can attend to.", true, true, "/edit"),
    CLOSE("Select final dates.", true, true, "/close"),
    VIEW("These dates have been selected.", false, false, "/view")
}