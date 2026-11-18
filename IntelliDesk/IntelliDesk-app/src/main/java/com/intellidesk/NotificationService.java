package com.intellidesk;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.intellidesk.Enums.Role;

/**
 * Creates in-app notifications. Runs inside the caller's transaction, so a notification is only stored if the
 * ticket change it describes commits. Nobody is notified about their own action, and nothing is sent while the
 * admin "Ticket Update Alerts" setting is off.
 */
@Service @Transactional
public class NotificationService {
  private final NotificationRepo repo; private final UserRepo users; private final SystemSettingRepo settings;
  public NotificationService(NotificationRepo repo, UserRepo users, SystemSettingRepo settings) {
    this.repo = repo; this.users = users; this.settings = settings;
  }
  public boolean enabled() { return settings.findById(SettingsController.TICKET_ALERTS).map(s -> s.enabled).orElse(true); }

  public void notify(User to, User actor, Ticket t, String message) { notifyAll(java.util.List.of(to), actor, t, message); }

  public void notifyAll(Collection<User> to, User actor, Ticket t, String message) {
    if (!enabled()) return;
    Instant now = Instant.now();
    new LinkedHashSet<>(to).stream().filter(Objects::nonNull).filter(u -> u.active)
        .filter(u -> actor == null || !u.id.equals(actor.id))
        .forEach(u -> {
          Notification n = new Notification(); n.recipient = u; n.ticketId = t.id; n.createdAt = now;
          n.message = message.length() > 300 ? message.substring(0, 297) + "..." : message;
          repo.save(n);
        });
  }
  public java.util.List<User> admins() { return users.findByRole(Role.ADMIN); }
}
