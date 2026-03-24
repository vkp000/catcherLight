//package com.incoin.demo.model;
//
//import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
//import lombok.AllArgsConstructor;
//import lombok.Builder;
//import lombok.Data;
//import lombok.NoArgsConstructor;
//
//import java.io.Serializable;
//import java.time.Instant;
//import java.util.HashSet;
//import java.util.Set;
//
//@Data
//@Builder
//@NoArgsConstructor
//@AllArgsConstructor
//@JsonIgnoreProperties(ignoreUnknown = true)
//public class UserSession implements Serializable {
//
//    private String userId;
//    private String incoinToken;
//    private String clientKey;
//    private String clientSecret;
//    private String selectedUpiAddr;
//    private String selectedToolType;
//    private String selectedToolName;
//
//    @Builder.Default
//    private Set<String> processedIds = new HashSet<>();
//
//    @Builder.Default
//    private GrabState grabState = new GrabState();
//
//    private Instant createdAt;
//    private Instant lastActiveAt;
//}






































package com.incoin.demo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserSession implements Serializable {

    private String userId;
    private String incoinToken;
    private String clientKey;
    private String clientSecret;
    private String selectedUpiAddr;
    private String selectedToolType;
    private String selectedToolName;

    /** Base URL of the Incoin server this session authenticated against. */
    private String incoinBaseUrl;   // ← NEW

    @Builder.Default
    private Set<String> processedIds = new HashSet<>();

    @Builder.Default
    private GrabState grabState = new GrabState();

    private Instant createdAt;
    private Instant lastActiveAt;
}