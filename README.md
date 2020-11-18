Android-Remote
==============

[![ci][1]][2]

Clementine Remote lets you remotely control the music player "Clementine".

__IMPORTANT:__<br />
You need at least Clementine 1.3 to use this remote!

With this application you can control the music player "Clementine" while you are sitting on your couch eating potato chips.
You have access to your library, playlists, read the lyrics while listening to your favourite song, enjoy the cover art, ...

If you receive a call or want to make one, you don't have pause the current track, Clementine Remote lowers the volume for you!

You want to hear the current track or album while you are on the go? No problem, download them with one click to your phone! No need to plug in a USB cable, it works via wifi! You can even download whole playlists!

__All Features:__
* Control player
* Download songs from Clementine to your phone
* Browse your library
* Search for songs
* Displays the cover art
* Read the lyrics
* Rate, Love and ban tracks
* Change the volume
* Volume lowers when you receive a call
* Shuffle / Repeat playback
* Playlist selection
* Lockscreen Controls
* Clementine Network Discovery: You don't have to enter the ip, Clementine Remote finds Clementine Players itself in the network!

__TRANSLATE:__
We use [transifex](https://www.transifex.com/amuttsch/clementine-remote/dashboard/), you can login via your Github account or create a new one.

__INSTALLATION DETAILS:__<br />
Download Clementine 1.3 from here: http://www.clementine-player.org/downloads
* The remote control is disabled by default. You have to activate it in the settings. *
* Downloads are disabled by default. You have to activate it in the setting, too *

Get Clementine Android-Remote:

<a href="https://f-droid.org/packages/de.qspool.clementineremote/" target="_blank">
<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="80"/></a>
<a href="https://play.google.com/store/apps/details?id=de.qspool.clementineremote" target="_blank">
<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" alt="Get it on Google Play" height="80"/></a>

This application is licensed under the GNU GPLv3.

If you have questions, suggestions etc. please write an e-mail.

Help: https://github.com/clementine-player/Android-Remote/wiki

__PERMISSIONS:__<br />
* android.permission.ACCESS_NETWORK_STATE: Check if you are connected to a wifi network.
* android.permission.ACCESS_WIFI_STATE: Get your current ip address.
* android.permission.CHANGE_WIFI_MULTICAST_STATE: Is needed for Clementine Network Discovery.
* android.permission.INTERNET: To connect to Clementine.
* android.permission.WAKE_LOCK: The device is in partial wake mode when connect to increase stability.
* android.permission.READ_PHONE_STATE: Is needed to detect calls and lower Clementine volume.
* android.permission.WRITE_EXTERNAL_STORAGE: For downloading songs.
* com.android.vending.BILLING: Is needed for doing donations.

[1]: https://github.com/clementine-player/Android-Remote/workflows/ci/badge.svg
[2]: https://github.com/clementine-player/Android-Remote/actions
