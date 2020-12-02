# ShinkuBot

編譯成jar後執行:
```
java -Djava.library.path=<libtdjni.so_path> -jar bot.jar <api_id> <api_hash> <bot_token>
```
windows須將-Djava.library.path指向dll所在路徑，linux為so所在路徑，詳如https://github.com/tdlib/td
