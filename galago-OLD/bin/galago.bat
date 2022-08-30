@REM ----------------------------------------------------------------------------
@REM Copyright 2001-2004 The Apache Software Foundation.
@REM
@REM Licensed under the Apache License, Version 2.0 (the "License");
@REM you may not use this file except in compliance with the License.
@REM You may obtain a copy of the License at
@REM
@REM      http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing, software
@REM distributed under the License is distributed on an "AS IS" BASIS,
@REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@REM See the License for the specific language governing permissions and
@REM limitations under the License.
@REM ----------------------------------------------------------------------------
@REM

@echo off

set ERROR_CODE=0

:init
@REM Decide how to startup depending on the version of windows

@REM -- Win98ME
if NOT "%OS%"=="Windows_NT" goto Win9xArg

@REM set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" @setlocal

@REM -- 4NT shell
if "%eval[2+2]" == "4" goto 4NTArgs

@REM -- Regular WinNT shell
set CMD_LINE_ARGS=%*
goto WinNTGetScriptDir

@REM The 4NT Shell from jp software
:4NTArgs
set CMD_LINE_ARGS=%$
goto WinNTGetScriptDir

:Win9xArg
@REM Slurp the command line arguments.  This loop allows for an unlimited number
@REM of arguments (up to the command line limit, anyway).
set CMD_LINE_ARGS=
:Win9xApp
if %1a==a goto Win9xGetScriptDir
set CMD_LINE_ARGS=%CMD_LINE_ARGS% %1
shift
goto Win9xApp

:Win9xGetScriptDir
set SAVEDIR=%CD%
%0\
cd %0\..\.. 
set BASEDIR=%CD%
cd %SAVEDIR%
set SAVE_DIR=
goto repoSetup

:WinNTGetScriptDir
set BASEDIR=%~dp0\..

:repoSetup


if "%JAVACMD%"=="" set JAVACMD=java

if "%REPO%"=="" set REPO=%BASEDIR%\lib

set CLASSPATH="%BASEDIR%"\etc;"%REPO%"\tupleflow-3.17.jar;"%REPO%"\trove4j-3.0.3.jar;"%REPO%"\utility-3.17.jar;"%REPO%"\xz-1.8.jar;"%REPO%"\jsr305-3.0.0.jar;"%REPO%"\jetty-server-9.4.11.v20180605.jar;"%REPO%"\jetty-http-9.4.11.v20180605.jar;"%REPO%"\jetty-util-9.4.11.v20180605.jar;"%REPO%"\jetty-io-9.4.11.v20180605.jar;"%REPO%"\commons-compress-1.18.jar;"%REPO%"\eval-3.17.jar;"%REPO%"\commons-math3-3.5.jar;"%REPO%"\snowball-stemmers-3.17.jar;"%REPO%"\krovetz-stemmer-3.17.jar;"%REPO%"\bliki-core-3.0.19.jar;"%REPO%"\commons-httpclient-3.1.jar;"%REPO%"\xmlenc-0.52.jar;"%REPO%"\snappy-java-1.0.5-M4.jar;"%REPO%"\reflections-0.9.10.jar;"%REPO%"\guava-15.0.jar;"%REPO%"\javassist-3.19.0-GA.jar;"%REPO%"\annotations-2.0.1.jar;"%REPO%"\caffeine-2.3.1.jar;"%REPO%"\httpclient-4.5.13.jar;"%REPO%"\httpcore-4.4.13.jar;"%REPO%"\commons-logging-1.2.jar;"%REPO%"\commons-codec-1.11.jar;"%REPO%"\slf4j-nop-1.7.12.jar;"%REPO%"\slf4j-api-1.7.12.jar;"%REPO%"\stanford-corenlp-3.7.0.jar;"%REPO%"\AppleJavaExtensions-1.4.jar;"%REPO%"\jollyday-0.4.9.jar;"%REPO%"\jaxb-api-2.2.7.jar;"%REPO%"\commons-lang3-3.3.1.jar;"%REPO%"\lucene-queryparser-4.10.3.jar;"%REPO%"\lucene-sandbox-4.10.3.jar;"%REPO%"\lucene-analyzers-common-4.10.3.jar;"%REPO%"\lucene-queries-4.10.3.jar;"%REPO%"\lucene-core-4.10.3.jar;"%REPO%"\javax.servlet-api-3.0.1.jar;"%REPO%"\xom-1.2.10.jar;"%REPO%"\xml-apis-1.3.03.jar;"%REPO%"\xercesImpl-2.12.0.jar;"%REPO%"\xalan-2.7.2.jar;"%REPO%"\serializer-2.7.2.jar;"%REPO%"\joda-time-2.9.jar;"%REPO%"\ejml-0.23.jar;"%REPO%"\javax.json-1.0.4.jar;"%REPO%"\protobuf-java-3.6.1.jar;"%REPO%"\stanford-corenlp-3.7.0-models.jar;"%REPO%"\core-3.17.jar
set EXTRA_JVM_ARGUMENTS=-ea -Dfile.encoding=UTF-8
goto endInit

@REM Reaching here means variables are defined and arguments have been captured
:endInit

%JAVACMD% %JAVA_OPTS% %EXTRA_JVM_ARGUMENTS% -classpath %CLASSPATH_PREFIX%;%CLASSPATH% -Dapp.name="galago" -Dapp.repo="%REPO%" -Dbasedir="%BASEDIR%" org.lemurproject.galago.core.tools.App %CMD_LINE_ARGS%
if ERRORLEVEL 1 goto error
goto end

:error
if "%OS%"=="Windows_NT" @endlocal
set ERROR_CODE=1

:end
@REM set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" goto endNT

@REM For old DOS remove the set variables from ENV - we assume they were not set
@REM before we started - at least we don't leave any baggage around
set CMD_LINE_ARGS=
goto postExec

:endNT
@endlocal

:postExec

if "%FORCE_EXIT_ON_ERROR%" == "on" (
  if %ERROR_CODE% NEQ 0 exit %ERROR_CODE%
)

exit /B %ERROR_CODE%
