package com.securechat.securemessaging.service;

import com.securechat.securemessaging.model.User;
import com.securechat.securemessaging.repository.UserRepository;
import com.securechat.securemessaging.security.DHUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class UserService {

    private final UserRepository    userRepository;
    private final BCryptPasswordEncoder encoder;
    private final EmailService      emailService;

    @Value("${app.otp.expiry-minutes:5}")
    private int otpExpiryMinutes;

    private static final SecureRandom RANDOM = new SecureRandom();

    public UserService(UserRepository userRepository,
                       BCryptPasswordEncoder encoder,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.encoder        = encoder;
        this.emailService   = emailService;
    }

    // ── Register (step 1 of 2) ────────────────────────────────

    /**
     * Creates (or overwrites an unverified) account and sends an OTP.
     *
     * Rules:
     *  - If a VERIFIED account already has this username → reject "Username already taken"
     *  - If a VERIFIED account already has this email    → reject "Email already registered"
     *  - If an UNVERIFIED account exists with this username or email → overwrite it
     *    (user may have mistyped their email and is retrying)
     */
    public User registerUser(String username, String email, String password) {
        String normalEmail = email.toLowerCase();

        // Block only verified duplicates
        User existingByUsername = userRepository.findByUsername(username);
        if (existingByUsername != null && existingByUsername.isEmailVerified()) {
            throw new RuntimeException("Username already taken");
        }

        User existingByEmail = userRepository.findByEmail(normalEmail).orElse(null);
        if (existingByEmail != null && existingByEmail.isEmailVerified()) {
            throw new RuntimeException("Email already registered");
        }

        // Reuse or create the user record
        // If an unverified record exists for this username or email, overwrite it
        User user = null;
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
            user.setPublicKey(DHUtil.publicKeyToString(keyPair.getPublic()));
        } catch (Exception e) {
            throw new RuntimeException("Key generation failed — please try again");
        }

        String otp = generateOtp();
        user.setOtpCode(otp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(otpExpiryMinutes));

        User saved = userRepository.save(user);
        emailService.sendOtpEmail(saved.getEmail(), saved.getUsername(), otp);
        return saved;
    }

    // ── Verify OTP (step 2 of 2) ──────────────────────────────

    public User verifyOtp(String email, String otp) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new RuntimeException("No account found for this email"));

        if (user.isEmailVerified()) {
            throw new RuntimeException("Email is already verified");
        }
        if (user.getOtpCode() == null || user.getOtpExpiry() == null) {
            throw new RuntimeException("No OTP found — please request a new one");
        }
        if (LocalDateTime.now().isAfter(user.getOtpExpiry())) {
            throw new RuntimeException("OTP has expired — please request a new one");
        }
        if (!user.getOtpCode().equals(otp.trim())) {
            throw new RuntimeException("Incorrect OTP");
        }

        user.setEmailVerified(true);
        user.setOtpCode(null);
        user.setOtpExpiry(null);
        return userRepository.save(user);
    }

    // ── Resend OTP ────────────────────────────────────────────

    public void resendOtp(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new RuntimeException("No account found for this email"));

        if (user.isEmailVerified()) {
            throw new RuntimeException("Email is already verified");
        }

        String otp = generateOtp();
        user.setOtpCode(otp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(otpExpiryMinutes));
        userRepository.save(user);

        emailService.sendOtpEmail(user.getEmail(), user.getUsername(), otp);
    }

    // ── Login ─────────────────────────────────────────────────

    public User loginUser(String username, String password) {
        User user = userRepository.findByUsername(username);

        if (user == null || !encoder.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("Invalid username or password");
        }
        if (!user.isEmailVerified()) {
            throw new RuntimeException("Please verify your email before logging in");
        }

        return user;
    }

    // ── Helpers ───────────────────────────────────────────────

    public String getPublicKey(String username) {
        User user = userRepository.findByUsername(username);
        if (user == null) throw new RuntimeException("User not found");
        return user.getPublicKey();
    }

    public boolean userExists(String username) {
        return userRepository.existsByUsername(username);
    }

    private String generateOtp() {
        // Cryptographically random 6-digit OTP
        int code = 100000 + RANDOM.nextInt(900000);
        return String.valueOf(code);
    }
}
