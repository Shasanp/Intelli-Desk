package com.intellidesk;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.intellidesk.Enums.*;
import com.intellidesk.Enums.CommentVisibility;
import com.intellidesk.Enums.Role;
import com.intellidesk.Enums.TicketStatus;
import static com.intellidesk.Enums.TicketStatus.IN_PROGRESS;
import static com.intellidesk.Enums.TicketStatus.OPEN;
import static com.intellidesk.Enums.TicketStatus.PENDING;
import static com.intellidesk.Enums.TicketStatus.RESOLVED;
/** Business rules. Every change and its TicketHistory row commit in one transaction. */
@Service @Transactional
public class TicketService {
  private static final Map<TicketStatus,Set<TicketStatus>> NEXT = Map.of(
      OPEN, Set.of(IN_PROGRESS), IN_PROGRESS, Set.of(PENDING), PENDING, Set.of(IN_PROGRESS), RESOLVED, Set.of());
  private final TicketRepo tickets; private final CommentRepo comments; private final HistoryRepo history;
  private final CategoryRepo cats; private final PriorityRepo prios; private final UserRepo users;
  public TicketService(TicketRepo t, CommentRepo c, HistoryRepo h, CategoryRepo ca, PriorityRepo p, UserRepo u) {
    tickets = t; comments = c; history = h; cats = ca; prios = p; users = u;
  }
  private static ResponseStatusException err(HttpStatus s, String m) { return new ResponseStatusException(s, m); }
  private static boolean blank(String s) { return s == null || s.isBlank(); }
  private void log(Ticket t, User by, String field, Object o, Object n) {
    TicketHistory h = new TicketHistory(); h.ticket = t; h.changedBy = by; h.field = field;
    h.oldValue = o == null ? null : o.toString(); h.newValue = n == null ? null : n.toString(); h.changedAt = Instant.now();
    history.save(h);
  }
  public Ticket create(User u, String title, String desc, Long catId, Long prioId) {
    if (u.role != Role.EMPLOYEE) throw err(HttpStatus.FORBIDDEN, "Only employees submit tickets");
    if (blank(title) || blank(desc) || catId == null || prioId == null) throw err(HttpStatus.BAD_REQUEST, "Title, category, priority and description are required");
    Ticket t = new Ticket(); t.title = title.trim(); t.description = desc.trim(); t.submitter = u; t.status = OPEN;
    t.category = cats.findById(catId).filter(c -> c.active).orElseThrow(() -> err(HttpStatus.BAD_REQUEST, "Unknown category"));
    t.priority = prios.findById(prioId).orElseThrow(() -> err(HttpStatus.BAD_REQUEST, "Unknown priority"));
    t.createdAt = Instant.now(); t.dueAt = t.createdAt.plus(t.priority.resolutionHours, ChronoUnit.HOURS);
    tickets.save(t); log(t, u, "status", null, OPEN); return t;
  }
  public List<Ticket> list(User u) {
    return u.role == Role.EMPLOYEE ? tickets.findBySubmitterOrderByCreatedAtDesc(u) : tickets.findAllByOrderByCreatedAtDesc();
  }
  public Ticket get(User u, Long id) {
    Ticket t = tickets.findById(id).orElseThrow(() -> err(HttpStatus.NOT_FOUND, "Ticket not found"));
    if (u.role == Role.EMPLOYEE && !t.submitter.id.equals(u.id)) throw err(HttpStatus.FORBIDDEN, "Not your ticket");
    return t;
  }
  private Ticket workable(User u, Long id) {
    Ticket t = get(u, id);
    if (u.role == Role.EMPLOYEE || (u.role == Role.TECHNICIAN && (t.assignee == null || !t.assignee.id.equals(u.id))))
      throw err(HttpStatus.FORBIDDEN, "Only the assigned technician or an admin can do this");
    if (t.status == RESOLVED) throw err(HttpStatus.BAD_REQUEST, "Resolved tickets are final");
    return t;
  }
  public Ticket assign(User admin, Long id, Long techId) {
    if (admin.role != Role.ADMIN) throw err(HttpStatus.FORBIDDEN, "Only admins can assign tickets");
    Ticket t = get(admin, id);
    if (t.status == RESOLVED) throw err(HttpStatus.BAD_REQUEST, "Resolved tickets are final");
    User tech = users.findById(techId == null ? -1L : techId).orElseThrow(() -> err(HttpStatus.BAD_REQUEST, "Unknown technician"));
    if (tech.role != Role.TECHNICIAN || !tech.active) throw err(HttpStatus.BAD_REQUEST, "Must be an active technician");
    log(t, admin, "assignee", t.assignee == null ? null : t.assignee.name, tech.name); t.assignee = tech; return t;
  }
  public Ticket changeStatus(User u, Long id, TicketStatus to) {
    Ticket t = workable(u, id);
    if (to == null) throw err(HttpStatus.BAD_REQUEST, "Status is required");
    if (to == RESOLVED) throw err(HttpStatus.BAD_REQUEST, "Use resolve to close a ticket");
    Set<TicketStatus> allowed = NEXT.get(t.status);
    if (allowed == null || !allowed.contains(to))
      throw err(HttpStatus.BAD_REQUEST, "Status change not allowed: " + t.status + " → " + to);
    log(t, u, "status", t.status, to); t.status = to; return t;
  }
  public Ticket resolve(User u, Long id, String notes) {
    Ticket t = workable(u, id);
    if (t.assignee == null) throw err(HttpStatus.BAD_REQUEST, "Assign a technician first");
    if (blank(notes)) throw err(HttpStatus.BAD_REQUEST, "Resolution notes are required");
    log(t, u, "status", t.status, RESOLVED); t.status = RESOLVED; t.resolvedAt = Instant.now(); t.resolutionNotes = notes.trim(); return t;
  }
  public Ticket escalate(User u, Long id, String reason) {
    Ticket t = workable(u, id);
    if (blank(reason)) throw err(HttpStatus.BAD_REQUEST, "A reason is required");
    t.escalatedBy = u; t.escalationReason = reason.trim(); log(t, u, "escalated", null, reason.trim()); return t;
  }
  public Comment addComment(User u, Long id, String body, CommentVisibility vis) {
    Ticket t = get(u, id);
    if (blank(body)) throw err(HttpStatus.BAD_REQUEST, "Comment is empty");
    Comment c = new Comment(); c.ticket = t; c.author = u; c.body = body.trim(); c.createdAt = Instant.now();
    c.visibility = (u.role == Role.EMPLOYEE || vis == null) ? CommentVisibility.PUBLIC : vis; // INTERNAL is staff-only
    return comments.save(c);
  }
  @Transactional(readOnly = true)
  public List<Comment> comments(User u, Long id) {
    Ticket t = get(u, id);
    return comments.findByTicketOrderByCreatedAt(t).stream()
        .filter(c -> u.role != Role.EMPLOYEE || c.visibility == CommentVisibility.PUBLIC).toList();
  }
  @Transactional(readOnly = true)
  public List<TicketHistory> history(User u, Long id) { return history.findByTicketOrderByChangedAt(get(u, id)); }
}
