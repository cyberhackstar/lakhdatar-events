package com.neelastack.lakhdatar.controller;

import com.neelastack.lakhdatar.security.UserPrincipal;
import com.neelastack.lakhdatar.service.CloudinaryAssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/assets")
@RequiredArgsConstructor
public class AdminAssetController {
    private final CloudinaryAssetService assets;

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<CloudinaryAssetService.UploadResult> upload(@RequestPart("file") MultipartFile file,
                                                                @RequestParam CloudinaryAssetService.Purpose purpose,
                                                                @RequestParam(required = false) UUID eventId,
                                                                @RequestParam(required = false) String organizerSlug,
                                                                Authentication authentication) {
        UserPrincipal p = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(assets.upload(file, purpose, eventId, organizerSlug, p.userId(), p.role()));
    }
}
