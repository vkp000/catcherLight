package com.incoin.demo.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mutable state of a single user's grab session.
 * Stored inside UserSession → persisted in Redis.
 * Fields are volatile or synchronized where needed because
 * the WorkerService thread and HTTP request threads both read/write this.
 */
@Data
public class GrabState {

    private volatile boolean running = false;
    private volatile int grabbed = 0;
    private int target = 0;
    private double minAmount = 0;
    private double maxAmount = Double.MAX_VALUE;

    /**
     * IDLE | RUNNING | DONE | STOPPED | ERROR
     */
    private String status = "IDLE";

    /**
     * Rolling log lines sent to the frontend via GET /grab/status or WebSocket.
     * Collections.synchronizedList ensures thread-safe appends.
     */
    private List<String> logs = Collections.synchronizedList(new ArrayList<>());
}
