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

## Translations are shared with the iOS client

Both clients are translated on [Transifex](https://app.transifex.com/davidsansome/clementine-remot/),
as two resources of one project, by the same translators, and Transifex's translation memory offers
a translation made for one client's string for the same English string in the other. So a string
both clients show is one string to translators: give it the same English, and the same note for
translators, in both. Here a note is the XML comment just before the string in `values/strings.xml`;
in the iOS client, it's the `comment:` of the string in the Swift code, which the String Catalog
takes.

When the clients fill a placeholder differently, the note says so, and asks for a translation that
works with both. For example, iOS writes durations in its own words, which can't take another
grammatical form, so the sentences they go into have to read right with the duration as it is.

## Release Notes

Add a "Release-note:" trailer to commits that make user-facing changes so that the changes are mentioned in
the next production release.

## Release notes

A commit that changes something users notice ends with a `Release-note:` trailer: one line,
written for users (what's new, not how it was done). Releases collect them as their notes on
Google Play, F-Droid and GitHub, and a week with none makes no release (see
[RELEASING.md](RELEASING.md)). Commits that don't change what users see (refactoring, tests, CI,
docs) have none. When a change goes into both clients, give it the same note in both.
