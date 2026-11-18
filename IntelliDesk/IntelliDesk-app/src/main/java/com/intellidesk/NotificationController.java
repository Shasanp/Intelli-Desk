package com.intellidesk;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpSession;

/** Each user sees and updates only their own notifications. */
@RestController @RequestMapping("/api/notifications")
public class NotificationController {
  private final Auth auth; private final NotificationRepo repo;
  public NotificationController(Auth auth, NotificationRepo repo) { this.auth = auth; this.repo = repo; }

  @GetMapping
  public Map<String,Object> list(HttpSession s) {
    User u = auth.current(s);
    return Map.of("unread", repo.countByRecipientAndReadFalse(u), "items", repo.findTop30ByRecipientOrderByCreatedAtDescIdDesc(u));
  }
  @PostMapping("/{id}/read") @Transactional
  public void read(@PathVariable Long id, HttpSession s) {
    User u = auth.current(s);
    Notification n = repo.findById(id).filter(x -> x.recipient.id.equals(u.id))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    n.read = true;
  }
  @PostMapping("/read-all") @Transactional
  public void readAll(HttpSession s) { repo.markAllRead(auth.current(s)); }
}
