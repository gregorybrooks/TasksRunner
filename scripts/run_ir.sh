#!/bin/bash
set -ve

cd target && java -jar tasks-runner-3.1.0.jar

chmod a+rw ${appFileLocation}/results.json
