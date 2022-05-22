#!/bin/bash
set -ve

cd target && java -jar tasks-runner-5.0.0.jar

set +e
chmod a+rw ${appFileLocation}/results.json
set -e
