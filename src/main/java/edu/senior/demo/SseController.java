package edu.senior.demo;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/sse")
public class SseController {

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private int clientData = 0;
    private static final Logger logger = Logger.getLogger(SseController.class.getName());

    // Separate CORS configuration class
    @Configuration
    public static class CorsConfig implements WebMvcConfigurer {
        @Override
        public void addCorsMappings(CorsRegistry registry) {
            registry.addMapping("/**")
                    .allowedOrigins("*")
                    .allowedMethods("*")
                    .allowedHeaders("*")
                    .maxAge(3600);
        }
    }

    public static class NotifyRequest {
        private int message;

        public int getMessage() {
            return message;
        }

        public void setMessage(int message) {
            this.message = message;
        }
    }

    @GetMapping("/subscribe")
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);

        logger.info("New client subscribed! Total clients: " + emitters.size());

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            logger.info("Client disconnected. Remaining clients: " + emitters.size());
        });

        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            logger.warning("Client timeout. Remaining clients: " + emitters.size());
        });

        return emitter;
    }

    @PostMapping("/notify")
    public ResponseEntity<String> notifyClients(@RequestBody NotifyRequest request) {
        if (emitters.isEmpty()) {
            logger.warning("No clients subscribed to receive data!");
            return ResponseEntity.badRequest().body("No clients connected");
        }

        double mNumber = request.getMessage();
        logger.info("Notifying clients with number: " + mNumber);
        sendToClients(mNumber);
        return ResponseEntity.ok("Notification sent");
    }

    @GetMapping("/getclientdata")
    public ResponseEntity<Integer> getClientData() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(clientData);
    }

    @PostMapping("/update")
    public ResponseEntity<Void> updateData(@RequestParam int data) {
        clientData = data;
        logger.info("Updated client data to: " + data);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/get-updated-data")
    public ResponseEntity<String> sendUpdatedData() {
        if (emitters.isEmpty()) {
            logger.warning("No clients subscribed to receive updated data!");
            return ResponseEntity.badRequest().body("No active connections");
        }

        logger.info("Sending updated client data to clients: " + clientData);
        sendToClients(clientData);
        return ResponseEntity.ok("Data update initiated");
    }

    private void sendToClients(Object message) {
        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("data-event")
                        .data(message, MediaType.APPLICATION_JSON));
                logger.info("Message sent to client: " + message);
            } catch (IOException e) {
                emitter.completeWithError(e);
                emitters.remove(emitter);
                logger.warning("Removed failed emitter: " + e.getMessage());
            }
        });
    }
}