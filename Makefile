serve:
	cd frontend && flutter build web --dart-define=LOGIN_API=https://login.tomyedwab.localhost
	go run main.go

# TODO(tom): This is probably broken atm due to auth.
run-web:
	cd frontend && flutter run -d chrome

run-android:
	cd frontend && flutter run -d emulator-5554 --dart-define=LOGIN_API=http://10.0.2.2:8081

install-android:
	adb install -r frontend/build/app/outputs/flutter-apk/app-release.apk

deploy:
	cd frontend && flutter build web --dart-define=LOGIN_API=https://login.tomyedwab.com
	aws s3 sync ./frontend/build/web s3://yellowstone-tomyedwab-com/
	aws cloudfront create-invalidation --distribution-id E23A9QHLPGZ5TU --paths "/*"