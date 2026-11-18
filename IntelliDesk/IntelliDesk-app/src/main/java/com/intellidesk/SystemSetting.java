package com.intellidesk;
import jakarta.persistence.*;
/** One admin-editable preference, stored by key: an on/off flag, or a number for numeric settings. */
@Entity @Table(name="system_settings")
public class SystemSetting {
  @Id @Column(name="setting_key",length=60) public String key;
  @Column(nullable=false) public boolean enabled;
  @Column(name="number_value") public Integer number;
  public SystemSetting() {}
  public SystemSetting(String key, boolean enabled) { this.key = key; this.enabled = enabled; }
  public SystemSetting(String key, int number) { this.key = key; this.enabled = true; this.number = number; }
}
