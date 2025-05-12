serve:
	cd frontend && flutter build web --dart-define=LOGIN_API=https://login.tomyedwab.localhost --dart-define=APP_API=https://yellowstone.tomyedwab.localhost
	go run main.go

# TODO(tom): This is probably broken atm due to auth.
run-web:
	cd frontend && flutter run -d chrome

run-android:
	cd frontend && flutter run -d emulator-5554 --dart-define=LOGIN_API=http://10.0.2.2:8081 --dart-define=APP_API=http://10.0.2.2:8334

install-android:
	cd frontend && flutter build apk --dart-define=LOGIN_API=https://login.tomyedwab.com --dart-define=APP_API=https://yellowstone.tomyedwab.com
	adb install -r frontend/build/app/outputs/flutter-apk/app-release.apk

deploy:
	cd frontend && flutter build web --dart-define=LOGIN_API=https://login.tomyedwab.com --dart-define=APP_API=https://yellowstone.tomyedwab.com
	aws s3 sync ./frontend/build/web s3://yellowstone-tomyedwab-com/
	aws cloudfront create-invalidation --distribution-id E23A9QHLPGZ5TU --paths "/*"