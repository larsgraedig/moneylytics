.PHONY: publish release deploy reset-db run compose docker-build

publish:
	./gradlew :web:jib
	docker buildx build --platform linux/arm64 --push \
		-t larsu/moneylytics-frontend:$$(git rev-parse --short HEAD) \
		./frontend

docker-build:
	./gradlew :web:jibDockerBuild

compose: docker-build
	docker compose up -d

ENV ?= prod

deploy:
	helm upgrade moneylytics deployment/api \
		--namespace moneylytics-$(ENV) \
		--set image.tag=$$(git rev-parse --short HEAD) \
		--set frontend.image.tag=$$(git rev-parse --short HEAD)

release:
	./deployment/release.sh $(ENV)

run:
	docker compose up -d
	@echo "Waiting for Postgres to be ready..."
	@n=0; until docker compose exec -e PGPASSWORD=moneylytics postgres psql -h 127.0.0.1 -U moneylytics -d moneylyticsdb -c '\q' 2>/dev/null; do \
		n=$$((n+1)); \
		if [ $$n -ge 30 ]; then \
			echo "ERROR: Could not authenticate against Postgres after 30s."; \
			echo "The volume may have been initialised with different credentials — run 'make reset-db' to wipe and recreate it."; \
			exit 1; \
		fi; \
		sleep 1; \
	done
	./gradlew :web:bootRun

reset-db:
	docker compose down -v
	docker compose up -d
	@echo "Postgres reset — waiting for it to be ready..."
	@until docker compose exec -e PGPASSWORD=moneylytics postgres psql -h 127.0.0.1 -U moneylytics -d moneylyticsdb -c '\q' 2>/dev/null; do sleep 1; done
	@echo "Postgres is ready."
