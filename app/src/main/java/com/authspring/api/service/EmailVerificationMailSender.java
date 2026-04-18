package com.authspring.api.service;

import com.authspring.api.config.VerificationMailProperties;
import com.authspring.api.domain.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailVerificationMailSender {

    private static final Charset MAIL_CHARSET = StandardCharsets.UTF_8;

    private final JavaMailSender mailSender;
    private final VerificationMailProperties mail;

    public EmailVerificationMailSender(JavaMailSender mailSender, VerificationMailProperties mail) {
        this.mailSender = mailSender;
        this.mail = mail;
    }

    public void send(User user, String verificationUrl) throws MessagingException, UnsupportedEncodingException {
        String body = buildBody(user, verificationUrl);

        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, false, MAIL_CHARSET.name());
        helper.setFrom(mail.fromAddress(), mail.fromName());
        helper.setTo(user.getEmail());
        helper.setSubject(mail.subject());
        helper.setText(body, false);
        mailSender.send(mime);
    }

    private String buildBody(User user, String url) {
        String greeting = mail.greetingTemplate().replace("{name}", user.getName());
        StringBuilder sb = new StringBuilder();
        sb.append(greeting).append("\n\n");
        for (String line : mail.lines()) {
            sb.append(line).append("\n\n");
        }
        sb.append(mail.actionLabel()).append(":\n").append(url).append("\n\n");
        for (String footer : mail.footerLines()) {
            String line = footer.replace("{minutes}", String.valueOf(mail.expiryMinutes()));
            sb.append(line).append("\n\n");
        }
        sb.append(mail.salutation());
        return sb.toString();
    }
}
