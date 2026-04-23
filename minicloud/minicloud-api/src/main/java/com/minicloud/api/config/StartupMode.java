package com.minicloud.api.config;

/**
 * Defines the two startup modes for MiniCloud.
 * WEB     = headless REST API mode (default).
 * DESKTOP = Swing desktop UI + embedded REST API.
 */
public enum StartupMode {
    WEB,
    DESKTOP
}