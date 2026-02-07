package imomjon.uz.cv;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class TelegramBotRunner implements ApplicationRunner {

    private static final long ADMIN_CHAT_ID = 1604335926L;

    private final TelegramBot bot;
    private final GeminiClient gemini;

    // 1) Knowledge cache (1 marta yuklanadi)
    private volatile String knowledge = "";

    // 2) Umumiy worker pool (Gemini call + send msg shu yerda)
    private final ThreadPoolExecutor workerPool;

    // 3) Har chat uchun ketma-ket bajarish (javoblar aralashmasligi uchun)
    private final ConcurrentHashMap<Long, CompletableFuture<Void>> chatChains = new ConcurrentHashMap<>();

    public TelegramBotRunner(TelegramBot bot, GeminiClient gemini) {
        this.bot = bot;
        this.gemini = gemini;

        int threads = Math.max(4, Runtime.getRuntime().availableProcessors());
        int queueSize = 200;

        this.workerPool = new ThreadPoolExecutor(
                threads,
                threads,
                30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueSize),
                new NamedThreadFactory("bot-worker-"),
                new ThreadPoolExecutor.CallerRunsPolicy() // backpressure: queue to'lsa listener thread ishlatadi
        );
    }

    @Override
    public void run(ApplicationArguments args) {
        System.out.println("Bot ishga tushdi ✅");

        // Knowledge'ni bir marta yuklab qo'yamiz
        this.knowledge = loadKnowledge();

        bot.setUpdatesListener(updates -> {
            for (Update u : updates) {
                if (u.message() == null || u.message().text() == null) continue;

                long chatId = u.message().chat().id();
                String userText = u.message().text().trim();

                // Og'ir ishlarni per-chat chain orqali ketma-ket navbatlaymiz
                enqueuePerChat(chatId, () -> handleMessage(u, chatId, userText));
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }

    private void enqueuePerChat(long chatId, Runnable task) {
        chatChains.compute(chatId, (id, prev) -> {
            CompletableFuture<Void> start = (prev == null) ? CompletableFuture.completedFuture(null) : prev;
            // prev tugagach workerPool’da task ishlasin
            CompletableFuture<Void> next = start.thenRunAsync(task, workerPool)
                    // xatoni chain'ni sindirmaslik uchun yutib yuboramiz
                    .exceptionally(ex -> null);
            return next;
        });
    }

    private void handleMessage(Update u, long chatId, String userText) {
        String answer;
        try {
            if ("/start".equals(userText)) {
                String prompt =
                        "Faqat o'zbek tilida gapir.\n\n" +
                                knowledge +
                                "\n\nFoydalanuvchini kutib ol.";

                answer = gemini.generateText(prompt);
                sendText(chatId, answer);
                sendReportToAdmin(u, userText, answer);
                return;
            }

            String prompt =
                    "You MUST answer only in Uzbek language.\n" +
                            "Never answer in English or Russian.\n" +
                            "You are assistant of Boy oka.\n\n" +
                            knowledge +
                            "\n\nFoydalanuvchi savoli:\n" + userText;

            answer = gemini.generateText(prompt);

            System.out.println("User: " + userText);
            System.out.println("Bot: " + answer);

            sendText(chatId, answer);
            sendReportToAdmin(u, userText, answer);

        } catch (Exception e) {
            String fallback = "Afsus, hozir limit tugab qolgan bo‘lishi mumkin. Birozdan so‘ng yana urinib ko‘ring.";
            sendText(chatId, fallback);
            sendReportToAdmin(u, userText, fallback);
        }
    }

    // Telegram 4096 limit: uzun javoblarni bo'lib yuboramiz
    private void sendText(long chatId, String text) {
        if (text == null || text.isBlank()) {
            bot.execute(new SendMessage(chatId, "Tushunmadim, yana bir marta yozib bera olasizmi?"));
            return;
        }

        int limit = 4000; // biroz zaxira qoldiramiz
        if (text.length() <= limit) {
            bot.execute(new SendMessage(chatId, text));
            return;
        }

        int from = 0;
        while (from < text.length()) {
            int to = Math.min(from + limit, text.length());
            String part = text.substring(from, to);
            bot.execute(new SendMessage(chatId, part));
            from = to;
        }
    }

    private void sendReportToAdmin(Update u, String userText, String botAnswer) {
        long fromChatId = u.message().chat().id();
        if (fromChatId == ADMIN_CHAT_ID) return;

        String report =
                "Ismi: " + userDisplay(u) + "\n" +
                        "Nima yozgani: " + userText + "\n\n" +
                        "Bot: " + botAnswer;

        try {
            sendText(ADMIN_CHAT_ID, report);
        } catch (Exception ignored) {}
    }

    private String userDisplay(Update u) {
        var from = u.message().from();
        if (from == null) return "Noma'lum";
        String first = from.firstName() != null ? from.firstName() : "";
        String last = from.lastName() != null ? from.lastName() : "";
        String full = (first + " " + last).trim();
        String username = from.username() != null ? ("@" + from.username()) : "";
        if (!full.isBlank() && !username.isBlank()) return full + " (" + username + ")";
        if (!full.isBlank()) return full;
        if (!username.isBlank()) return username;
        return "ID:" + from.id();
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

    @PreDestroy
    public void shutdown() {
        workerPool.shutdown();
    }

    // Oddiy thread name qo'yish uchun
    static class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger idx = new AtomicInteger(1);

        NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName(prefix + idx.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
