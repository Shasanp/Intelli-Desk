package com.intellidesk;
import static com.intellidesk.Enums.Role.*;
import com.intellidesk.Enums.*;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api")
public class TicketController {
  record NewTicket(String title, String description, Long categoryId, Long priorityId) {}
  record Assign(Long technicianId) {}
  record StatusChange(TicketStatus status) {}
  record Notes(String notes) {}
  record Reason(String reason) {}
  record NewComment(String body, CommentVisibility visibility) {}
  private final TicketService svc; private final Auth auth; private final CategoryRepo cats; private final PriorityRepo prios;
  public TicketController(TicketService svc, Auth auth, CategoryRepo cats, PriorityRepo prios) { this.svc = svc; this.auth = auth; this.cats = cats; this.prios = prios; }

  @GetMapping("/tickets") public List<Ticket> list(HttpSession s) { return svc.list(auth.current(s)); }
  @PostMapping("/tickets") public Ticket create(@RequestBody NewTicket b, HttpSession s) { return svc.create(auth.current(s), b.title(), b.description(), b.categoryId(), b.priorityId()); }
  @GetMapping("/tickets/{id}") public Ticket get(@PathVariable Long id, HttpSession s) { return svc.get(auth.current(s), id); }
  @PostMapping("/tickets/{id}/assign") public Ticket assign(@PathVariable Long id, @RequestBody Assign b, HttpSession s) { return svc.assign(auth.require(s, ADMIN), id, b.technicianId()); }
  @PostMapping("/tickets/{id}/status") public Ticket status(@PathVariable Long id, @RequestBody StatusChange b, HttpSession s) { return svc.changeStatus(auth.require(s, TECHNICIAN, ADMIN), id, b.status()); }
  @PostMapping("/tickets/{id}/resolve") public Ticket resolve(@PathVariable Long id, @RequestBody Notes b, HttpSession s) { return svc.resolve(auth.require(s, TECHNICIAN, ADMIN), id, b.notes()); }
  @PostMapping("/tickets/{id}/escalate") public Ticket escalate(@PathVariable Long id, @RequestBody Reason b, HttpSession s) { return svc.escalate(auth.require(s, TECHNICIAN, ADMIN), id, b.reason()); }
  @GetMapping("/tickets/{id}/comments") public List<Comment> comments(@PathVariable Long id, HttpSession s) { return svc.comments(auth.current(s), id); }
  @PostMapping("/tickets/{id}/comments") public Comment comment(@PathVariable Long id, @RequestBody NewComment b, HttpSession s) { return svc.addComment(auth.current(s), id, b.body(), b.visibility()); }
  @GetMapping("/tickets/{id}/history") public List<TicketHistory> history(@PathVariable Long id, HttpSession s) { return svc.history(auth.current(s), id); }
  @GetMapping("/categories") public List<Category> categories(HttpSession s) { auth.current(s); return cats.findAll().stream().filter(c -> c.active).toList(); }
  @GetMapping("/priorities") public List<PriorityPolicy> priorities(HttpSession s) { auth.current(s); return prios.findAll(); }
}
