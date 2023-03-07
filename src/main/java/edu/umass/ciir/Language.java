package edu.umass.ciir;

import java.util.Locale;

public class Language {
    /**
     * Returns the normalized form of a language that is in a corpus file's "language" field.
     * We convert everything to an all-lowercase, English word like "arabic".
     * @param rawLanguage The value seen in the corpus file, like "ar" or "Arabic" or "zho".
     * @return the normalized form of the language, like "arabic"
     */
    static String toCanonicalForm(String rawLanguage) {
        rawLanguage = rawLanguage.toLowerCase(Locale.ROOT);
        switch (rawLanguage) {
            case "ar": case "arabic":
                rawLanguage = "arabic";
                break;
            case "ko": case "kor": case "korean":
                rawLanguage = "korean";
                break;
            case "fa": case "farsi":
                rawLanguage = "farsi";
                break;
            case "zh": case "zho": case "chinese":
                rawLanguage = "chinese";
                break;
            case "ru": case "rus": case "russian":
                rawLanguage = "russian";
                break;
            case "en": case "eng": case "english":
                rawLanguage = "english";
                break;
            default:
                throw new TasksRunnerException("Invalid raw language passed to toCanonicalForm: " + rawLanguage);
        }
        return rawLanguage;
    }

    static String toTwoCharForm(String canonicalForm) {
        switch (canonicalForm) {
            case "arabic":
                canonicalForm = "ar";
                break;
            case "korean":
                canonicalForm = "ko";
                break;
            case "farsi":
                canonicalForm = "fa";
                break;
            case "chinese":
                canonicalForm = "zh";
                break;
            case "russian":
                canonicalForm = "ru";
                break;
            case "english":
                canonicalForm = "en";
                break;
            default:
                throw new TasksRunnerException("Invalid language passed to toTwoCharForm: " + canonicalForm);
        }
        return canonicalForm;

    }

    static String toThreeCharForm(String canonicalForm) {
        switch (canonicalForm) {
            case "arabic":
                canonicalForm = "ara";
                break;
            case "korean":
                canonicalForm = "kor";
                break;
            case "farsi":
                canonicalForm = "far";
                break;
            case "chinese":
                canonicalForm = "zho";
                break;
            case "russian":
                canonicalForm = "rus";
                break;
            case "english":
                canonicalForm = "eng";
                break;
            default:
                throw new TasksRunnerException("Invalid language passed to toThreeCharForm: " + canonicalForm);
        }
        return canonicalForm;
    }

    /**
     * Returns the ISO 639-3 abbreviation for a language, which is what the ISI event annotator requires.
     * @param canonicalForm The canonical form of the language (e.g. chinese)
     * @return The language abbreviation
     */
    static String toISIThreeCharForm(String canonicalForm) {
        switch (canonicalForm) {
            case "arabic":
                canonicalForm = "ara";
                break;
            case "korean":
                canonicalForm = "kor";
                break;
            case "farsi":
                canonicalForm = "fas";
                break;
            case "chinese":
                canonicalForm = "zho";
                break;
            case "russian":
                canonicalForm = "rus";
                break;
            case "english":
                canonicalForm = "eng";
                break;
            default:
                throw new TasksRunnerException("Invalid language passed to toISIThreeCharForm: " + canonicalForm);
        }
        return canonicalForm;
    }
}
