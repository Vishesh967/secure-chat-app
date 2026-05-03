package com.securechat.securemessaging.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Value("${app.otp.from-name:SecureChat}")
    private String fromName;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendOtpEmail(String toEmail, String username, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(toEmail);
            helper.setSubject("Your SecureChat verification code");
            helper.setText(buildOtpHtml(username, otp), true);
            mailSender.send(message);
            log.info("OTP email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("SMTP failed for {}: {}", toEmail, e.getMessage(), e);
        }
    }

    private String buildOtpHtml(String username, String otp) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8"/>
              <style>
                body { font-family: 'Segoe UI', Arial, sans-serif; background:#f4f4f4; margin:0; padding:0; }
                .container { max-width:520px; margin:40px auto; background:#fff;
                             border-radius:12px; overflow:hidden;
                             box-shadow:0 4px 20px rgba(0,0,0,.08); }
                .header { background:#2e7d32; padding:32px 40px; text-align:center; }
                .header h1 { color:#fff; margin:0; font-size:1.6rem; letter-spacing:-0.5px; }
                .header p  { color:rgba(255,255,255,.8); margin:6px 0 0; font-size:.9rem; }
                .body { padding:36px 40px; }
                .body p { color:#374151; line-height:1.6; margin:0 0 16px; }
                .otp-box { background:#f0fdf4; border:2px solid #86efac;
                           border-radius:10px; padding:20px; text-align:center; margin:24px 0; }
                .otp-code { font-size:2.4rem; font-weight:700; letter-spacing:10px;
                            color:#166534; font-family:monospace; }
                .expiry { font-size:.82rem; color:#6b7280; margin-top:8px; }
                .footer { background:#f9fafb; padding:20px 40px; text-align:center;
                          font-size:.78rem; color:#9ca3af; border-top:1px solid #e5e7eb; }
              </style>
            </head>
            <body>
              <div class="container">
                <div class="header">
                  <h1>&#128274; SecureChat</h1>
                  <p>End-to-end encrypted messaging</p>
                </div>
                <div class="body">
                  <p>Hi <strong>%s</strong>,</p>
                  <p>Use the code below to verify your email address and activate your account.</p>
                  <div class="otp-box">
                    <div class="otp-code">%s</div>
                    <div class="expiry">Expires in 5 minutes</div>
                  </div>
                  <p>If you did not create a SecureChat account, you can safely ignore this email.</p>
                </div>
                <div class="footer">
                  &copy; SecureChat &mdash; This is an automated message, please do not reply.
                </div>
              </div>
            </body>
            </html>
            """.formatted(username, otp);
    }
}
