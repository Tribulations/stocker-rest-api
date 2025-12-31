.PHONY: build-snapshot deploy-snapshot

build-snapshot:
	@bash scripts/build-snapshot.sh

deploy-snapshot:
	@if [ -n "$(SERVER_IP)" ]; then bash scripts/deploy-snapshot.sh "$(SERVER_IP)"; else bash scripts/deploy-snapshot.sh; fi
