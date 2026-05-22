.PHONY: publish release

publish:
	./gradlew :api:jib

release:
	./deployment/release.sh

