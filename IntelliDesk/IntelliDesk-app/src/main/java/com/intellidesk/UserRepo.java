package com.intellidesk;
import com.intellidesk.Enums.Role;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserRepo extends JpaRepository<User,Long> { Optional<User> findByEmailIgnoreCase(String e); List<User> findByRole(Role r); }
