package com.pm.authservice.application.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Data
public class FaceRequest {
    private UUID userId;
    private MultipartFile image;
}
