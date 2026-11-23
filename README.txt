=========================================================
      PROGETTO CINEMAX - LABORATORIO INTERDISCIPLINARE B
=========================================================

CineMax - Laboratorio Interdisciplinare B

Prerequisito: JDK 25 (LTS) e Maven.

Compilazione e generazione dei file JAR (in /bin):
mvn clean package

Generazione documentazione Javadoc (in /doc/javadoc):
mvn javadoc:javadoc

Avvio Server:
java -jar bin/serverCM.jar

Avvio Client:
java -jar bin/clientCM.jar