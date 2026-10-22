package com.intellidesk;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
public class IntelliDeskApplication {
  public static void main(String[] a) { SpringApplication.run(IntelliDeskApplication.class, a); }

  /** Single shared BCryptPasswordEncoder bean — injected into all controllers and DataSeeder. */
  @Bean
  public BCryptPasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}

