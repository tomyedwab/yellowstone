module tomyedwab.com/yellowstone-server

go 1.23.4

require (
	github.com/jmoiron/sqlx v1.4.0
	github.com/mattn/go-sqlite3 v1.14.22
	github.com/tomyedwab/yesterday v1.0.3
)

require github.com/golang-jwt/jwt/v5 v5.2.2 // indirect

replace github.com/tomyedwab/yesterday => ../../yesterday
