package com.intellidesk;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.intellidesk.Enums.*;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="comments")
public class Comment {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="comment_id") public Long id;
  @JsonIgnore @ManyToOne(optional=false) @JoinColumn(name="ticket_id") public Ticket ticket;
  @ManyToOne(optional=false) @JoinColumn(name="author_id") public User author;
  @Column(nullable=false,length=4000) public String body;
  @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) public CommentVisibility visibility;
  @Column(nullable=false) public Instant createdAt;
}
