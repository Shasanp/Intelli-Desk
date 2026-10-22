package com.intellidesk;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.intellidesk.Enums.Role;

/** Demo data. All demo accounts use the password "password". */
@Component
public class DataSeeder implements CommandLineRunner {
  private final UserRepo users; private final CategoryRepo cats; private final PriorityRepo prios;
  private final BCryptPasswordEncoder enc;
  public DataSeeder(UserRepo u, CategoryRepo c, PriorityRepo p, BCryptPasswordEncoder enc) { users = u; cats = c; prios = p; this.enc = enc; }

  @Override public void run(String... args) {
    if (users.count() > 0) return;

    for (String n : new String[]{"Network", "Software", "Account", "Hardware"}) {
      Category c = new Category(); c.name = n; cats.save(c);
    }
    Object[][] p = {{"Low", 1, 48}, {"Medium", 2, 24}, {"High", 3, 4}};
    for (Object[] x : p) {
      PriorityPolicy pp = new PriorityPolicy(); pp.name = (String) x[0]; pp.rank = (int) x[1]; pp.resolutionHours = (int) x[2]; prios.save(pp);
    }

    String hash = enc.encode("password");
    // {display name, role, username}
    String[][] u = {
      {"James Carter",   "EMPLOYEE",   "james.carter"},
      {"Priya Nair",     "EMPLOYEE",   "priya.nair"},
      {"Daniel Okonkwo", "EMPLOYEE",   "daniel.okonkwo"},
      {"Sofia Reyes",    "EMPLOYEE",   "sofia.reyes"},
      {"Marcus Webb",    "TECHNICIAN", "marcus.webb"},
      {"Aisha Thornton", "TECHNICIAN", "aisha.thornton"},
      {"Leo Yamamoto",   "TECHNICIAN", "leo.yamamoto"},
      {"admin",          "ADMIN",      "admin"}
    };
    for (String[] r : u) {
      User x = new User(); x.name = r[0]; x.role = Role.valueOf(r[1]); x.passwordHash = hash;
      x.email = r[2]; // stored as username (no @domain)
      if (x.role == Role.TECHNICIAN) x.capacity = 5;
      users.save(x);
    }
  }
}
