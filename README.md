# Kanjinator

Kanjinator lets you select a part of your screen, performs
[OCR](https://en.wikipedia.org/wiki/Optical_character_recognition) on it and then displays a translation for the recognized words.

Currently only Japanese (using jisho.org) is supported, but the code is written in a way that would
allow adding other languages easily.

## Why?

Sometimes you have Kanji on your screen that you cannot simply copy, e.g as part of an image or a game. In this case it's really hard to look up the meaning of that kanji because you'd either need to know how it's pronounced or alternatively search the symbol by its radicals, which is pretty annoying.

### Example
![shogi golden king](screenshots/demo-2.png)

## Building

You'll need to have [Leiningen](https://leiningen.org/) installed.

``` shell
git clone https://github.com/Funkschy/kanjinator
cd kanjinator
lein run
```

If you don't want to build it yourself, just grab a prebuilt jar from the releases site.

## Usage

After starting the program, you can simply select part of your screen by dragging the mouse over it.

![select a part on the screen](screenshots/demo-1-before.png)
![the application will do the rest](screenshots/demo-1-after.png)

## Extend

In contrast to its name, Kanjinator isn't actually built only for japanese. The application is actually language agnostic. If you want to add another language, you just have to implement the Language protocol with regards to the dictionaries/spec.clj. Then you can just change the current-language inside of the config map in config.clj to your language.
