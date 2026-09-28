# Clementine Remote for Android

See [README.md](README.md) for what the app does, and [RELEASING.md](RELEASING.md) for releasing it.

## Keep the iOS and Android clients in step

Clementine Remote has two clients: this one and the [iOS
client](https://github.com/clementine-player/iOS-Remote). Someone should find the same features,
working the same way, whichever phone they use. So a change to what users see or do goes into both
clients at the same time: the same screens and flows, the same results, the same wording.

That's the experience, not the code. Each client builds it the way its own platform does things:
here, Android's own APIs and Material Design guidelines, in the conventions of this codebase. Don't
port code line by line from the other client, or bend this one's structure to match it. If the
remote control protocol changes, both clients need to speak the new version.

When you're asked to add a feature or change how something behaves, and the user hasn't said it's
only for this client, ask them whether they want it in both clients before you start. If they do and
the other client's repository isn't in the session, ask them to add it (for example with
`/add-dir`), so both changes are made together.
