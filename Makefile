.DEFAULT_GOAL := help

.PHONY: help
help: ## show this help
	@awk 'BEGIN {FS = ":.*##"; print "usage: make <target>\n"} \
		/^##@/ { printf "\n\033[1m%s:\033[0m\n", substr($$0, 5); next } \
		/^[a-zA-Z0-9_.-]+:.*##/ { printf "  \033[36m%-22s\033[0m %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

##@ Build
.PHONY: build test release publish publish-local
build: ## check formatting, compile with error prone and nullaway, test, check declared dependencies; TESTS_JDK=21 tests on that JDK
	./gradlew build buildHealth $(if $(TESTS_JDK),-PtestsJdk=$(TESTS_JDK))

test: ## run the tests
	./gradlew test

release: ## tag origin/main, asking which version, wait for release.yml to publish it, write the release page; TAG=v0.1.0 answers
	./bin/release.sh

publish: ## build, sign and publish the jars to Maven Central, as the release workflow does; RELEASE_VERSION=v0.1.0 releases
	./gradlew build publishToMavenCentral $(if $(RELEASE_VERSION),-PreleaseVersion=$(RELEASE_VERSION:v%=%))

publish-local: ## install the jars into ~/.m2, to try them in another project
	./gradlew publishToMavenLocal

##@ Code style
.PHONY: format lint scripts-test
format: ## reformat java and gradle files in place
	./gradlew spotlessApply

lint: ## fail on formatting that make format would change
	./gradlew spotlessCheck

scripts-test: ## shellcheck every script, run every *.test.sh
	mise exec shellcheck -- shellcheck $$(git ls-files '*.sh')
	@rc=0; for t in $$(git ls-files '*.test.sh'); do echo "== $$t"; "./$$t" || { echo "FAIL $$t"; rc=1; }; done; exit $$rc
