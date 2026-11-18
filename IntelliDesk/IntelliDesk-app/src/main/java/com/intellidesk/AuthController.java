package com.intellidesk;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import com.intellidesk.Enums.Role;

@RestController @RequestMapping("/api/auth")
public class AuthController {
  record Login(String username, String password, Role role) {}
  record Verify(String code) {}
  private final UserRepo users; private final Auth auth;
  private final BCryptPasswordEncoder enc; private final AuthenticatorService authenticator;
  private final SettingsController settings;
  @Value("${intellidesk.two-factor:true}") private boolean twoFactor;
  public AuthController(UserRepo users, Auth auth, BCryptPasswordEncoder enc, AuthenticatorService authenticator,
      SettingsController settings) {
    this.users = users; this.auth = auth; this.enc = enc; this.authenticator = authenticator; this.settings = settings;
  }
  @ModelAttribute
  public void preventCaching(HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store"); response.setHeader("Pragma", "no-cache");
  }
  private void clearPending(HttpSession session) {
    for (String name : List.of("pendingUid", "challengeExp", "pendingSecret", "replaceAuthenticator",
        "codeHash", "codeExp", "attempts")) session.removeAttribute(name);
  }
  private void signIn(User user, HttpSession session, HttpServletRequest request) {
    clearPending(session); request.changeSessionId(); session.setAttribute(Auth.KEY, user.id);
    session.setMaxInactiveInterval(settings.sessionTimeoutMinutes() * 60); // admin Session Timeout setting
  }
  private User pendingUser(HttpSession session) {
    Long id = (Long) session.getAttribute("pendingUid");
    Instant expiry = (Instant) session.getAttribute("challengeExp");
    if (id == null || expiry == null || !Instant.now().isBefore(expiry)) {
      clearPending(session); throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign-in expired. Enter your username and password again.");
    }
    return users.findById(id).filter(user -> user.active)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again."));
  }
  private boolean needsSetup(User user, HttpSession session) {
    return user.totpSecret == null || Boolean.TRUE.equals(session.getAttribute("replaceAuthenticator"));
  }
  private Map<String,Object> challenge(User user, HttpSession session) {
    return Map.of("verified", false, "setupRequired", needsSetup(user, session), "username", user.email,
        "role", user.role.name(), "method", "TOTP");
  }
  @PostMapping("/login")
  public Map<String,Object> login(@RequestBody Login body, HttpSession session, HttpServletRequest request) {
    synchronized (session) {
      session.removeAttribute(Auth.KEY); clearPending(session);
      String username = body.username() == null ? "" : body.username().trim();
      User user = users.findByEmailIgnoreCase(username).orElse(null);
      if (user == null || !user.active || body.password() == null || !enc.matches(body.password(), user.passwordHash))
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect username or password, or the account is inactive.");
      if (body.role() != null && body.role() != user.role)
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose " + user.role.name().toLowerCase(java.util.Locale.ROOT) + " as the role for this account.");
      request.changeSessionId();
      if (!twoFactor) { signIn(user, session, request); return Map.of("verified", true); }
      session.setAttribute("pendingUid", user.id);
      session.setAttribute("challengeExp", Instant.now().plusSeconds(600));
      return challenge(user, session);
    }
  }
  @GetMapping("/challenge")
  public Map<String,Object> currentChallenge(HttpSession session) {
    synchronized (session) { return challenge(pendingUser(session), session); }
  }
  @GetMapping("/setup")
  public Map<String,Object> setup(HttpSession session) {
    synchronized (session) {
      User user = pendingUser(session);
      if (!needsSetup(user, session)) throw new ResponseStatusException(HttpStatus.CONFLICT, "This account already has an authenticator.");
      String secret = (String) session.getAttribute("pendingSecret");
      if (secret == null) { secret = Totp.newSecret(); session.setAttribute("pendingSecret", secret); }
      String uri = Totp.uri(user.email, secret);
      return Map.of("username", user.email, "secret", secret, "qrDataUrl", Totp.qrDataUrl(uri));
    }
  }
  @PostMapping("/verify")
  public Map<String,Object> verify(@RequestBody Verify body, HttpSession session, HttpServletRequest request) {
    synchronized (session) {
      User user = pendingUser(session); List<String> codes = List.of();
      String code = body.code() == null ? "" : body.code().trim();
      if (needsSetup(user, session)) {
        String secret = (String) session.getAttribute("pendingSecret");
        if (secret == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Open authenticator setup first.");
        codes = authenticator.enroll(user.id, secret, code, Boolean.TRUE.equals(session.getAttribute("replaceAuthenticator")));
      } else authenticator.verify(user.id, code);
      signIn(user, session, request);
      Map<String,Object> result = new LinkedHashMap<>(); result.put("verified", true);
      if (!codes.isEmpty()) result.put("recoveryCodes", codes);
      return result;
    }
  }
  /** A password plus one recovery code authorizes replacement enrollment, not an MFA-free session. */
  @PostMapping("/recovery")
  public Map<String,Object> recovery(@RequestBody Verify body, HttpSession session, HttpServletRequest request) {
    synchronized (session) {
      User user = pendingUser(session);
      if (needsSetup(user, session)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Finish authenticator setup first.");
      authenticator.consumeRecovery(user.id, body.code());
      request.changeSessionId(); session.removeAttribute("pendingSecret");
      session.setAttribute("replaceAuthenticator", true);
      session.setAttribute("challengeExp", Instant.now().plusSeconds(600));
      return challenge(user, session);
    }
  }
  @GetMapping("/me") public User me(HttpSession session) { return auth.current(session); }
  @PostMapping("/logout") public void logout(HttpSession session) { session.invalidate(); }
}
