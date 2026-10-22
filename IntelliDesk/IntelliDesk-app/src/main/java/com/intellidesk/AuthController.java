package com.intellidesk;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
@RestController @RequestMapping("/api/auth")
public class AuthController {
  record Login(String username, String password) {}
  record Verify(String code) {}
  private static final Logger log = LoggerFactory.getLogger(AuthController.class);
  private final UserRepo users; private final Auth auth;
  private final BCryptPasswordEncoder enc;
  private final SecureRandom rnd = new SecureRandom();
  @Value("${intellidesk.two-factor:true}") private boolean twoFactor;
  public AuthController(UserRepo users, Auth auth, BCryptPasswordEncoder enc) { this.users = users; this.auth = auth; this.enc = enc; }

  @PostMapping("/login")
  public Map<String,Object> login(@RequestBody Login b, HttpSession s) {
    String username = b.username() == null ? "" : b.username().trim();
    User found = users.findByEmailIgnoreCase(username).orElse(null);
    if (found == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Username not found");
    if (!found.active || b.password() == null || !enc.matches(b.password(), found.passwordHash))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect password");
    User u = found;
    if (!twoFactor) { s.setAttribute(Auth.KEY, u.id); return Map.of("verified", true); }
    String code = String.format("%06d", rnd.nextInt(1_000_000));
    s.setAttribute("pendingUid", u.id);
    s.setAttribute("codeHash", enc.encode(code));
    s.setAttribute("codeExp", Instant.now().plus(10, ChronoUnit.MINUTES));
    s.setAttribute("attempts", 0);
    log.info("[EmailGateway] verification code for {}: {}", u.email, code); // replace with a real mail sender
    return Map.of("verified", false, "message", "Code sent to your email. It expires in 10 minutes.");
  }

  @PostMapping("/verify")
  public Map<String,Object> verify(@RequestBody Verify b, HttpSession s, HttpServletRequest req) {
    Long uid = (Long) s.getAttribute("pendingUid");
    if (uid == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in first");
    int attempts = (int) s.getAttribute("attempts");
    if (attempts >= 5 || Instant.now().isAfter((Instant) s.getAttribute("codeExp"))) {
      s.invalidate(); throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Code expired. Sign in again.");
    }
    if (b.code() == null || !enc.matches(b.code().trim(), (String) s.getAttribute("codeHash"))) {
      s.setAttribute("attempts", attempts + 1);
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect code");
    }
    s.removeAttribute("pendingUid"); s.removeAttribute("codeHash");
    s.removeAttribute("codeExp"); s.removeAttribute("attempts");
    req.changeSessionId();
    s.setAttribute(Auth.KEY, uid);
    return Map.of("verified", true);
  }

  @GetMapping("/me") public User me(HttpSession s) { return auth.current(s); }
  @PostMapping("/logout") public void logout(HttpSession s) { s.invalidate(); }
}
