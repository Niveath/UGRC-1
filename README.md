
Run these in order

- docker start soot-project
- docker exec -it soot-project bash
- export CLASSPATH=/usr/local/lib/soot/soot-4.4.1-jar-with-dependencies.jar:$CLASSPATH

To run the java program

- cd usr/local/src/
- javac ugrc1/Driver.java ugrc1/callGraphAnalysis.java
- java ugrc1/Driver


