package com.bpflow.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
@Slf4j
public class ChatbotController {

    @Value("${AI_SERVICE_URL:http://ai-service:8000}")
    private String aiServiceUrl;

    private final RestTemplate restTemplate;

    @PostMapping("/message")
    public ResponseEntity<Object> chat(
            @RequestBody Map<String, Object> body) {
        try {
            String url = aiServiceUrl + "/chat/chat";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
            ResponseEntity<Object> resp = restTemplate.postForEntity(url, req, Object.class);
            return ResponseEntity.ok(resp.getBody());
        } catch (Exception e) {
            log.warn("AI service unavailable, using fallback: {}", e.getMessage());
            // Fallback: friendly offline response
            return ResponseEntity.ok(Map.of(
                "reply",  "El servicio de IA no está disponible en este momento. Por favor intenta más tarde.",
                "type",   "text",
                "data",   Map.of()
            ));
        }
    }
}
