package imomjon.uz.cv;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class GeminiClient {

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String apiKey;
    private final String model;

    public GeminiClient(String apiKey) {
        this(apiKey, "gemini-2.5-flash");
    }

    public GeminiClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    public String generateText(String prompt) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + apiKey;
        String json = """
                {
                  "contents": [{
                    "role": "user",
                    "parts": [{"text": %s }]
                  }]
                }
                """.formatted(mapper.writeValueAsString(prompt));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException("Gemini error: " + resp.body());
        }
        GeminiResponse parsed = mapper.readValue(resp.body(), GeminiResponse.class);
        String text = parsed.firstTextOrNull();
        if (text == null || text.isBlank()) {
            return "Javob topilmadi 😅";
        }
        return text.trim();
    }
}
