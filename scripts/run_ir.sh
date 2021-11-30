#!/bin/bash
set -ve

cd target && java -jar tasks-runner-3.0.2.jar

chmod a+rw ${appFileLocation}/results.json
