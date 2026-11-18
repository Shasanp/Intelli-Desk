package com.intellidesk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
public interface NotificationRepo extends JpaRepository<Notification,Long> {
  List<Notification> findTop30ByRecipientOrderByCreatedAtDescIdDesc(User recipient);
  long countByRecipientAndReadFalse(User recipient);
  @Modifying @Query("update Notification n set n.read = true where n.recipient = :u and n.read = false")
  int markAllRead(User u);
}
