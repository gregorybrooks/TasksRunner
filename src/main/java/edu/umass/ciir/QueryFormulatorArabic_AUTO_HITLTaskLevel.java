package edu.umass.ciir;

import java.util.*;

public class QueryFormulatorArabic_AUTO_HITLTaskLevel extends NewQueryFormulator {

    QueryFormulatorArabic_AUTO_HITLTaskLevel(AnalyticTasks tasks) {
        super(tasks);
    }

    /**
     * Constructs the queries from the Tasks
     * */
    public void buildQueries(String queryFileName) {
        tasks.getTaskList().stream().parallel()
                .forEach(this::buildDocsString);
        writeQueryFile(queryFileName);
    }

    /**
     * Builds the task-level query for this task.
     *
     * @param t the task
     */
    protected void buildDocsString(Task t) {
        Map<String, Integer> soFar = new HashMap<>();
        Set<String> uniqueDocIds = new HashSet<>();
        List<String> uniqueDocTexts = new ArrayList<>();
        /*
         * Make a no-duplicates set of the example docids
         * for this task and all its requests
         */
        for (ExampleDocument d : t.taskExampleDocs) {
            uniqueDocIds.add(d.getDocid());
        }
        for (Request r : t.getRequests().values()) {
            for (ExampleDocument d : r.reqExampleDocs) {
                uniqueDocIds.add(d.getDocid());
            }
        }
        /* Now get the document texts so we can extract noun phrases */
        for (String docid : uniqueDocIds) {
            uniqueDocTexts.add(Document.getDocumentWithMap(docid));
        }

        /*
         * Extract unique noun phrases from the example documents,
         * with document use counts (how many docs use each phrase)
         */
        for (String extr : uniqueDocTexts) {
            addNounPhrases(soFar, extr);
        }
        /*
         * Also extract unique noun phrases from any "highlights"
         * provided for the requests for this task
         */
        for (Request r : t.getRequests().values()) {
            for (String extr : r.getReqExtrList()) {
                addNounPhrases(soFar, extr);
            }
        }

        /* Sort the unique noun phrases by document use count, descending */
        LinkedHashMap<String, Integer> reverseSortedMap = new LinkedHashMap<>();
        soFar.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEachOrdered(x -> reverseSortedMap.put(x.getKey(), x.getValue()));

        /*
         * Make a final list of those unique noun phrases used in more
         * than one example doc. (Include them all if there is only one example doc.)
         */
        List<String> finalList = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : reverseSortedMap.entrySet()) {
            if ((uniqueDocIds.size() == 1) || (entry.getValue() > 1)) {
                finalList.add(filterCertainCharacters(entry.getKey()));
            }
        }
        boolean useTaskParts = (mode.equals("AUTO-HITL") || mode.equals("HITL"));

        /*
         * Translate finalList, then put all those phrases into a string wrapped
         * in a #combine operator.
         * We also construct a non-translated version of the query for debugging purposes.
         */
        List<String> nonTranslatedFinalList = new ArrayList<>();
        /* First the non-translated version: */
        for (String phrase : finalList) {
            if (!phrase.contains(" ")) {
                nonTranslatedFinalList.add(phrase);
            } else {
                /* For multi-word phrases, we wrap the phrase in a sequential dependence operator */
                nonTranslatedFinalList.add("#sdm(" + phrase + ") ");
            }
        }
        /*
         * Next the translated version (translation includes adding #sdm
         * operators where appropriate
         */
        List<String> translatedFinalList;
        if (!Pathnames.targetLanguageIsEnglish) {
            translatedFinalList = Spacy.getTranslations(finalList);
        } else {
            translatedFinalList = nonTranslatedFinalList;
        }

        /*
         * Convert the final translated and non-translated lists of noun phrases
         * into strings wrapped in #combine operators
         */
        StringBuilder nonTranslatedNounPhrasesStringBuilder = new StringBuilder();
        for (String phrase : nonTranslatedFinalList) {
            nonTranslatedNounPhrasesStringBuilder.append(phrase).append(" ");
        }
        String nonTranslatedNounPhrasesString = nonTranslatedNounPhrasesStringBuilder.toString();
        nonTranslatedNounPhrasesString = "#combine(" + nonTranslatedNounPhrasesString + ") ";

