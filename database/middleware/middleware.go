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
		if r.Method == "OPTIONS" {
			w.WriteHeader(http.StatusOK)
			// Do not call through to the handler itself, just return immediately
			return
		}

		secret := os.Getenv("CLOUDFRONT_SECRET")
		if secret == "" {
			log.Fatal("CLOUDFRONT_SECRET environment variable not set")
		}

		if r.Header.Get("X-CloudFront-Secret") != secret {
			http.Error(w, "Unauthorized", http.StatusUnauthorized)
			fmt.Printf("%s - %s %s UNAUTHORIZED\n",
				r.RemoteAddr,
				r.Method,
				r.URL.Path,
			)
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
		if os.Getenv("ENABLE_CROSS_ORIGIN") != "" {
			fmt.Printf("%s - %s %s Enable Cross Origin\n",
				r.RemoteAddr,
				r.Method,
				r.URL.Path,
			)
			w.Header().Set("Access-Control-Allow-Origin", "*")
			w.Header().Set("Access-Control-Allow-Headers", "Content-Type, X-CloudFront-Secret")
		}
		next.ServeHTTP(w, r)
	}
}

// Combine multiple middleware functions
func Chain(h http.HandlerFunc, middleware ...func(http.HandlerFunc) http.HandlerFunc) http.HandlerFunc {
	for _, m := range middleware {
		h = m(h)
	}
	return h
}

// Apply the default middlewars in the correct order
func ApplyDefault(h http.HandlerFunc) http.HandlerFunc {
	return Chain(
		h,
		RequireCloudFrontSecret,
		EnableCrossOrigin,
		LogRequests,
	)
}
