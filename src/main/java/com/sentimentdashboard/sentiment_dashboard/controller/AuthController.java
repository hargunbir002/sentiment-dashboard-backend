package com.sentimentdashboard.sentiment_dashboard.controller;

import com.sentimentdashboard.sentiment_dashboard.entity.User;
import com.sentimentdashboard.sentiment_dashboard.repository.UserRepository;
import com.sentimentdashboard.sentiment_dashboard.service.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public record RegisterRequest(String username, String email, String password) {}
    public record LoginRequest(String username, String password) {}

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest r) {
        if (r.username() == null || r.username().isBlank() || r.email() == null
                || !r.email().contains("@") || r.password() == null || r.password().length() < 6) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Username, valid email and password (min 6 chars) required"));
        }
        if (users.existsByUsername(r.username())) {
            return ResponseEntity.status(409).body(Map.of("message", "Username already taken"));
        }
        if (users.existsByEmail(r.email())) {
            return ResponseEntity.status(409).body(Map.of("message", "Email already registered"));
        }
        User u = new User();
        u.setUsername(r.username().trim());
        u.setEmail(r.email().trim().toLowerCase());
        u.setPassword(encoder.encode(r.password()));
        users.save(u);
        return ResponseEntity.ok(Map.of("token", jwt.generate(u.getUsername()),
                "username", u.getUsername(), "email", u.getEmail()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest r) {
        var found = r.username() == null ? java.util.Optional.<User>empty() : users.findByUsername(r.username().trim());
        if (found.isEmpty() || r.password() == null || !encoder.matches(r.password(), found.get().getPassword())) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid username or password"));
        }
        User u = found.get();
        return ResponseEntity.ok(Map.of("token", jwt.generate(u.getUsername()),
                "username", u.getUsername(), "email", u.getEmail()));
    }
}