serve:
	ENABLE_CROSS_ORIGIN=1 go run main.go

run-web:
	cd frontend && flutter run -d chrome

run-android:
	cd frontend && flutter run -d emulator-5554

build-web:
	cd frontend && flutter build web

build-android:
	cd frontend && flutter build apk

build-server:
	go build -o yellowstone-server main.go

deploy: build-web
	aws s3 sync ./frontend/build/web s3://yellowstone-tomyedwab-com/
	aws cloudfront create-invalidation --distribution-id E23A9QHLPGZ5TU --paths "/*"