package io.doodlebot.backend

enum class DoodleConfig(
        val text: String,
        val hasConfirmButton: Boolean,
        val isEditable: Boolean,
        val confirmUrl: String) {
    SETUP("Pick dates to add to the Doodle.", true, true, "/genDoodle"),
    EDIT("Choose dates you can attend to.", true, true, "/todo"),
    CLOSING("Select final dates.", true, true, "/todo"),
    RESULTS("These dates have been selected.", false, false, "/todo")
}