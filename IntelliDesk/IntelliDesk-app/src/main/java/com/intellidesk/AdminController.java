package com.intellidesk;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.intellidesk.Enums.*;
import com.intellidesk.Enums.Role;
import static com.intellidesk.Enums.Role.ADMIN;
import static com.intellidesk.Enums.Role.EMPLOYEE;
import static com.intellidesk.Enums.Role.TECHNICIAN;
import static com.intellidesk.Enums.TicketStatus.IN_PROGRESS;
import static com.intellidesk.Enums.TicketStatus.OPEN;
import static com.intellidesk.Enums.TicketStatus.PENDING;
import static com.intellidesk.Enums.TicketStatus.RESOLVED;

import jakarta.servlet.http.HttpSession;
@RestController @RequestMapping("/api")
public class AdminController {
  record NewUser(String name, String email, String password, Role role) {}
  private final Auth auth; private final UserRepo users; private final TicketRepo tickets;
  private final BCryptPasswordEncoder enc;
  public AdminController(Auth auth, UserRepo users, TicketRepo tickets, BCryptPasswordEncoder enc) { this.auth = auth; this.users = users; this.tickets = tickets; this.enc = enc; }

  /** Every number is calculated on request from stored rows. */
  @GetMapping("/reports/dashboard")
  public Map<String,Object> dashboard(HttpSession s) {
    auth.require(s, ADMIN, TECHNICIAN);
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("total", tickets.count()); m.put("open", tickets.countByStatus(OPEN));
    m.put("inProgress", tickets.countByStatus(IN_PROGRESS)); m.put("pending", tickets.countByStatus(PENDING));
    m.put("resolved", tickets.countByStatus(RESOLVED));
    m.put("technicians", users.findByRole(TECHNICIAN).stream().filter(u -> u.active).count());
    m.put("employees", users.findByRole(EMPLOYEE).stream().filter(u -> u.active).count());
    return m;
  }
  @GetMapping("/technicians")
  public List<Map<String,Object>> technicians(HttpSession s) {
    auth.require(s, ADMIN);
    return users.findByRole(TECHNICIAN).stream().filter(u -> u.active).map(u -> {
      long active = tickets.countByAssigneeAndStatusNot(u, RESOLVED);
      Map<String,Object> m = new LinkedHashMap<>();
      m.put("id", u.id); m.put("name", u.name); m.put("email", u.email); m.put("activeTickets", active);
      m.put("loadPercent", u.capacity == null || u.capacity == 0 ? null : Math.round(active * 100.0 / u.capacity));
      return m;
    }).toList();
  }
  @GetMapping("/users") public List<User> users(HttpSession s) { auth.require(s, ADMIN); return users.findAll(); }
  @PostMapping("/users")
  public User create(@RequestBody NewUser b, HttpSession s) {
    auth.require(s, ADMIN);
    if (b.name() == null || b.name().isBlank() || b.email() == null || !b.email().contains("@") || b.role() == null
        || b.password() == null || b.password().length() < 8)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name, valid email, role and an 8+ character password are required");
    if (users.findByEmailIgnoreCase(b.email().trim()).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
    User u = new User(); u.name = b.name().trim(); u.email = b.email().trim(); u.role = b.role();
    u.passwordHash = enc.encode(b.password()); u.capacity = b.role() == TECHNICIAN ? 5 : null;
    return users.save(u);
  }
  /** Users are deactivated, never deleted. */
  @PostMapping("/users/{id}/deactivate")
  public User deactivate(@PathVariable Long id, HttpSession s) {
    User me = auth.require(s, ADMIN);
    User u = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    if (u.id.equals(me.id)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot deactivate yourself");
    u.active = false; return users.save(u);
  }
}
