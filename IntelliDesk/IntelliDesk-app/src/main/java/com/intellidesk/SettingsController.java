package com.intellidesk;
import static com.intellidesk.Enums.Role.ADMIN;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpSession;

/**
 * Admin-only system preferences. Unknown keys and out-of-range values are rejected.
 * Role-based access is not a setting: the server always enforces it.
 */
@RestController @RequestMapping("/api/settings")
public class SettingsController {
  static final String TICKET_ALERTS = "ticketUpdateAlerts";
  static final String SESSION_TIMEOUT = "sessionTimeoutMinutes";
  static final List<Integer> TIMEOUT_CHOICES = List.of(15, 30, 60, 120);
  static final int DEFAULT_TIMEOUT = 30;
  private final Auth auth; private final SystemSettingRepo repo;
  public SettingsController(Auth auth, SystemSettingRepo repo) { this.auth = auth; this.repo = repo; }

  @GetMapping
  public Map<String,Object> get(HttpSession s) { auth.require(s, ADMIN); return current(); }

  @PutMapping
  public Map<String,Object> save(@RequestBody Map<String,Object> body, HttpSession s) {
    auth.require(s, ADMIN);
    if (body == null || body.isEmpty()) throw bad("No settings provided");
    for (var e : body.entrySet()) {
      Object v = e.getValue();
      switch (e.getKey()) {
        case TICKET_ALERTS -> { if (!(v instanceof Boolean)) throw bad("Ticket Update Alerts must be true or false"); }
        case SESSION_TIMEOUT -> {
          if (!(v instanceof Integer n) || !TIMEOUT_CHOICES.contains(n))
            throw bad("Session timeout must be one of " + TIMEOUT_CHOICES + " minutes");
        }
        default -> throw bad("Unknown setting: " + e.getKey());
      }
    }
    if (body.containsKey(TICKET_ALERTS)) repo.save(new SystemSetting(TICKET_ALERTS, (Boolean) body.get(TICKET_ALERTS)));
    if (body.containsKey(SESSION_TIMEOUT)) repo.save(new SystemSetting(SESSION_TIMEOUT, (Integer) body.get(SESSION_TIMEOUT)));
    return current();
  }

  /** Minutes of inactivity before a signed-in user is logged out. */
  public int sessionTimeoutMinutes() {
    return repo.findById(SESSION_TIMEOUT).map(x -> x.number).filter(TIMEOUT_CHOICES::contains).orElse(DEFAULT_TIMEOUT);
  }

  private Map<String,Object> current() {
    Map<String,Object> m = new LinkedHashMap<>();
    m.put(TICKET_ALERTS, repo.findById(TICKET_ALERTS).map(x -> x.enabled).orElse(true));
    m.put(SESSION_TIMEOUT, sessionTimeoutMinutes());
    m.put("sessionTimeoutChoices", TIMEOUT_CHOICES);
    return m;
  }
  private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
