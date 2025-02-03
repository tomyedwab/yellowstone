serve:
	ENABLE_CROSS_ORIGIN=1 go run main.go

run-web:
	cd frontend && flutter run -d chrome

run-android:
	cd frontend && flutter run -d emulator-5554

install-android:
	adb install -r frontend/build/app/outputs/flutter-apk/app-release.apk

deploy:
	aws s3 sync ./frontend/build/web s3://yellowstone-tomyedwab-com/
	aws cloudfront create-invalidation --distribution-id E23A9QHLPGZ5TU --paths "/*"

# Auth comes from here: https://github.com/experoinc/aws-lambda-edge-oauth