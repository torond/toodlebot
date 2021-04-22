# API
The code generating the GUI is called *frontend*.

Types of Doodles:
- Setup Doodle
- Standard View (answering the Doodle)
- Closing View
- Results View
- Edit View

Frontend needs:
- Type of Doodle
- List of dates
- Authentication of user given by Telegram Login-URL?

```kotlin
enum class Type {
    SETUP, NORMAL, CLOSING
}


fun generateDoodle(type: Type, dates?: List = null): whatever{...}
```

## To Do
### Setup Doodle

- Send: Standard calendar, no restrictions + Cancel / confirm buttons
- Return: List of dates

### Standard View
- User requests site (following cryptolink): Register user to this Doodle
- Send: Restricted calendar (+ auth token?)
- Return: List of dates (subset of dates sent to user)

## Comments from Server.kt
```//pickableDates by doodleId (dates enabled in the calendar)
// -> /setup/{doodleId}: All dates
// -> /answer/{doodleId}: Dates chosen by admin on setup -> proposedDates
// -> /close/{doodleId}: Dates chosen by admin on setup -> proposedDates
// -> /view/{doodleId}: none
//pickedDates by doodleId & participantId (prev. picked dates for updating doodle or answers)
// -> /setup/{doodleId}: Dates prev. chosen by admin -> proposedDates
// -> /answer/{doodleId}: Dates prev. chosen by participant -> yesDates
//finalDates by doodleId
// -> /close/{doodleId}: Final dates prev. chosen by admin -> finalDates
// -> /view/{doodleId}: Final dates prev. chosen by admin -> finalDates
//
// => Mustache Template anpassen, s.d. Server.kt verständlich bleibt
// MAPPING
// (0. All dates / no dates -> controlled by DoodleConfig or absence of 1.)
// 1. Dates chosen by admin on setup, open for answers -> proposedDates in backend, enabledDates or defaultDates in frontend
// 2. Dates chosen by participants -> yesDates in backend, defaultDates in frontend (other: chosenDates, answeredDates, repliedDates, respondedDates, consideredDates, committedDates, attendableDates)
// 3. Final dates chosen by admin -> finalDates in backend and frontend
/*
* Principle of least astonishment.
* Be precise.
* -> Mention parameters (byDoodleId), but not in add methods?
* -> ID's always with corresponding Table (DoodleId not Uuid)
*
* -> No EntityId<...> in Server.kt, at most the doodle doodleId
* -> No other IDs? Yes.
*
* ideas:
* - only update methods -> addOrUpdate
*
* questions: Optional return types?
* */
```