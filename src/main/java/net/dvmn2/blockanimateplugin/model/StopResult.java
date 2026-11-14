package net.dvmn2.blockanimateplugin.model;

/**
 * Outcome of stopping an animation. {@link #QUEUE_CLEARED} means the animation was not playing but its queued
 * operations were removed.
 */
public enum StopResult { STOPPED, NOT_FOUND, NOT_RUNNING, QUEUE_CLEARED }
