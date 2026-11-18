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
  private final NotificationService notes;
  public TicketService(TicketRepo t, CommentRepo c, HistoryRepo h, CategoryRepo ca, PriorityRepo p, UserRepo u, NotificationService n) {
    tickets = t; comments = c; history = h; cats = ca; prios = p; users = u; notes = n;
  }
  private static String ref(Ticket t) { return "#" + t.id + " “" + t.title + "”"; }
  /** Staff who should hear about a ticket: its technician, or every admin while it is unassigned. */
  private List<User> staffFor(Ticket t) { return t.assignee != null ? List.of(t.assignee) : notes.admins(); }
  /** Priority given to every new ticket. Employees cannot choose it; staff adjust it after triage. */
  static final String DEFAULT_PRIORITY = "Medium";
  private static ResponseStatusException err(HttpStatus s, String m) { return new ResponseStatusException(s, m); }
  private static boolean blank(String s) { return s == null || s.isBlank(); }
  private void log(Ticket t, User by, String field, Object o, Object n) {
    TicketHistory h = new TicketHistory(); h.ticket = t; h.changedBy = by; h.field = field;
    h.oldValue = o == null ? null : o.toString(); h.newValue = n == null ? null : n.toString(); h.changedAt = Instant.now();
    history.save(h);
  }
  /** Employees choose only the category; priority always starts at DEFAULT_PRIORITY (any priority sent by the client is ignored). */
  public Ticket create(User u, String title, String desc, Long catId) {
    if (u.role != Role.EMPLOYEE) throw err(HttpStatus.FORBIDDEN, "Only employees submit tickets");
    if (blank(title) || blank(desc) || catId == null) throw err(HttpStatus.BAD_REQUEST, "Title, category and description are required");
    Ticket t = new Ticket(); t.title = title.trim(); t.description = desc.trim(); t.submitter = u; t.status = OPEN;
    t.category = cats.findById(catId).filter(c -> c.active).orElseThrow(() -> err(HttpStatus.BAD_REQUEST, "Unknown category"));
    t.priority = prios.findByNameIgnoreCase(DEFAULT_PRIORITY).or(prios::findFirstByOrderByRankAsc)
        .orElseThrow(() -> err(HttpStatus.INTERNAL_SERVER_ERROR, "No priority levels are configured"));
    t.createdAt = Instant.now(); t.dueAt = t.createdAt.plus(t.priority.resolutionHours, ChronoUnit.HOURS);
    tickets.save(t); log(t, u, "status", null, OPEN);
    notes.notifyAll(notes.admins(), u, t, "New ticket " + ref(t) + " from " + u.name);
    return t;
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
    log(t, admin, "assignee", t.assignee == null ? null : t.assignee.name, tech.name); t.assignee = tech;
    notes.notify(tech, admin, t, "Ticket " + ref(t) + " was assigned to you");
    notes.notify(t.submitter, admin, t, tech.name + " is now handling your ticket " + ref(t));
    return t;
  }
  /** SLA deadline = created + priority hours + time spent waiting on the employee. */
  private static Instant deadline(Ticket t) {
    long paused = t.pausedSeconds == null ? 0 : t.pausedSeconds;
    return t.createdAt.plus(t.priority.resolutionHours, ChronoUnit.HOURS).plusSeconds(paused);
  }
  /** Ends an SLA pause: the waiting time is added to the deadline and recorded in the history. */
  private void resumeSla(Ticket t, User by) {
    if (t.pausedAt == null) return;
    long secs = Math.max(0, java.time.Duration.between(t.pausedAt, Instant.now()).getSeconds());
    t.pausedSeconds = (t.pausedSeconds == null ? 0 : t.pausedSeconds) + secs;
    t.pausedAt = null;
    Instant old = t.dueAt; t.dueAt = deadline(t);
    log(t, by, "sla", old, t.dueAt);
  }
  /**
   * PENDING means "waiting on the employee", so it pauses the SLA clock. To stop it being used just to gain time,
   * moving to PENDING requires a question that is posted to the employee, and the employee's reply resumes the clock.
   */
  public Ticket changeStatus(User u, Long id, TicketStatus to, String question) {
    Ticket t = workable(u, id);
    if (to == null) throw err(HttpStatus.BAD_REQUEST, "Status is required");
    if (to == RESOLVED) throw err(HttpStatus.BAD_REQUEST, "Use resolve to close a ticket");
    Set<TicketStatus> allowed = NEXT.get(t.status);
    if (allowed == null || !allowed.contains(to))
      throw err(HttpStatus.BAD_REQUEST, "Status change not allowed: " + t.status + " → " + to);
    if (to == PENDING) {
      if (blank(question)) throw err(HttpStatus.BAD_REQUEST, "Tell the employee what you need from them before setting the ticket to Pending");
      Comment c = new Comment(); c.ticket = t; c.author = u; c.body = question.trim(); c.createdAt = Instant.now();
      c.visibility = CommentVisibility.PUBLIC; comments.save(c);
      t.pausedAt = Instant.now();
    }
    if (t.status == PENDING) resumeSla(t, u);
    log(t, u, "status", t.status, to); t.status = to;
    notes.notify(t.submitter, u, t, to == PENDING ? u.name + " needs more information on your ticket " + ref(t)
        : "Your ticket " + ref(t) + " is now In Progress");
    return t;
  }
  /** Assigned technician or admin re-prioritizes a ticket; the SLA deadline is recalculated from the creation time. */
  public Ticket changePriority(User u, Long id, Long prioId) {
    Ticket t = workable(u, id);
    PriorityPolicy p = prios.findById(prioId == null ? -1L : prioId).orElseThrow(() -> err(HttpStatus.BAD_REQUEST, "Unknown priority"));
    if (p.id.equals(t.priority.id)) return t;
    log(t, u, "priority", t.priority.name, p.name);
    t.priority = p; t.dueAt = deadline(t); return t;
  }
  public Ticket resolve(User u, Long id, String notes) {
    Ticket t = workable(u, id);
    if (t.assignee == null) throw err(HttpStatus.BAD_REQUEST, "Assign a technician first");
    if (blank(notes)) throw err(HttpStatus.BAD_REQUEST, "Resolution notes are required");
    resumeSla(t, u);
    log(t, u, "status", t.status, RESOLVED); t.status = RESOLVED; t.resolvedAt = Instant.now(); t.resolutionNotes = notes.trim();
    this.notes.notify(t.submitter, u, t, "Your ticket " + ref(t) + " was resolved");
    return t;
  }
  public Ticket escalate(User u, Long id, String reason) {
    Ticket t = workable(u, id);
    if (blank(reason)) throw err(HttpStatus.BAD_REQUEST, "A reason is required");
    t.escalatedBy = u; t.escalationReason = reason.trim(); log(t, u, "escalated", null, reason.trim());
    notes.notifyAll(notes.admins(), u, t, u.name + " escalated ticket " + ref(t));
    return t;
  }
  public Comment addComment(User u, Long id, String body, CommentVisibility vis) {
    Ticket t = get(u, id);
    if (blank(body)) throw err(HttpStatus.BAD_REQUEST, "Comment is empty");
    Comment c = new Comment(); c.ticket = t; c.author = u; c.body = body.trim(); c.createdAt = Instant.now();
    c.visibility = (u.role == Role.EMPLOYEE || vis == null) ? CommentVisibility.PUBLIC : vis; // INTERNAL is staff-only
    comments.save(c);
    if (u.role == Role.EMPLOYEE && t.status == PENDING) { // the employee answered: back to work, clock running
      resumeSla(t, u); log(t, u, "status", PENDING, IN_PROGRESS); t.status = IN_PROGRESS;
    }
    if (u.role == Role.EMPLOYEE) notes.notifyAll(staffFor(t), u, t, u.name + " replied on ticket " + ref(t));
    else if (c.visibility == CommentVisibility.PUBLIC) notes.notify(t.submitter, u, t, u.name + " commented on your ticket " + ref(t));
    else notes.notifyAll(staffFor(t), u, t, u.name + " added an internal note on ticket " + ref(t));
    return c;
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
