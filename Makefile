serve:
	ENABLE_CROSS_ORIGIN=1 CLOUDFRONT_SECRET=123 go run main.go

run-web:
	cd frontend && flutter run -d chrome

run-android:
	cd frontend && flutter run -d emulator-5554

build-web:
	cd frontend && flutter build web

build-android:
	cd frontend && flutter build apk

install-android: build-android
	adb install -r frontend/build/app/outputs/flutter-apk/app-release.apk

deploy: build-web
	aws s3 sync ./frontend/build/web s3://yellowstone-tomyedwab-com/
	aws cloudfront create-invalidation --distribution-id E23A9QHLPGZ5TU --paths "/*"

push-server-image:
	docker build -t tomyedwab/yellowstone-arm64 -f Dockerfile.arm64 .
	docker push tomyedwab/yellowstone-arm64

# Auth comes from here: https://github.com/experoinc/aws-lambda-edge-oauth