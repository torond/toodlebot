# DateFinderBot
Add file `environment.properties` to repository root with contents:
```
local_ip=<Local ip address of the machine the application is running on>
bot_name=<Name of the Telegram bot>
bot_token=<Token of the Telegram bot>
```

Use `/setdomain` in BotFather to enable Login URL.
For development purposes, this should be the same as `local_ip` above.