        String nounPhrasesString;
        if (!Pathnames.targetLanguageIsEnglish) {
            StringBuilder nounPhrasesStringBuilder = new StringBuilder();
            for (String phrase : translatedFinalList) {
                nounPhrasesStringBuilder.append(filterCertainCharactersPostTranslation(phrase)).append(" ");
            }
            nounPhrasesString = nounPhrasesStringBuilder.toString();
            nounPhrasesString = "#combine(" + nounPhrasesString + ") ";
        } else {
            nounPhrasesString = nonTranslatedNounPhrasesString;
        }

        /*
         * At this point, nounPhasesString has the most important noun phrases from
         * the task's and its requests' example docs and its requests' highlights
         */

        /* Add query elements from the Task definition, which is allowed in HITL mode */
        List<String> taskParts = new ArrayList<>();
        boolean hasTaskParts = true;
        if (t.taskTitle == null && t.taskStmt == null && t.taskNarr == null) {
            hasTaskParts = false;
        } else {
            if (t.taskTitle != null) {
                taskParts.add(filterCertainCharacters(t.taskTitle));
            }
            if (t.taskStmt != null) {
                taskParts.add(filterCertainCharacters(t.taskStmt));
            }
            if (t.taskNarr != null) {
                taskParts.add(filterCertainCharacters(t.taskNarr));
            }
        }

        /* Translate taskParts (but we also keep a non-translated version for debugging purposes) */
        /*
         * Convert the translated and non-translated lists of task elements
         * into strings wrapped in #combine operators. No #sdm's are used on these
         * strings of words, which are sometimes long
         */
        String taskPartsString = "";
        String nonTranslatedTaskPartsString = "";
        if (hasTaskParts) {
            StringBuilder nonTranslatedTaskPartsStringBuilder = new StringBuilder();
            for (String phrase : taskParts) {
                nonTranslatedTaskPartsStringBuilder.append(phrase).append(" ");
            }
            nonTranslatedTaskPartsString = nonTranslatedTaskPartsStringBuilder.toString();
            nonTranslatedTaskPartsString = "#combine(" + nonTranslatedTaskPartsString + ") ";
            List<String> translatedTaskParts;
            if (!Pathnames.targetLanguageIsEnglish) {
                translatedTaskParts = Spacy.getTranslations(taskParts);
                StringBuilder taskPartsStringBuilder = new StringBuilder();
                for (String phrase : translatedTaskParts) {
                    taskPartsStringBuilder.append(filterCertainCharactersPostTranslation(phrase)).append(" ");
                }
                taskPartsString = taskPartsStringBuilder.toString();
                taskPartsString = "#combine(" + taskPartsString + ") ";
            } else {
                taskPartsString = nonTranslatedTaskPartsString;
            }
        }

        /*
         * Construct the final Galago queries, translated and non-translated versions,
         * from the noun phrases and task elements. Use the #combine's weights to heavily
         * emphasize the task elements (title, narrative and statement)
         */
        String finalString;
        String nonTranslatedFinalString;

        if (useTaskParts && hasTaskParts) {
            finalString = "#combine:0=0.2:1=0.8 (" + nounPhrasesString + " "
                    + taskPartsString + ")";
            nonTranslatedFinalString = "#combine:0=0.2:1=0.8 (" + nonTranslatedNounPhrasesString + " "
                    + nonTranslatedTaskPartsString + ")";
        } else {
            finalString = "#combine (" + nounPhrasesString + ")";
            nonTranslatedFinalString = "#combine (" + nonTranslatedNounPhrasesString + ")";
        }

        setQuery(t.taskNum, finalString);
        setNonTranslatedQuery(t.taskNum, nonTranslatedFinalString);
    }

    private void addNounPhrases(Map<String, Integer> soFar, String extr) {
        List<String> nouns = Spacy.getNounPhrases(extr);
        for (String noun_phrase : nouns) {
            if (noun_phrase.length() > 2) {  // omit small single words
                if (!soFar.containsKey(noun_phrase)) {
                    soFar.put(noun_phrase, 1);
                } else {
                    int x = soFar.get(noun_phrase);
                    soFar.put(noun_phrase, x + 1);
                }
            }
        }
    }
}
