# Kanjinator

Kanjinator lets you select a part of your screen, performs
[OCR](https://en.wikipedia.org/wiki/Optical_character_recognition) on it and then displays a translation for the recognized words.

Currently only Japanese (using jisho.org) is supported, but the code is written in a way that would
allow adding other languages easily.

## WIP

At this point, the tool will only print the words instead of showing the translation.

## Installation

You'll need to have [Leiningen](https://leiningen.org/) installed.

``` shell
git clone https://github.com/Funkschy/kanjinator
cd kanjinator
lein run
```

## Usage

After starting the program, you can simply select part of your screen by dragging the mouse over it.
