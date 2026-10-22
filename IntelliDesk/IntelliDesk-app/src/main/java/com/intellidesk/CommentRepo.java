package com.intellidesk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CommentRepo extends JpaRepository<Comment,Long> { List<Comment> findByTicketOrderByCreatedAt(Ticket t); }
