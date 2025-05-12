import 'package:flutter/material.dart';
import 'package:http/http.dart' as http; // Import the http package
import 'dart:convert'; // For jsonEncode
import '../yesterday/auth.dart';

class LoginPage extends StatefulWidget {
  final VoidCallback onLoginSuccess;
  final YesterdayAuth auth;

  const LoginPage({
    super.key,
    required this.onLoginSuccess,
    required this.auth,
  });

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final _formKey = GlobalKey<FormState>(); // Add a form key
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _isLoading = false; // To manage loading state

  @override
  void dispose() {
    _usernameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _login() async {
    if (_formKey.currentState?.validate() ?? false) {
      setState(() {
        _isLoading = true;
      });

      try {
        final success = await widget.auth.attemptLogin(
          _usernameController.text,
          _passwordController.text,
        );
        if (success) {
          widget.onLoginSuccess();
        } else {
          // If the server did not return a 200 OK response,
          // then throw an exception.
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Login failed')),
          );
        }
      } catch (e) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error: $e')),
        );
      } finally {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Login'),
      ),
      body: Center(
        // Center the form on the page
        child: ConstrainedBox(
          // Apply a maximum width
          constraints:
              const BoxConstraints(maxWidth: 400), // Adjust maxWidth as needed
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Form(
              key: _formKey,
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center, // Center the form
                crossAxisAlignment:
                    CrossAxisAlignment.stretch, // Stretch buttons
                children: <Widget>[
                  TextFormField(
                    controller: _usernameController,
                    decoration: const InputDecoration(
                      labelText: 'Username',
                      border: OutlineInputBorder(), // Add a border
                    ),
                    validator: (value) {
                      if (value == null || value.isEmpty) {
                        return 'Please enter your username';
                      }
                      return null;
                    },
                  ),
                  const SizedBox(height: 16.0), // Add spacing
                  TextFormField(
                    controller: _passwordController,
                    decoration: const InputDecoration(
                      labelText: 'Password',
                      border: OutlineInputBorder(),
                    ),
                    obscureText: true, // Hide password
                    validator: (value) {
                      if (value == null || value.isEmpty) {
                        return 'Please enter your password';
                      }
                      return null;
                    },
                  ),
                  const SizedBox(height: 24.0), // Add more spacing
                  _isLoading
                      ? const Center(
                          child: CircularProgressIndicator()) // Show loader
                      : ElevatedButton(
                          onPressed: _login,
                          style: ElevatedButton.styleFrom(
                            // Style the button
                            padding: const EdgeInsets.symmetric(vertical: 16.0),
                          ),
                          child: const Text('Login'),
                        ),
                ],
              ),
            ),
          ),
        ), // Closing parenthesis for ConstrainedBox
      ), // Closing parenthesis for Center
    );
  }
}
