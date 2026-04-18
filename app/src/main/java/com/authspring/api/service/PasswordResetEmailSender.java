package com.authspring.api.service;

import com.authspring.api.config.FrontendProperties;
import com.authspring.api.config.PasswordResetMailProperties;
import com.authspring.api.domain.User;
import com.authspring.api.security.JwtService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetEmailSender {

    private static final Charset MAIL_CHARSET = StandardCharsets.UTF_8;

    private final JavaMailSender mailSender;
    private final JwtService jwtService;
    private final FrontendProperties frontend;
    private final PasswordResetMailProperties mail;

    public PasswordResetEmailSender(
            JavaMailSender mailSender,
            JwtService jwtService,
            FrontendProperties frontend,
            PasswordResetMailProperties mail) {
        this.mailSender = mailSender;
        this.jwtService = jwtService;
        this.frontend = frontend;
        this.mail = mail;
    }

    public void send(User user, String plainToken) throws MessagingException, UnsupportedEncodingException {
        String apiToken = jwtService.createPasswordResetFlowToken(user);
        String url = buildResetUrl(user, plainToken, apiToken);
        String body = buildBody(user, url);

        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, false, MAIL_CHARSET.name());
        helper.setFrom(mail.fromAddress(), mail.fromName());
        helper.setTo(user.getEmail());
        helper.setSubject(mail.subject());
        helper.setText(body, false);
        mailSender.send(mime);
    }

    private String buildResetUrl(User user, String plainToken, String apiToken) {
        String base = frontend.baseUrl().replaceAll("/$", "");
        return base
                + "/?new_password=1&password_reset_token="
                + urlEncode(plainToken)
                + "&email="
                + urlEncode(user.getEmail())
                + "&api_token="
                + urlEncode(apiToken)
                + "&user_id="
                + user.getId()
                + "&user_name="
                + urlEncode(user.getName());
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, MAIL_CHARSET);
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
