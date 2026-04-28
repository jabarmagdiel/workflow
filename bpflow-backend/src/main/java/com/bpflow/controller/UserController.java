package com.bpflow.controller;

import com.bpflow.model.User;
import com.bpflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<User> getMe(@AuthenticationPrincipal String userId) {
        return userRepository.findById(userId)
                .map(u -> {
                    u.setPassword(null); // never expose password
                    return ResponseEntity.ok(u);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<List<User>> getAll() {
        List<User> users = userRepository.findAll();
        users.forEach(u -> u.setPassword(null));
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<User> getById(@PathVariable String id) {
        return userRepository.findById(id).map(u -> {
            u.setPassword(null);
            return ResponseEntity.ok(u);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #userId == #id")
    public ResponseEntity<User> update(@PathVariable String id,
            @RequestBody User updated,
            @AuthenticationPrincipal String userId) {
        return userRepository.findById(id).map(user -> {
            user.setFirstName(updated.getFirstName());
            user.setLastName(updated.getLastName());
            user.setPhone(updated.getPhone());
            user.setDepartment(updated.getDepartment());
            user.setPosition(updated.getPosition());
            
            User saved = userRepository.save(user);
            saved.setPassword(null); // Clear only for response
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> updateRoles(@PathVariable String id,
            @RequestBody Map<String, List<String>> body) {
        return userRepository.findById(id).map(user -> {
            user.setRoles(Set.copyOf(body.get("roles")));
            
            User saved = userRepository.save(user);
            saved.setPassword(null); // Clear only for response
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> toggleStatus(@PathVariable String id) {
        return userRepository.findById(id).map(user -> {
            user.setEnabled(!user.isEnabled());
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("enabled", (Object) user.isEnabled()));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me/fcm-token")
    public ResponseEntity<Void> updateFcmToken(@AuthenticationPrincipal String userId,
            @RequestBody Map<String, String> body) {
        String token = body.get("token");
        userRepository.findById(userId).ifPresent(user -> {
            user.setFcmToken(token);
            userRepository.save(user);
        });
        return ResponseEntity.noContent().build();
    }
}
