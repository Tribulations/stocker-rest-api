.PHONY: all build-snapshot bump-minor-version deploy-snapshot deploy-snapshot-tag

all: build-snapshot deploy-snapshot-tag

build-snapshot:
	@bash scripts/build-snapshot.sh

bump-minor-version:
	@bash scripts/bump-minor-version.sh

deploy-snapshot:
	@if [ -n "$(SERVER_IP)" ]; then bash scripts/deploy-snapshot.sh "$(SERVER_IP)"; else bash scripts/deploy-snapshot.sh; fi

deploy-snapshot-tag: deploy-snapshot
	@bash scripts/tag-next-minor.sh
