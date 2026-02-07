package imomjon.uz.cv;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiResponse {
    public List<Candidate> candidates;

    public String firstTextOrNull() {
        if (candidates == null || candidates.isEmpty()) return null;
        Candidate c = candidates.get(0);
        if (c.content == null || c.content.parts == null || c.content.parts.isEmpty())
            return null;
        return c.content.parts.get(0).text;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Candidate {
        public Content content;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Content {
        public List<Part> parts;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Part {
        public String text;
    }
}
