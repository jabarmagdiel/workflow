package com.bpflow.config;

import com.bpflow.model.User;
import com.bpflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        log.info("Ensuring default users are up to date...");
        upsertUser("admin@bpflow.com", "Admin1234", "Admin", "System", "ADMIN");
        upsertUser("manager@bpflow.com", "Admin1234", "Manager", "User", "MANAGER");
        upsertUser("officer@bpflow.com", "Admin1234", "Officer", "User", "OFFICER");
    }

    private void upsertUser(String email, String password, String firstName, String lastName, String role) {
        User user = userRepository.findByEmail(email).orElse(new User());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setRoles(Set.of(role));
        user.setEnabled(true);
        user.setAccountNonLocked(true);
        userRepository.save(user);
    }
}
