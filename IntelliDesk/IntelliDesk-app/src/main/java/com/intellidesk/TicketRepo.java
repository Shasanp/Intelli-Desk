package com.intellidesk;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.intellidesk.Enums.TicketStatus;
public interface TicketRepo extends JpaRepository<Ticket,Long> {

  /** Eagerly fetch all FK associations in one JOIN to avoid N+1 queries. */
  @EntityGraph(attributePaths = {"submitter","assignee","category","priority","escalatedBy"})
  List<Ticket> findBySubmitterOrderByCreatedAtDesc(User u);

  @EntityGraph(attributePaths = {"submitter","assignee","category","priority","escalatedBy"})
  List<Ticket> findAllByOrderByCreatedAtDesc();

  @EntityGraph(attributePaths = {"submitter","assignee","category","priority","escalatedBy"})
  Optional<Ticket> findById(Long id);

  long countByStatus(TicketStatus s);
  long countByAssigneeAndStatusNot(User u, TicketStatus s);
}
