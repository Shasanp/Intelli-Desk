package com.intellidesk;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="ticket_history")
public class TicketHistory {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="history_id") public Long id;
  @JsonIgnore @ManyToOne(optional=false) @JoinColumn(name="ticket_id") public Ticket ticket;
  @ManyToOne(optional=false) @JoinColumn(name="changed_by") public User changedBy;
  @Column(name="field_name",nullable=false,length=40) public String field;
  @Column(length=1000) public String oldValue;
  @Column(length=1000) public String newValue;
  @Column(nullable=false) public Instant changedAt;
}
