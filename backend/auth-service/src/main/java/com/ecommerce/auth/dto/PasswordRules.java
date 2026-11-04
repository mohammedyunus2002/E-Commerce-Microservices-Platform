package com.ecommerce.auth.dto;

final class PasswordRules {

    static final String PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).+$";
    static final String MESSAGE = "must contain at least one letter and one digit";

    private PasswordRules() {
    }
}
