package com.securechat.securemessaging.service;

import com.securechat.securemessaging.model.User;
import com.securechat.securemessaging.repository.UserRepository;
import com.securechat.securemessaging.security.DHUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder encoder;
    private final EmailService emailService;

    @Value("${app.otp.expiry-minutes:5}")
    private int otpExpiryMinutes;

    private static final SecureRandom RANDOM = new SecureRandom();

    public UserService(UserRepository userRepository,
                       BCryptPasswordEncoder encoder,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.encoder = encoder;
        this.emailService = emailService;
    }

    public User registerUser(String username, String email, String password) {
        String normalEmail = email.toLowerCase();

        // ✅ FIXED
        User existingByUsername = userRepository.findByUsername(username).orElse(null);
        if (existingByUsername != null && existingByUsername.isEmailVerified()) {
            throw new RuntimeException("Username already taken");
        }

        // already correct
        User existingByEmail = userRepository.findByEmail(normalEmail).orElse(null);
        if (existingByEmail != null && existingByEmail.isEmailVerified()) {
            throw new RuntimeException("Email already registered");
        }

        User user;
        if (existingByUsername != null && !existingByUsername.isEmailVerified()) {
            user = existingByUsername;
        } else if (existingByEmail != null && !existingByEmail.isEmailVerified()) {
            user = existingByEmail;
        } else {
            user = new User();
        }

        user.setUsername(username);
        user.setEmail(normalEmail);
        user.setPasswordHash(encoder.encode(password));
        user.setEmailVerified(false);

        try {
            KeyPair keyPair = DHUtil.generateKeyPair();
            user.setPublicKey(DHUtil.publicKeyToString(keyPair.getPublic()).getBytes()); // match byte[]
        } catch (Exception e) {
            throw new RuntimeException("Key generation failed");
        }

        String otp = generateOtp();
        user.setOtpCode(otp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(otpExpiryMinutes));

        User saved = userRepository.save(user);
        emailService.sendOtpEmail(saved.getEmail(), saved.getUsername(), otp);
        return saved;
    }

    public User verifyOtp(String email, String otp) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new RuntimeException("No account found"));

        if (user.isEmailVerified()) {
            throw new RuntimeException("Already verified");
        }

        if (user.getOtpCode() == null || user.getOtpExpiry() == null) {
            throw new RuntimeException("No OTP found");
        }

        if (LocalDateTime.now().isAfter(user.getOtpExpiry())) {
            throw new RuntimeException("OTP expired");
        }

        if (!user.getOtpCode().equals(otp.trim())) {
            throw new RuntimeException("Incorrect OTP");
        }

        user.setEmailVerified(true);
        user.setOtpCode(null);
        user.setOtpExpiry(null);
        return userRepository.save(user);
    }

    public void resendOtp(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new RuntimeException("No account found"));

        if (user.isEmailVerified()) {
            throw new RuntimeException("Already verified");
        }

        String otp = generateOtp();
        user.setOtpCode(otp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(otpExpiryMinutes));
        userRepository.save(user);

        emailService.sendOtpEmail(user.getEmail(), user.getUsername(), otp);
    }

    public User loginUser(String username, String password) {
        // ✅ FIXED
        User user = userRepository.findByUsername(username).orElse(null);

        if (user == null || !encoder.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("Invalid username or password");
        }

        if (!user.isEmailVerified()) {
            throw new RuntimeException("Verify email first");
        }

        return user;
    }

    public byte[] getPublicKey(String username) {
        // ✅ FIXED
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) throw new RuntimeException("User not found");
        return user.getPublicKey();
    }

    public User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public User updatePublicKey(String username, String publicKey) {
        User user = getUser(username);
        user.setPublicKey(java.util.Base64.getDecoder().decode(publicKey));
        return userRepository.save(user);
    }

    public boolean userExists(String username) {
        return userRepository.existsByUsername(username);
    }

    private String generateOtp() {
        int code = 100000 + RANDOM.nextInt(900000);
        return String.valueOf(code);
    }
}
