package com.groupxx.greengrocer.model;

/**
 * Defines the user roles within the system, controlling access and
 * functionality.
 */
public enum Role {
    /** Regular customer who purchases products. */
    CUSTOMER,
    /** Carrier responsible for delivering orders. */
    CARRIER,
    /** Store owner who manages products and carriers. */
    OWNER
}
