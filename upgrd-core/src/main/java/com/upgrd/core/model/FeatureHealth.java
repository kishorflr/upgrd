package com.upgrd.core.model;

/**
 * Runtime health inferred from combined access, server, out, and application logs.
 */
public enum FeatureHealth {
    UNOBSERVED,
    HEALTHY,
    BROKEN
}
