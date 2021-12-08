#!/bin/bash
set -ve

cd target && java -jar tasks-runner-3.0.3.jar

set +e
chmod a+rw ${appFileLocation}/results.json
