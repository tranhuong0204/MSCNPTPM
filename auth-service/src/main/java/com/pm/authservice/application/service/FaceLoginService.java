package com.pm.authservice.application.service;

import com.google.gson.Gson;
import com.pm.authservice.application.dto.EmbeddingResultDto;
import com.pm.authservice.application.dto.FaceRequest;
import com.pm.authservice.application.dto.FaceResponse;
import com.pm.authservice.domain.FaceData;
import com.pm.authservice.domain.User;
import com.pm.authservice.infrastructure.exception.UserNotFoundException;
import com.pm.authservice.infrastructure.repo.FaceDataRepository;
import com.pm.authservice.infrastructure.repo.UserRepository;
import com.pm.authservice.infrastructure.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springdoc.api.ErrorMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FaceLoginService {
    private final FaceDataRepository faceRepo;
    private final UserRepository userRepo;
    private final FaceAIClient faceAIClient;

    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

    private final Gson gson = new Gson();

    public void register(String userId, MultipartFile image) throws IOException {
        File tempFile = File.createTempFile("upload-", image.getOriginalFilename());
        image.transferTo(tempFile);

        //***
        User user = userRepo.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UserNotFoundException(ErrorMessage.class.getName()));

        double[] embedding = faceAIClient.getOriginalEmbedding(tempFile);

        FaceData face = FaceData.builder()
                .faceEncoding(gson.toJson(embedding))
                .createdAt(LocalDateTime.now())
                .user(user)
                .build();

        faceRepo.save(face);
    }

    public FaceResponse recognize(MultipartFile imageFile) throws IOException {

        File tempFile = File.createTempFile("upload-", imageFile.getOriginalFilename());
        imageFile.transferTo(tempFile);
//        double[] input = faceAIClient.getAttendanceEmbedding(tempFile);
        System.out.println("===== START RECOGNIZE =====");

        System.out.println("Calling Python AI...");
        EmbeddingResultDto result = faceAIClient.getLoginEmbedding(tempFile);
        System.out.println("===== PYTHON AI DONE =====");

        if (result.getEmbedding() == null) {
            return FaceResponse.builder()
                    .name("Unknown")
                    .confidence(0.0)
                    .message(result.getStatus())
                    .build();
        }

        double[] input = result.getEmbedding();

        //đang hard-code best đúng k
        double best = 0;
        User bestUser = null;

        List<FaceData> faceDataList = faceRepo.findAll();

        for (FaceData f : faceDataList) {
            //danh cho du lieu embbeding da dua ve mang 1D
            double[] db = gson.fromJson(f.getFaceEncoding(), double[].class);

            double score = cosine(input, db);

            //đoạn này nhìn hơi thừa
            if (score > best) {
                best = score;
                bestUser = f.getUser();
            }
        }

//        String name = (best > 0.6 && bestUser != null)
        boolean matched = best > 0.6 && bestUser != null;

        String name = matched
                ? bestUser.getEmail()
                : "Unknown";

        String accessToken = null;
        String refreshToken = null;

        if (matched) {
            String email = bestUser.getEmail();
            String role = bestUser.getRole().name();

            accessToken = jwtUtil.generateToken(email, role);

            refreshToken = refreshTokenService
                    .createRefreshToken(email, role)
                    .getToken();
        }

        return FaceResponse.builder()
                .userId(matched ? bestUser.getId().toString() : null)
                .name(name)
                .confidence(best)
                .message(result.getStatus())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    private double cosine(double[] a, double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vectors must be of the same length");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += Math.pow(a[i], 2);
            normB += Math.pow(b[i], 2);
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

}
