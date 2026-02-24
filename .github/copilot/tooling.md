# Local tool setup

## Java, Maven and Ant

Java versions are located inside following window/batch an *nix/bash locations

```batch
:: Java LTS version is on of 8,11,17,21,25 default is 21
set java_version=21
set JAVA_HOME=%USERPROFILE%\.ecdev\java\ee\JAVA%version%
set PATH=%JAVA_HOME%;%PATH%

:: Maven installation path
set MAVEN_HOME=%USERPROFILE%\.ecdev\tool\apache-maven-3.9.9
set PATH=%MAVEN_HOME%;%PATH%

:: Ant installation path
set ANT_HOME=%USERPROFILE%\.ecdev\tool\apache-ant-1.10.14
set PATH=%ANT_HOME%;%PATH%
``` 

```bash
# Java LTS version is on of 8,11,17,21,25 default is 21
java_version=21
export JAVA_HOME=~/.ecdev/java/ee/JAVA${java_version}
export PATH=$JAVA_HOME/bin:$PATH

# Maven installation path
export MAVEN_HOME=~/.ecdev/tool/apache-maven-3.9.4
export PATH=${MAVEN_HOME}/bin:$PATH

# Maven installation path
export ANT_HOME=~/.ecdev/tool/apache-ant-1.10.14
export PATH=${ANT_HOME}/bin:$PATH

```
