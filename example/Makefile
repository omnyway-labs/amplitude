all: dev

deps:
	yarn

compile:
	yarn && yarn compile

dev: .
	yarn
	shadow-cljs -A:dev watch app
clean:
	rm -rf target; rm -rf dist
	rm -rf .shadow-cljs
	rm -rf .cpcache
	rm -rf node_modules
	rm -rf public/js

codegen: js
	shadow-cljs compile script
	node target/script.js cljc
	rm -rf src/graphql

gql: .
	cat etc/schema.graphql > amplify/backend/api/amplitude/schema.graphql
	amplify api gql-compile

mock: gql
	amplify mock api
