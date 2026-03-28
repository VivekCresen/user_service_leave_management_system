package com.cresensolutions.userservice.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailServiceImpl.class);
    private static final String LOGO_CONTENT_ID = "cresenSolutionsLogo";

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    public EmailServiceImpl(JavaMailSender mailSender, MailProperties mailProperties) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
    }

    @Override
    @Async("auditTaskExecutor")
    public void sendPasswordResetOtp(String email, String fullName, String otp) {
        if (mailProperties.fromAddress().isBlank()) {
            LOGGER.warn("Mail sender is not configured. OTP for {} is {}", email, otp);
            return;
        }

        String content = """
                <p style="margin:0 0 12px;color:#475569;font-size:15px;line-height:1.7;">
                  Hello %s,
                </p>
                <p style="margin:0 0 16px;color:#475569;font-size:15px;line-height:1.7;">
                  Use the OTP below to reset your password.
                </p>
                <div style="margin:0 0 18px;padding:20px;border-radius:18px;background:linear-gradient(135deg,#ecfeff,#fff7ed);border:1px solid #dbeafe;text-align:center;">
                  <div style="font-size:12px;letter-spacing:0.18em;text-transform:uppercase;color:#0f766e;font-weight:700;margin-bottom:8px;">Password Reset OTP</div>
                  <div style="font-size:30px;letter-spacing:0.32em;color:#0f172a;font-weight:800;">%s</div>
                </div>
                <div style="padding:16px;border-radius:16px;background:#f8fafc;border:1px solid #e2e8f0;">
                  <p style="margin:0;color:#475569;font-size:14px;line-height:1.7;">
                    This OTP will expire in %d minutes. If you did not request a password reset, you can safely ignore this email.
                  </p>
                </div>
                """.formatted(fullName, otp, mailProperties.otpExpirationMinutes());

        sendHtmlEmail(email, "Cresen Solutions Password Reset OTP", "Password reset request", content);
    }

    @Override
    @Async("auditTaskExecutor")
    public void sendNewUserCreatedEmail(
            String email,
            String fullName,
            Long userId,
            String companyId,
            String username,
            String password,
            String role,
            String forgotPasswordLink
    ) {
        if (mailProperties.fromAddress().isBlank()) {
            LOGGER.warn("Mail sender is not configured. New user credentials for {} cannot be emailed.", email);
            return;
        }

        String resolvedUserId = userId == null ? "Pending" : String.valueOf(userId);
        String resetLink = (forgotPasswordLink == null || forgotPasswordLink.isBlank())
                ? mailProperties.forgotPasswordUrl()
                : forgotPasswordLink.trim();

        String content = """
                <p style="margin:0 0 12px;color:#475569;font-size:15px;line-height:1.7;">
                  Hello %s,
                </p>
                <p style="margin:0 0 18px;color:#475569;font-size:15px;line-height:1.7;">
                  Your Cresen Solutions account has been created successfully. You can sign in with the credentials below.
                </p>
                <table role="presentation" style="width:100%%;border-collapse:separate;border-spacing:0 10px;margin:0 0 18px;">
                  <tr>
                    <td style="width:38%%;padding:12px 14px;border-radius:14px 0 0 14px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">User ID</td>
                    <td style="padding:12px 14px;border-radius:0 14px 14px 0;background:#ffffff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">%s</td>
                  </tr>
                  <tr>
                    <td style="padding:12px 14px;border-radius:14px 0 0 14px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Company ID</td>
                    <td style="padding:12px 14px;border-radius:0 14px 14px 0;background:#ffffff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">%s</td>
                  </tr>
                  <tr>
                    <td style="padding:12px 14px;border-radius:14px 0 0 14px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Username</td>
                    <td style="padding:12px 14px;border-radius:0 14px 14px 0;background:#ffffff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">%s</td>
                  </tr>
                  <tr>
                    <td style="padding:12px 14px;border-radius:14px 0 0 14px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Role</td>
                    <td style="padding:12px 14px;border-radius:0 14px 14px 0;background:#ffffff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">%s</td>
                  </tr>
                  <tr>
                    <td style="padding:12px 14px;border-radius:14px 0 0 14px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Password</td>
                    <td style="padding:12px 14px;border-radius:0 14px 14px 0;background:#ffffff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;font-weight:700;">%s</td>
                  </tr>
                </table>
                <div style="margin:0 0 18px;padding:18px;border-radius:18px;background:linear-gradient(135deg,#ecfeff,#fff7ed);border:1px solid #dbeafe;">
                  <p style="margin:0 0 12px;color:#0f172a;font-size:15px;font-weight:700;">Reset your password anytime</p>
                  <p style="margin:0 0 14px;color:#475569;font-size:14px;line-height:1.7;">
                    For security, we recommend changing your password after your first login.
                  </p>
                  <a href="%s" style="display:inline-block;padding:12px 18px;border-radius:12px;background:#0f8b8d;color:#ffffff;text-decoration:none;font-size:14px;font-weight:700;">
                    Reset Password
                  </a>
                </div>
                <p style="margin:0;color:#64748b;font-size:13px;line-height:1.7;">
                  If the button does not work, copy and open this link:<br>
                  <a href="%s" style="color:#0f766e;text-decoration:none;">%s</a>
                </p>
                """.formatted(
                fullName,
                resolvedUserId,
                companyId,
                username,
                role,
                password,
                resetLink,
                resetLink,
                resetLink
        );

        sendHtmlEmail(email, "Cresen Solutions Account Created", "Your account is ready", content);
    }

    private void sendHtmlEmail(String email, String subject, String headerTitle, String contentHtml) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    true,
                    StandardCharsets.UTF_8.name()
            );

            helper.setFrom(mailProperties.fromAddress());
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(buildEmailHtml(headerTitle, contentHtml), true);
            addLogoIfAvailable(helper);

            mailSender.send(message);
        } catch (MessagingException exception) {
            LOGGER.error("Failed to prepare HTML email for {}", email, exception);
        }
    }

    private String buildEmailHtml(String headerTitle, String contentHtml) {
        String logoSection = resolveLogoMarkup();
        return """
                <!doctype html>
                <html>
                  <body style="margin:0;padding:24px;background:#eef4f8;font-family:Arial,'Helvetica Neue',sans-serif;">
                    <div style="max-width:680px;margin:0 auto;background:#ffffff;border:1px solid #dbe4ee;border-radius:28px;overflow:hidden;box-shadow:0 24px 60px -40px rgba(15,23,42,0.45);">
                      <div style="padding:28px 32px;background:linear-gradient(135deg,#f0fdfa,#fff7ed);border-bottom:1px solid #e2e8f0;">
                        %s
                        <div style="margin-top:16px;font-size:12px;letter-spacing:0.18em;text-transform:uppercase;color:#0f766e;font-weight:700;">Cresen Solutions</div>
                        <h1 style="margin:8px 0 0;color:#0f172a;font-size:28px;line-height:1.2;">%s</h1>
                      </div>
                      <div style="padding:28px 32px 30px;">
                        %s
                      </div>
                      <div style="padding:18px 32px;background:#f8fafc;border-top:1px solid #e2e8f0;color:#64748b;font-size:12px;line-height:1.7;">
                        Cresen Solutions LLC<br>
                        This is an automated email from the Leave Management System.
                      </div>
                    </div>
                  </body>
                </html>
                """.formatted(logoSection, headerTitle, contentHtml);
    }

    private String resolveLogoMarkup() {
        File logoFile = resolveLogoFile();
        if (logoFile == null) {
            return "";
        }

        return """
                <img src="cid:%s" alt="Cresen Solutions Logo" style="display:block;height:56px;width:auto;">
                """.formatted(LOGO_CONTENT_ID);
    }

    private void addLogoIfAvailable(MimeMessageHelper helper) throws MessagingException {
        File logoFile = resolveLogoFile();
        if (logoFile == null) {
            return;
        }

        helper.addInline(LOGO_CONTENT_ID, new FileSystemResource(logoFile), "image/png");
    }

    private File resolveLogoFile() {
        String logoPath = mailProperties.logoPath();
        if (logoPath == null || logoPath.isBlank()) {
            return null;
        }

        File logoFile = new File(logoPath);
        if (!logoFile.exists() || !logoFile.isFile()) {
            LOGGER.warn("Mail logo not found at {}", logoPath);
            return null;
        }

        return logoFile;
    }
}
