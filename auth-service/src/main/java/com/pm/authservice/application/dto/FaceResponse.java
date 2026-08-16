package com.pm.authservice.application.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Builder
@Getter
@Setter
public class FaceResponse {
    private String userId;
    private String name;
    private double confidence;
    private String message;
    private String accessToken;
    private String refreshToken;
}
