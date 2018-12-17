# WearOsHomeAssistantMediaPlayerRemote
Home Assistant (HASS) media_player controller for (Android) Wear OS with crown.

To get started, edit ./HtmlCrown/wear/src/main/java/nl/jolanrensen/htmlcrown/MainActivity.kt at the top with you own Home Assistant url, password etc.
After this you can compile the project (for instance with Android Studio) and run it on your Wear OS watch.
When you open the app, the watch tries to connect to the media_player and when it has, you can control the volume by turning the scroll wheel. You can also change the sound mode using the buttons on your watch and you can turn on/off the media_player by flicking out/in the watch.
