package middleware

import (
	"fmt"
	"log"
	"net/http"
	"os"
	"time"
)

func RequireCloudFrontSecret(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		secret := os.Getenv("CLOUDFRONT_SECRET")
		if secret == "" {
			log.Fatal("CLOUDFRONT_SECRET environment variable not set")
		}

		if r.Header.Get("X-CloudFront-Secret") != secret {
			http.Error(w, "Unauthorized", http.StatusUnauthorized)
			return
		}

		next.ServeHTTP(w, r)
	}
}

func LogRequests(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()

		// Call the next handler
		next.ServeHTTP(w, r)

		// Log the request details
		duration := time.Since(start)
		fmt.Printf("%s - %s %s %s - %v\n",
			r.RemoteAddr,
			r.Method,
			r.URL.Path,
			r.Proto,
			duration,
		)
	}
}

func EnableCrossOrigin(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		next.ServeHTTP(w, r)

		if os.Getenv("ENABLE_CROSS_ORIGIN") != "" {
			w.Header().Set("Access-Control-Allow-Origin", "*")
		}
	}
}

// Combine multiple middleware functions
func Chain(h http.HandlerFunc, middleware ...func(http.HandlerFunc) http.HandlerFunc) http.HandlerFunc {
	for _, m := range middleware {
		h = m(h)
	}
	return h
}
