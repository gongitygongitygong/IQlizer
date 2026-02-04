IQlizer is a simple system audio equaliser made mainly for use with Spotify.
It applies EQ at the system level, since Spotify itself doesn’t provide a proper equaliser on all platforms.


Depending on your audio setup and codec, the effect may not apply instantly.
If that happens, pausing and replaying the audio usually fixes it.


This project exists because system-wide equalisers are either bloated, locked behind paid software, or just annoying to configure.


IQlizer aims to be as lightweight, straightforward, easy to use, and most importantly, free.


IQlizer features System-level audio equalisation, works alongside Spotify playback, has a simple interface with adjustable frequency bands with a native backend written in C++



**Requirements:**

JDK 11 or newer

Gradle (or use the included wrapper)

A supported audio backend on your system


**Usage**

Open Spotify

Start playing audio

Launch IQlizer

Adjust the frequency sliders


If the EQ doesn’t apply immediately, pause and resume playback.


**Known issues**

Does not work woth YouTube most of the time as YouTube uses their own audio codec. Spotify is strongly recommended.

If the app keeps stopping, check that you have granted it the microphone permission. This is because one of the libraries used auto adds the mic permission. IQlizer does not need the mic to function but requires the permission to be granted.

EQ may not apply instantly on some systems

Audio backend support varies depending on OS and configuration

Not all codecs behave the same way


This is expected behaviour and not a crash.


**Contributing**


If you want to contribute, feel free to open a pull request or issue.
Clean code, clear commits, and explanations help a lot.



**License**


Currently no license is included.
If you plan to reuse or distribute this code, a license should be added.

https://drive.google.com/file/d/1niG8rKvbFHNjDYvRgm_g9ri05lgVCnPs/view?usp=sharing
