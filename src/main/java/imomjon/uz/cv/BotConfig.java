package imomjon.uz.cv.config;

import com.pengrad.telegrambot.TelegramBot;
import imomjon.uz.cv.GeminiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class BotConfig {

    @Bean
    public TelegramBot telegramBot(@Value("${telegram.bot.token}") String token) {

        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Telegram token topilmadi");
        }

        return new TelegramBot(token);
    }

    @Bean
    public GeminiClient geminiClient(@Value("${gemini.api.key}") String apiKey) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Gemini key topilmadi");
        }

        return new GeminiClient(apiKey);
    }
}
