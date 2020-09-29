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