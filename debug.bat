call mvn clean install -Dmaven.test.skip=true
set MAVEN_OPTS=-Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=8000,suspend=n
call mvn hpi:run -Djetty.port=8090  -Dhpi.prefix=/jenkins