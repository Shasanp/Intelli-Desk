package com.intellidesk;
import jakarta.persistence.*;
@Entity @Table(name="categories")
public class Category {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="category_id") public Long id;
  @Column(nullable=false,unique=true,length=80) public String name;
  public boolean active = true;
}
