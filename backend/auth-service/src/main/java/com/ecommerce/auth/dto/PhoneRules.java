package com.ecommerce.auth.dto;

final class PhoneRules {

    static final String PATTERN = "^\\+?[0-9 ()-]{7,20}$";
    static final String MESSAGE = "must be 7 to 20 digits, optionally starting with +";

    private PhoneRules() {
    }
}
