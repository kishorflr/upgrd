package com.upgrd.core.model;

/**
 * How a planned upgrade change should be treated in the review dashboard.
 */
public enum ChangeClassification {
    /** Required for target runtime, security, or build correctness. */
    MANDATORY,
    /** Strongly recommended; safe mechanical improvement. */
    RECOMMENDED,
    /** Optional enhancement; can defer. */
    OPTIONAL,
    /** Cannot be auto-fixed — manual redesign required. */
    REWRITE_REQUIRED
}
