package edu.senior.demo;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
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

    // Subscribe client to SSE
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

    // Notify clients with a random number
    @PostMapping("/notify")
    public void notifyClients() {
        if (emitters.isEmpty()) {
            logger.warning("No clients subscribed to receive data!");
            return;
        }
        double randomNumber = 10;
        logger.info("Notifying clients with random number: " + randomNumber);
        sendToClients(randomNumber);
    }

    // Receive updated data from client
    @PostMapping("/update")
    public String updateData(@RequestParam int data) {
        this.clientData = data;
        logger.info("Client data updated to: " + clientData);
        return "Data updated to: " + clientData;
    }

    // Send updated data back to clients
    @PostMapping("/get-updated-data")
    public void sendUpdatedData() {
        if (emitters.isEmpty()) {
            logger.warning("No clients subscribed to receive updated data!");
            return;
        }
        logger.info("Sending updated client data to clients: " + clientData);
        sendToClients(clientData);
    }

    private void sendToClients(Object message) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("data-event")
                        .data(message, MediaType.APPLICATION_JSON));
                logger.info("Message sent to client: " + message);
            } catch (IOException e) {
                emitters.remove(emitter);
                logger.warning("Failed to send message to client. Removing emitter.");
            }
        }
    }
}
