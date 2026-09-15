package com.vesta.rest_api.vestaboard;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class Vestaboard {

    private String key;
    private Gson gson;
    private static final Logger LOG = LogManager.getLogger(Vestaboard.class.getName());

    public Vestaboard(String key) {
        this.key = key;
        this.gson = new Gson();
    }

    /**
     * Retrieves the state from the Vestaboard API.
     *
     * This method sends an HTTP GET request to the Vestaboard API endpoint and
     * returns the response as a string.
     * The request includes the necessary authentication header using the provided
     * read-write key.
     *
     * @return A string representing the state retrieved from the Vestaboard API, or
     *         null if an exception occurs.
     */
    public String getState() {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet request = new HttpGet("https://rw.vestaboard.com/");
            request.setHeader("X-Vestaboard-Read-Write-Key", this.key);

            HttpResponse response = client.execute(request);
            String result = EntityUtils.toString(response.getEntity());

            Map<String, Map<String, String>> json = gson.fromJson(
                    result,
                    Map.class);
            String state = json.get("currentMessage").get("layout");
            return state;
        } catch (Exception e) {
            LOG.error("Failed to get Vestaboard state: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Sends a message to the Vestaboard.
     *
     * @param message The message to be sent to the Vestaboard.
     * @return The response from the Vestaboard API as a String, or null if an error
     *         occurs.
     */
    public String sendMessage(String message) {
        // send a message with a string
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost request = new HttpPost("https://rw.vestaboard.com");
            request.setHeader("X-Vestaboard-Read-Write-Key", this.key);
            request.setHeader("Content-Type", "application/json");
            final String messageBody = String.format(
                    "{\"text\": \"%s\"}",
                    message);
            LOG.debug("Sending message to Vestaboard");
            final StringEntity body = new StringEntity(messageBody);
            request.setEntity(body);

            try {
                HttpResponse response = client.execute(request);
                String result = EntityUtils.toString(response.getEntity());
                return result;
            } catch (Exception e) {
                LOG.warn("Failed to send message to Vestaboard: {}. This might be because you are trying to submit text that is already on the board.", e.getMessage());
                return null;
            }
        } catch (Exception e) {
            LOG.error("Error creating HTTP client for Vestaboard: {}", e.getMessage());
            return null;
        }
    }

    public HashMap<String, String> sendRaw(String body) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost request = new HttpPost("https://rw.vestaboard.com");
            request.setHeader("X-Vestaboard-Read-Write-Key", this.key);
            request.setHeader("Content-Type", "application/json");
            final StringEntity requestBody = new StringEntity(body);
            request.setEntity(requestBody);

            try {
                HttpResponse response = client.execute(request);
                String result = EntityUtils.toString(response.getEntity());
                HashMap<String, String> responseMap = gson.fromJson(result, HashMap.class);
                return responseMap;
            } catch (Exception e) {
                LOG.warn("Failed to send raw data to Vestaboard: {}. This might be because you are trying to submit text that is already on the board.", e.getMessage());
                return null;
            }
        } catch (Exception e) {
            LOG.error("Error creating HTTP client for Vestaboard: {}", e.getMessage());
            return null;
        }
    }
}
