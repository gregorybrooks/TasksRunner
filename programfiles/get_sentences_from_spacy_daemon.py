import spacy
import sys

"""Calls spaCy to extract noun_phrases, verbs, or named entities from a string.

Pass one argument, which is any of these operations:
    noun_phrases
    verbs
    named_entities
You can request combinations by concatenating the operations with plus signs (no spaces):
    noun_phrases+named_entites
    noun_phrases+verbs+named_entities
Returns -1 if passed an invalid operation.
Pass the string to extract from via STDIN.
This script returns the extracted phrases in STDOUT.
"""

operation = 'sentences'

# Load English tokenizer, tagger, parser, NER and word vectors
nlp = spacy.load("en_core_web_sm")

# Get query from stdin:
while True:
    text = ""
    for line in sys.stdin:
        if line.startswith('EOF'):
            sys.exit(0)
        elif line.startswith('EOD'):
            break
        text += line
    
    doc = nlp(text)
    nouns = []
    verbs = []
    entities = []
    sentences = []
    sentences = [sent.text.strip() for sent in doc.sents]
    for a in sentences:
        print(a)
    print('EOL')
    sys.stdout.flush()
sys.exit(0)
