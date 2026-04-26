package com.bpflow.service;

import com.bpflow.dto.AuthDTOs;
import com.bpflow.model.AuditLog;
import com.bpflow.model.User;
import com.bpflow.repository.AuditLogRepository;
import com.bpflow.repository.UserRepository;
import com.bpflow.security.JwtTokenProvider;
import com.bpflow.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final NotificationService notificationService;

    @Value("${jwt.expiration-ms}")
    private long jwtExpirationMs;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 30;

    // ─── LOGIN ───────────────────────────────────────────────────
    public AuthDTOs.AuthResponse login(AuthDTOs.LoginRequest request, String ipAddress) {
        var user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // Check if account is locked
        if (!user.isAccountNonLocked()) {
            if (user.getLockedUntil() != null && LocalDateTime.now().isBefore(user.getLockedUntil())) {
                throw new LockedException("Account is locked until " + user.getLockedUntil());
            } else {
                // Unlock automatically
                user.setAccountNonLocked(true);
                user.setFailedLoginAttempts(0);
            }
        }

        try {
            log.info("Attempting authentication for user: {}", request.getEmail());
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
            log.info("Authentication successful for user: {}", request.getEmail());
        } catch (BadCredentialsException e) {
            log.error("Authentication failed for user: {} - Bad Credentials", request.getEmail());
            handleFailedLogin(user);
            throw new BadCredentialsException("Invalid email or password");
        } catch (Exception e) {
            log.error("Authentication failed for user: {} - Error: {}", request.getEmail(), e.getMessage());
            throw e;
        }

        // Reset failed attempts on success
        user.setFailedLoginAttempts(0);
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ipAddress);

        UserPrincipal principal = new UserPrincipal(user);
        String accessToken = jwtTokenProvider.generateAccessToken(principal);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        user.setRefreshToken(passwordEncoder.encode(refreshToken));
        user.setRefreshTokenExpiry(LocalDateTime.now().plusDays(1));
        userRepository.save(user);

        // Audit
        audit(user.getId(), user.getEmail(), "LOGIN", "USER", user.getId(), ipAddress, true, null);

        return buildAuthResponse(accessToken, refreshToken, user);
    }

    // ─── REGISTER ────────────────────────────────────────────────
    public AuthDTOs.AuthResponse register(AuthDTOs.RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        String role = (request.getRole() != null) ? request.getRole().toUpperCase() : "CLIENT";

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .department(request.getDepartment())
                .position(request.getPosition())
                .roles(Set.of(role))
                .enabled(true)
                .build();

        userRepository.save(user);

        UserPrincipal principal = new UserPrincipal(user);
        String accessToken = jwtTokenProvider.generateAccessToken(principal);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        user.setRefreshToken(passwordEncoder.encode(refreshToken));
        user.setRefreshTokenExpiry(LocalDateTime.now().plusDays(1));
        userRepository.save(user);

        audit(user.getId(), user.getEmail(), "REGISTER", "USER", user.getId(), null, true, null);
        return buildAuthResponse(accessToken, refreshToken, user);
    }

    // ─── REFRESH TOKEN ───────────────────────────────────────────
    public AuthDTOs.AuthResponse refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        String userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getRefreshTokenExpiry() == null || LocalDateTime.now().isAfter(user.getRefreshTokenExpiry())) {
            throw new IllegalArgumentException("Refresh token expired");
        }

        UserPrincipal principal = new UserPrincipal(user);
        String newAccessToken = jwtTokenProvider.generateAccessToken(principal);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        user.setRefreshToken(passwordEncoder.encode(newRefreshToken));
        user.setRefreshTokenExpiry(LocalDateTime.now().plusDays(1));
        userRepository.save(user);

        return buildAuthResponse(newAccessToken, newRefreshToken, user);
    }

    // ─── LOGOUT ──────────────────────────────────────────────────
    public void logout(String userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setRefreshToken(null);
            user.setRefreshTokenExpiry(null);
            userRepository.save(user);
            audit(user.getId(), user.getEmail(), "LOGOUT", "USER", user.getId(), null, true, null);
        });
    }

    // ─── FORGOT PASSWORD ─────────────────────────────────────────
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            user.setResetPasswordToken(token);
            user.setResetPasswordExpiry(LocalDateTime.now().plusHours(2));
            userRepository.save(user);
            // In production: send email with reset link
            log.info("Password reset token for {}: {}", email, token);
            notificationService.sendPasswordResetEmail(email, token);
        });
    }

    // ─── RESET PASSWORD ──────────────────────────────────────────
    public void resetPassword(AuthDTOs.ResetPasswordRequest request) {
        var user = userRepository.findByResetPasswordToken(request.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired token"));

        if (LocalDateTime.now().isAfter(user.getResetPasswordExpiry())) {
            throw new IllegalArgumentException("Reset token has expired");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setResetPasswordToken(null);
        user.setResetPasswordExpiry(null);
        userRepository.save(user);

        audit(user.getId(), user.getEmail(), "RESET_PASSWORD", "USER", user.getId(), null, true, null);
    }

    // ─── CHANGE PASSWORD ─────────────────────────────────────────
    public void changePassword(String userId, AuthDTOs.ChangePasswordRequest request) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        audit(user.getId(), user.getEmail(), "CHANGE_PASSWORD", "USER", user.getId(), null, true, null);
    }

    // ─── HELPERS ─────────────────────────────────────────────────
    private void handleFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setAccountNonLocked(false);
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
            log.warn("Account locked for user {} after {} failed attempts", user.getEmail(), attempts);
        }
        userRepository.save(user);
    }

    private AuthDTOs.AuthResponse buildAuthResponse(String accessToken, String refreshToken, User user) {
        var userInfo = new AuthDTOs.UserInfo();
        userInfo.setId(user.getId());
        userInfo.setEmail(user.getEmail());
        userInfo.setFirstName(user.getFirstName());
        userInfo.setLastName(user.getLastName());
        userInfo.setRoles(user.getRoles().stream().toList());
        userInfo.setDepartment(user.getDepartment());

        var response = new AuthDTOs.AuthResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setExpiresIn(jwtExpirationMs / 1000);
        response.setUser(userInfo);
        return response;
    }

    private void audit(String userId, String email, String action, String resType,
            String resId, String ip, boolean success, String detail) {
        auditLogRepository.save(AuditLog.builder()
                .userId(userId).userEmail(email).action(action)
                .resourceType(resType).resourceId(resId)
                .ipAddress(ip).success(success).detail(detail)
                .build());
    }
}
