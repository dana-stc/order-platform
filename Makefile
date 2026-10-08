SHELL := /bin/bash
ARCH  := $(shell uname -m | sed -e 's/x86_64/amd64/' -e 's/aarch64/arm64/')
MVN   := ./mvnw -B -ntp
SERVICES := api-gateway order-service inventory-service payment-service notification-service

.DEFAULT_GOAL := help
.PHONY: help build test infra-up infra-down images apps-up apps-down demo load-test \
        kind-up k8s-local k8s-observability k8s-down

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'

build: ## Compile + unit tests (+ Testcontainers tests when Docker is available)
	$(MVN) verify

test: ## Unit tests only
	$(MVN) test

# ---- local development (docker compose) -----------------------------------------------------------------------
infra-up: ## Start postgres, kafka, prometheus, tempo, grafana (run services with: ./mvnw -pl order-service spring-boot:run)
	docker compose up -d

infra-down: ## Stop infrastructure and delete its volumes
	docker compose --profile apps down -v

images: ## Build the 5 service images into the local Docker daemon (Jib, no Dockerfile)
	$(MVN) -DskipTests -Djib.arch=$(ARCH) package jib:dockerBuild -pl $(shell echo $(SERVICES) | tr ' ' ',') -am

apps-up: images ## Run everything (infra + 5 services) with docker compose
	docker compose --profile apps up -d

apps-down: ## Stop everything
	docker compose --profile apps down

demo: ## Run the demo scenarios against the gateway (localhost:8080)
	./scripts/demo.sh

load-test: ## k6 load test (needs k6: brew install k6)
	TOKEN=$$(./scripts/token.sh load-test 7200) k6 run load-test/orders.js

# ---- Kubernetes (kind) -------------------------------------------------------------------------------------
kind-up: ## Create the local kind cluster
	kind create cluster --config k8s/kind-cluster.yaml

k8s-local: images ## Build images, load them into kind and deploy the local overlay
	for s in $(SERVICES); do kind load docker-image order-platform/$$s:local --name order-platform; done
	kubectl apply -k k8s/overlays/local
	kubectl -n order-platform rollout status deploy --timeout=300s

k8s-observability: ## Install Prometheus + Grafana + Tempo into the cluster (needs helm)
	./k8s/observability/install.sh

k8s-down: ## Delete the kind cluster
	kind delete cluster --name order-platform
