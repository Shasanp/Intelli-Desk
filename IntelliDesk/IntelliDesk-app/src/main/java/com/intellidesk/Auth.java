package com.intellidesk;
import com.intellidesk.Enums.Role;
import jakarta.servlet.http.HttpSession;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
/** Server-side authorization: identity and role always come from the session, never from the client. */
@Component
public class Auth {
  public static final String KEY = "uid";
  private final UserRepo users;
  public Auth(UserRepo users) { this.users = users; }
  public User current(HttpSession s) {
    Object id = s.getAttribute(KEY);
    if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in");
    return users.findById((Long) id).filter(u -> u.active)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in"));
  }
  public User require(HttpSession s, Role... roles) {
    User u = current(s);
    if (!Arrays.asList(roles).contains(u.role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
    return u;
  }
}
