package com.intellidesk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface HistoryRepo extends JpaRepository<TicketHistory,Long> { List<TicketHistory> findByTicketOrderByChangedAt(Ticket t); }
