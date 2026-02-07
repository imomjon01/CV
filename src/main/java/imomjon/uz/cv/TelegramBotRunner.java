package imomjon.uz.cv;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;


import java.nio.charset.StandardCharsets;

@Component
public class TelegramBotRunner implements ApplicationRunner {

    private final TelegramBot bot;
    private final GeminiClient gemini;

    public TelegramBotRunner(TelegramBot bot, GeminiClient gemini) {
        this.bot = bot;
        this.gemini = gemini;
    }

    @Override
    public void run(ApplicationArguments args) {

        System.out.println("Bot ishga tushdi ✅");

        bot.setUpdatesListener(updates -> {

            for (Update u : updates) {

                if (u.message() == null || u.message().text() == null)
                    continue;

                long chatId = u.message().chat().id();
                String userText = u.message().text();

                String knowledge = loadKnowledge();

                try {

                    if ("/start".equals(userText)) {

                        String prompt =
                                "Faqat o'zbek tilida gapir.\n\n" +
                                        knowledge +
                                        "\n\nFoydalanuvchini kutib ol.";

                        String answer = gemini.generateText(prompt);

                        bot.execute(new SendMessage(chatId, answer));
                        continue;
                    }



                    String prompt =
                            "You MUST answer only in Uzbek language.\n" +
                                    "Never answer in English or Russian.\n" +
                                    "You are assistant of Boy oka.\n\n" +

                                    knowledge +

                                    "\n\nFoydalanuvchi savoli:\n" + userText;


                    String answer = gemini.generateText(prompt);
                    System.out.printf(userText);
                    System.out.printf(answer);

                    bot.execute(new SendMessage(chatId, answer));

                } catch (Exception e) {

                    bot.execute(new SendMessage(chatId,
                            "Afsus limit tugab qoldi birozdan son urunib koring!"));
                }
            }

            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }

    private String loadKnowledge() {
        try {
            var res = new ClassPathResource("imomjon.txt");

            try (var in = res.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }

        } catch (Exception e) {
            return "";
        }
    }
}
