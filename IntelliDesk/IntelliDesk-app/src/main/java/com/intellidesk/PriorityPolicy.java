package com.intellidesk;
import jakarta.persistence.*;
@Entity @Table(name="priority_policies")
public class PriorityPolicy {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="priority_id") public Long id;
  @Column(nullable=false,unique=true,length=30) public String name;
  @Column(name="priority_rank",nullable=false) public int rank;
  @Column(nullable=false) public int resolutionHours;
}
