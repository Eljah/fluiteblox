# Audiveris integration notes (Android)

## What is available from Maven

- `org.audiveris:proxymusic:4.0.3` is available on Maven Central and is now used in this project
  for MusicXML generation.

## What is not directly available as a single Maven library

- The full Audiveris OMR engine (`Audiveris/audiveris`) is primarily distributed as an application
  (Gradle multi-module project) and not as one stable embeddable Maven artifact for Android.

## Current integration in this repository

- MusicXML export now builds a `ScorePartwise` model via ProxyMusic classes and marshals XML
  through `Marshalling.marshal(...)`.
- A safe fallback to legacy string-based XML export is kept if marshalling fails at runtime.
