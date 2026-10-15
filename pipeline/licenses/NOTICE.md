# Data sources and their licenses

The dictionary data that this pipeline builds comes from these sources. Show this attribution in the app's credits.

| Source | Used for | License | Attribution |
|---|---|---|---|
| [New General Service List (NGSL)](https://www.newgeneralservicelist.com/) | The 2,800 core words, their rank and forms | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) | Browne, C., Culligan, B., & Phillips, J., The New General Service List |
| [Wiktionary](https://en.wiktionary.org/) via [Kaikki.org](https://kaikki.org/) | Senses, IPA, Vietnamese hints given to the LLM | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) | Wiktionary contributors; extracted by Tatu Ylonen, Wiktextract |
| [Open English WordNet 2025](https://github.com/globalwordnet/english-wordnet) | Topic words: which words are animals, food, clothes, … (TASK-61) | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) | McCrae, J. P., et al., Open English WordNet, based on Princeton WordNet 3.0 (© Princeton University) |
| [wordfreq](https://github.com/rspeer/wordfreq) | Topic words: keep only common words (Zipf frequency) | Code: [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0); data: [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) | Robyn Speer, wordfreq v3.1 (Zenodo, doi:10.5281/zenodo.7199437) |
| [Pixabay](https://pixabay.com/) | Photos and short clips per sense | [Pixabay Content License](https://pixabay.com/service/license-summary/) | Not required; we keep the author and page in the `media` table |

The Cambridge Young Learners word lists (© Cambridge University Press & Assessment) were read only to check
which everyday words the topic list misses. No part of them is copied into this repository.
