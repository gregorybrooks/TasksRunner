import spacy
import sys

"""Calls spaCy to extract noun_phrases from a string.

Pass the string to extract from via STDIN.
This script returns the extracted phrases in STDOUT.
"""
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
    nouns = [chunk.text.strip() for chunk in doc.noun_chunks]
    for a in set(nouns):
        print(a)
    print('EOL')
    sys.stdout.flush()
sys.exit(0)
