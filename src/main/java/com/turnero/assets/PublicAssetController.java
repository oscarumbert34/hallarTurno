package com.turnero.assets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/public/assets")
public class PublicAssetController {

    private static final MediaType SVG_MEDIA_TYPE = MediaType.parseMediaType("image/svg+xml");
    private static final String HALLARTURNO_LOGO_PATH = "assets/logo_hallarturno.svg";

    @GetMapping(value = "/logo-hallarturno.svg", produces = "image/svg+xml")
    public ResponseEntity<Resource> hallarturnoLogo() {
        ClassPathResource logo = new ClassPathResource(HALLARTURNO_LOGO_PATH);
        if (!logo.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(SVG_MEDIA_TYPE)
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .body(logo);
    }
}
