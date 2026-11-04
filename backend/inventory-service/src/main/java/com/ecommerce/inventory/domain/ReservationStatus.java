package com.ecommerce.inventory.domain;

public enum ReservationStatus {
    /** Units are held: moved from available to reserved. */
    RESERVED,
    /** Order failed or was cancelled before completion; units went back to available. */
    RELEASED,
    /** Order completed; reserved units left the warehouse. */
    COMMITTED
}
