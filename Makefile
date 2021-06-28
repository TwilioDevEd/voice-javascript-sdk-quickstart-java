.PHONY: install serve

install:
	mvn package
	cd src/main/resources/public && npm install

serve:
	java -jar target/client-quickstart-1.0-SNAPSHOT.jar
