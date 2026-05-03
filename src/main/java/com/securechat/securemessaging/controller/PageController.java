package com.securechat.securemessaging.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Serves chat.html and index.html with a build-time cache-buster
 * injected into the CSS/JS URLs so the browser always loads fresh assets.
 */
@RestController
public class PageController {

    // Fixed at startup — changes every time the server restarts
    private static final String BUILD_TS = String.valueOf(System.currentTimeMillis());

    @GetMapping(value = {"/", "/index.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> indexPage() throws IOException {
        return serveWithCacheBust("static/index.html");
    }

    @GetMapping(value = "/chat.html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> chatPage() throws IOException {
        return serveWithCacheBust("static/chat.html");
    }

    private ResponseEntity<String> serveWithCacheBust(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        String html = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        // Inject cache-buster into CSS and JS references
        html = html.replace("href=\"style.css\"",  "href=\"style.css?v=" + BUILD_TS + "\"");
        html = html.replace("src=\"script.js\"",   "src=\"script.js?v="  + BUILD_TS + "\"");

        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }
}
