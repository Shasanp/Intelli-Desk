package com.intellidesk;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
/** An in-app alert for one user about one ticket. */
@Entity @Table(name="notifications", indexes=@Index(name="idx_notif_recipient", columnList="recipient_id,created_at"))
public class Notification {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="notification_id") public Long id;
  @JsonIgnore @ManyToOne(optional=false) @JoinColumn(name="recipient_id") public User recipient;
  @Column(name="ticket_id") public Long ticketId;
  @Column(nullable=false,length=300) public String message;
  @Column(name="created_at",nullable=false) public Instant createdAt;
  /** "read" is a reserved word in MySQL, so the column is is_read. */
  @Column(name="is_read",nullable=false) public boolean read;
}
