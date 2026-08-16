package com.pm.authservice.interfaces;

import com.pm.authservice.application.dto.AuthResponse;
import com.pm.authservice.application.dto.FaceRequest;
import com.pm.authservice.application.dto.FaceResponse;
import com.pm.authservice.application.dto.LoginRequestDTO;
import com.pm.authservice.application.service.AuthService;
import com.pm.authservice.application.service.FaceLoginService;
import com.pm.authservice.application.service.RefreshTokenService;
import com.pm.authservice.application.service.UserService;
import com.pm.authservice.domain.User;
import com.pm.authservice.infrastructure.exception.UserAlreadyExistsException;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final FaceLoginService faceLoginService;

    @PostMapping("/login")
    @Operation(summary = "Dang nhap va tao access + refresh token")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequestDTO request) {
        Optional<AuthResponse> authResponse = authService.authenticate(request);
        return authResponse
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @PostMapping(value = "/loginByFace",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @Operation(summary = "Dang nhap bang khuon mat va tao access + refresh token")
    public ResponseEntity<FaceResponse> loginByFace(@ModelAttribute FaceRequest req) throws IOException {
        Optional<FaceResponse> faceResponse = Optional.ofNullable(faceLoginService.recognize(req.getImage()));
        return faceResponse
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @PostMapping(value = "/registerByFace",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @Operation(summary = "Dang ky khuon mat")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<String> registerFace(@ModelAttribute FaceRequest req) throws IOException {
        faceLoginService.register(req);
        return ResponseEntity.ok("Dang ky thanh cong");
    }

    @PostMapping("/signup")
    @Operation(summary = "Dang ky nguoi dung")
    public ResponseEntity<User> register(@Valid @RequestBody User user) {
        if (userService.existsByEmail(user.getEmail())) {
            throw new UserAlreadyExistsException(user.getEmail());
        }

        userService.createUser(user);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/validate")
    @Operation(summary = "Xac thuc access token va tra ve identity")
    public ResponseEntity<Map<String, Object>> validateToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        Map<String, Object> response = new HashMap<>();
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.put("status", HttpStatus.UNAUTHORIZED.value());
            response.put("error", "Thieu hoac sai dinh dang header Authorization");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String token = authHeader.substring(7);
        Claims claims = authService.validateToken(token);
        if (claims == null) {
            response.put("status", HttpStatus.UNAUTHORIZED.value());
            response.put("error", "Token khong hop le");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String email = claims.getSubject();
        Optional<User> user = userService.findByEmail(email);

        response.put("status", HttpStatus.OK.value());
        response.put("message", "Token hop le");
        response.put("role", claims.get("role", String.class));
        response.put("email", email);
        response.put("userId", user.map(u -> u.getId().toString()).orElse(null));
        response.put("fullName", null);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Tao access token moi bang refresh token")
    public ResponseEntity<Map<String, String>> refreshToken(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Thieu refreshToken"));
        }

        return authService.refreshAccessToken(refreshToken)
                .map(token -> ResponseEntity.ok(Map.of("accessToken", token)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Refresh token da het han hoac khong hop le")));
    }

    @DeleteMapping("/logout")
    @Operation(summary = "Dang xuat")
    public ResponseEntity<String> logout(@RequestParam(required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            return ResponseEntity.ok("Khong co token de xoa");
        }
        refreshTokenService.deleteByToken(refreshToken);
        return ResponseEntity.ok("Xoa refresh token thanh cong");
    }

    @DeleteMapping("/logout/all")
    @Operation(summary = "Dang xuat tat ca")
    public ResponseEntity<String> logoutAll(@RequestParam(required = false) String email) {
        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest().body("Thieu email");
        }
        refreshTokenService.deleteByEmail(email);
        return ResponseEntity.ok("Da xoa tat ca refresh token cua nguoi dung: " + email);
    }

    @PostMapping("/reset")
    @Operation(summary = "Reset/change password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody LoginRequestDTO change) {
        boolean reset = userService.resetPassword(change);
        if (reset) {
            return ResponseEntity.ok("Dat lai mat khau thanh cong");
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Dat lai mat khau that bai");
    }

    @GetMapping("/user")
    @Operation(summary = "Xem danh sach user")
    public ResponseEntity<List<User>> getAll() {
        return ResponseEntity.ok(userService.getAll());
    }
}
