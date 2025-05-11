import 'package:flutter/material.dart';
import 'package:http/http.dart' as http; // Import the http package
import 'dart:convert'; // For jsonEncode
import 'dart:io'; // For Cookie parsing
import 'package:flutter_secure_storage/flutter_secure_storage.dart'; // Import flutter_secure_storage

class LoginPage extends StatefulWidget {
  final VoidCallback onLoginSuccess;

  const LoginPage({
    super.key,
    required this.onLoginSuccess,
  });

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final _formKey = GlobalKey<FormState>(); // Add a form key
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _isLoading = false; // To manage loading state

  // Create storage
  final _storage = const FlutterSecureStorage(
    aOptions: AndroidOptions(
      encryptedSharedPreferences: true,
    ),
  );

  static const String _loginApi = String.fromEnvironment("LOGIN_API");
  static const String _loginUrl = '$_loginApi/api/login?app=0001-0003';

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
        print(
            'Logging in with username: ${_usernameController.text} to $_loginUrl');
        final response = await http.post(
          Uri.parse(_loginUrl),
          headers: <String, String>{
            'Content-Type': 'application/json; charset=UTF-8',
          },
          body: jsonEncode(<String, String>{
            'username': _usernameController.text,
            'password': _passwordController.text,
          }),
        );

        if (response.statusCode == 200) {
          // If the server returns a 200 OK response,
          // then parse the returned cookie.
          final setCookieHeader = response.headers['set-cookie'];
          if (setCookieHeader != null) {
            // Split the header string in case multiple cookies are present
            // This regex splits on commas that are likely delimiters between cookies
            final cookieStrings =
                setCookieHeader.split(RegExp(r',(?=[^ ;]+=)'));
            String? yrtCookieValue;

            for (final cookieStr in cookieStrings) {
              try {
                final cookie = Cookie.fromSetCookieValue(cookieStr.trim());
                if (cookie.name == 'YRT') {
                  // Assuming the cookie name is YRT
                  yrtCookieValue = cookie.value;
                  break;
                }
              } catch (e) {
                // Log parsing error or handle as needed
                print('Error parsing cookie string: $cookieStr. Error: $e');
              }
            }

            if (yrtCookieValue != null) {
              print('YRT Cookie Value: $yrtCookieValue');
              await _storage.write(key: 'yrt_cookie', value: yrtCookieValue);
              print('YRT Cookie saved to secure storage');
            } else {
              print('YRT cookie not found in Set-Cookie header');
            }
          }
          widget.onLoginSuccess();
        } else {
          // If the server did not return a 200 OK response,
          // then throw an exception.
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Login failed: ${response.reasonPhrase}')),
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
