package com.cresensolutions.userservice.service.Impl;

import com.cresensolutions.userservice.common.StringUtils;
import com.cresensolutions.userservice.common.UserConstants;
import com.cresensolutions.userservice.model.EmailTemplate;
import com.cresensolutions.userservice.repository.EmailTemplateRepository;
import com.cresensolutions.userservice.service.EmailService;
import com.cresensolutions.userservice.service.MailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final EmailTemplateRepository emailTemplateRepo;

    public EmailServiceImpl(JavaMailSender mailSender,
                            MailProperties mailProperties,
                            EmailTemplateRepository emailTemplateRepo) {
        this.mailSender        = mailSender;
        this.mailProperties    = mailProperties;
        this.emailTemplateRepo = emailTemplateRepo;
    }

    @Override
    @Async("auditTaskExecutor")
    public void sendPasswordResetOtp(String email, String fullName, String otp) {
        sendTemplatedEmail(UserConstants.TMPL_PASSWORD_RESET, email, "Password Reset Request",
                Map.of("fullName", safe(fullName),
                       "otp", safe(otp),
                       "otpExpiry", String.valueOf(mailProperties.otpExpirationMinutes())));
    }

    @Override
    @Async("auditTaskExecutor")
    public void sendNewUserCreatedEmail(String email, String fullName, Long userId,
            String companyId, String username, String role, String forgotPasswordLink) {
        String resetLink = (forgotPasswordLink == null || forgotPasswordLink.isBlank())
                ? mailProperties.forgotPasswordUrl() : forgotPasswordLink.trim();
        sendTemplatedEmail(UserConstants.TMPL_USER_CREATED, email, "Your Account Is Ready",
                Map.of("fullName", safe(fullName),
                       "userId", userId == null ? "Pending" : String.valueOf(userId),
                       "companyId", safe(companyId),
                       "username", safe(username),
                       "role", safe(role),
                       "resetLink", safe(resetLink)));
    }

    @Override
    @Async("auditTaskExecutor")
    public void sendUserDeletedEmail(String email, String fullName, String username,
            String role, String deletedByUsername, String deletedByRole) {
        sendTemplatedEmail(UserConstants.TMPL_USER_DELETED, email, "Account Removed",
                Map.of("fullName", safe(fullName),
                       "username", safe(username),
                       "role", safe(role),
                       "deletedBy", safe(deletedByUsername),
                       "deletedByRole", safe(deletedByRole)));
    }

    @Override
    @Async("auditTaskExecutor")
    public void sendUserRoleChangedEmail(String email, String fullName, String username,
            String previousRole, String newRole, String changedByUsername,
            String changedByRole, String loginUrl) {
        String resolvedLoginUrl = (loginUrl == null || loginUrl.isBlank())
                ? mailProperties.loginUrl() : loginUrl.trim();
        sendTemplatedEmail(UserConstants.TMPL_ROLE_CHANGED, email, "Your Role Has Changed",
                Map.of("fullName", safe(fullName),
                       "username", safe(username),
                       "previousRole", safe(previousRole),
                       "newRole", safe(newRole),
                       "changedBy", safe(changedByUsername),
                       "changedByRole", safe(changedByRole),
                       "loginUrl", safe(resolvedLoginUrl)));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void sendTemplatedEmail(String templateType, String email, String headerTitle,
            Map<String, String> vars) {
        if (mailProperties.fromAddress().isBlank()) return;
        String subject = resolveSubject(templateType, headerTitle);
        String body = vars.entrySet().stream()
                .reduce(resolveBody(templateType),
                        (html, entry) -> html.replace("{{" + entry.getKey() + "}}", entry.getValue()),
                        (a, b) -> b);
        sendHtmlEmail(email, subject, headerTitle, body);
    }

    private String resolveSubject(String templateType, String fallback) {
        return emailTemplateRepo.findByTemplateTypeAndActiveTrue(templateType)
                .map(EmailTemplate::getSubject)
                .orElse(fallback);
    }

    private String resolveBody(String templateType) {
        return emailTemplateRepo.findByTemplateTypeAndActiveTrue(templateType)
                .map(EmailTemplate::getBodyHtml)
                .orElseGet(() -> {
                    log.warn("[EmailService] No active template for type={}", templateType);
                    return "";
                });
    }

    private void sendHtmlEmail(String email, String subject, String headerTitle, String bodyHtml) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(mailProperties.fromAddress());
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(wrapInLayout(headerTitle, bodyHtml), true);
            addLogoIfAvailable(helper);
            mailSender.send(message);
            log.info("[EmailService] Sent '{}' to {}", subject, email);
        } catch (MessagingException | MailException e) {
            log.error("[EmailService] Failed to send '{}' to {}: {}", subject, email, e.getMessage(), e);
        }
    }

    private String wrapInLayout(String headerTitle, String contentHtml) {
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
                      <div style="padding:28px 32px 30px;">%s</div>
                      <div style="padding:18px 32px;background:#f8fafc;border-top:1px solid #e2e8f0;color:#64748b;font-size:12px;line-height:1.7;">
                        Cresen Solutions LLC<br>This is an automated email from the Leave Management System.
                      </div>
                    </div>
                  </body>
                </html>
                """.formatted(resolveLogoMarkup(), headerTitle, contentHtml);
    }

    private String resolveLogoMarkup() {
        if (resolveLogoFile() == null) return "";
        return "<img src=\"cid:" + UserConstants.LOGO_CID + "\" alt=\"Cresen Solutions Logo\" style=\"display:block;height:56px;width:auto;\">";
    }

    private void addLogoIfAvailable(MimeMessageHelper helper) throws MessagingException {
        File f = resolveLogoFile();
        if (f != null) helper.addInline(UserConstants.LOGO_CID, new FileSystemResource(f), "image/png");
    }

    private File resolveLogoFile() {
        String path = mailProperties.logoPath();
        if (path == null || path.isBlank()) return null;
        File f = new File(path);
        if (!f.exists() || !f.isFile()) { log.warn("[EmailService] Logo not found at {}", path); return null; }
        return f;
    }

    private String safe(String v) { return StringUtils.safe(v); }
}
