package com.intellidesk;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.intellidesk.Enums.*;
import jakarta.persistence.*;
@Entity @Table(name="users")
public class User {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="user_id") public Long id;
  @Column(nullable=false) public String name;
  @Column(nullable=false,unique=true,length=254) public String email;
  @JsonIgnore @Column(nullable=false) public String passwordHash;
  @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) public Role role;
  public boolean active = true;
  public Integer capacity;
}
