package com.example.BACK.dto;

public class LoginRequest {
    private String email;
    private String password;

    // Getters y setters
    public String getEmail() { return email; }
    public String getPassword() { return password; }

    public void setEmail(String email) { this.email = email; }
    public void setPassword(String password) { this.password = password; }
}
