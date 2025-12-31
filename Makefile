.PHONY: build-snapshot deploy-snapshot deploy-snapshot-tag

build-snapshot:
	@bash scripts/build-snapshot.sh

deploy-snapshot:
	@if [ -n "$(SERVER_IP)" ]; then bash scripts/deploy-snapshot.sh "$(SERVER_IP)"; else bash scripts/deploy-snapshot.sh; fi

deploy-snapshot-tag: deploy-snapshot
	@bash scripts/tag-next-minor.sh
