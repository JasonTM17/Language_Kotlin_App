# Word pronunciation

The vocabulary speaker uses a recorded clip when the selected language section
links one for the headword and the file metadata passes the checks below.
Otherwise it asks Android's Text-to-Speech engine to speak the word with the
selected language locale. Japanese cards use the stored kana reading for TTS
when one is available.

## Audio sources and matching

On a speaker tap, the app looks up the headword in English Wiktionary and
examines only the section for the selected language. It then asks Wikimedia
Commons for that file's HTTPS audio URL, media type, license and attribution
metadata. A recording is streamed only if the language template, audio file,
supported MIME type, reusable license, attribution and source links pass the
checks. A Chinese pronunciation embedded elsewhere on a page, for example, is
never used as Japanese audio.
Wiktionary's page and template metadata provide the word-to-file association;
if a clip has a `text=` label, that label must also match the requested
headword. A phrase clip such as “a cat” is skipped for “cat.” LinguaAI does not
independently transcribe every recording, so this metadata check is not an
acoustic verification of the audio bytes.

Each clip shows a credit dialog with the Wiktionary entry, Commons file page,
author/credit, license name and license link. The audio remains hosted by
Commons; it is not bundled into the app or copied into a local audio library.

Sources:

- [Wiktionary API](https://en.wiktionary.org/w/api.php)
- [Wikimedia Commons API](https://commons.wikimedia.org/w/api.php)
- [MediaWiki parse API and wikitext property](https://www.mediawiki.org/wiki/API%3AParsing_wikitext)
- [MediaWiki imageinfo properties](https://www.mediawiki.org/wiki/API%3AImageinfo/en)
- [CommonsMetadata fields, including Artist and license metadata](https://www.mediawiki.org/wiki/Extension%3ACommonsMetadata/en)
- [Wikimedia API access policy](https://www.mediawiki.org/wiki/Wikimedia_APIs/Access_policy)
- [Wikimedia API etiquette](https://www.mediawiki.org/wiki/API:Etiquette)
- [Wikimedia Commons reuse guidance](https://commons.wikimedia.org/wiki/Commons:REUSE)
- [Android TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech)

## Coverage and fallback

Recorded coverage is partial. The catalogue has more than two million rows, and
Wiktionary does not provide a matching audio file for every entry, language,
script, pronunciation variant or dialect. The app does not synthesize or bulk
download missing files. Missing, unlicensed, mismatched or unreachable audio
falls back to the installed Android voice for the selected language. If the
device has no compatible voice installed, Android cannot speak the word; the
app reports that condition instead of silently using a different language.

The selected language code is cached locally with the selected language ID so
the TTS fallback keeps the right locale when the language catalogue endpoint is
offline. Metadata lookup results are held in a bounded in-memory cache for the
current app process; audio bytes are streamed from Commons and are not stored.

## Privacy and request behavior

Wikimedia lookup happens only after the learner taps a speaker. The request
contains the selected headword and its language code, and Wikimedia receives
ordinary network metadata such as the device's IP address. LinguaAI does not
send an account ID, profile, session token or learning history to these public
read-only APIs. TTS fallback does not require a Wikimedia request when no
language code is available; when a lookup is attempted but misses or fails, the
app uses TTS after the lookup returns.

Requests are serialized, spaced apart, identified with a descriptive
application User-Agent, and respect `Retry-After` cooldowns. The lookup cache is
limited to 128 entries per app process; failed/transient responses are not
cached as missing pronunciations.

## License handling

License metadata is evaluated for each Commons file. The app accepts only
recognized CC0, public-domain, CC BY or CC BY-SA labels with an allowed HTTPS
license link and usable attribution metadata. The credit UI links to the
original source and the file's license page. The Commons file page remains the
authoritative place to review the current file-specific terms before separate
reuse or redistribution.
