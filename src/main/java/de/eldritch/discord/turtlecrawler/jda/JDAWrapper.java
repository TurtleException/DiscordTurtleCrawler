package de.eldritch.discord.turtlecrawler.jda;

import de.eldritch.discord.turtlecrawler.DiscordTurtleCrawler;
import de.eldritch.discord.turtlecrawler.util.MiscUtil;
import de.eldritch.discord.turtlecrawler.util.logging.NestedToggleLogger;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.managers.Presence;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import javax.security.auth.login.LoginException;
import java.io.*;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

/**
 * Simple wrapper to handle {@link JDA} initialization and manage the {@link PresenceController}.
 */
public class JDAWrapper {
    public static final NestedToggleLogger LOGGER = new NestedToggleLogger("JDA", DiscordTurtleCrawler.LOGGER);
    public static final NestedToggleLogger LOGGER_INTERNAL = new NestedToggleLogger("JDA-internal", LOGGER);
    /**
     * Singleton object to ensure instance uniqueness.
     */
    private static JDAWrapper singleton;

    /**
     * Constantly updates the {@link Presence} of the bot.
     */
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private final PresenceController presenceController;

    /**
     * Discord API authorization token
     */
    private final String token;
    /**
     * JDA instance.
     * @see JDAWrapper#init()
     */
    private JDA jda;

    public JDAWrapper() throws LoginException, IOException {
        this.token = getDiscordToken();
        this.checkSingleton();
        this.init();

        presenceController = new PresenceController(this);

        // only declare singleton when new instance is successfully initialized
        singleton = this;
    }

    private void checkSingleton() {
        if (singleton != null) {
            LOGGER.log(Level.WARNING, "New instance declared!");
            LOGGER.log(Level.INFO, "Shutting down old instance...");

            this.shutdown();

            LOGGER.log(Level.INFO, "OK! New instance can now be safely initialized.");
        }
    }

    /**
     * Builds the {@link JDA} instance.
     */
    private void init() throws LoginException {
        LOGGER.log(Level.INFO, "Initializing...");
        JDABuilder builder = JDABuilder.createDefault(token);

        builder.setMemberCachePolicy(MemberCachePolicy.NONE);
        builder.setStatus(OnlineStatus.IDLE);
        builder.setActivity(null);

        LOGGER.log(Level.INFO, "Building JDA...");
        jda = builder.build();
        LOGGER.log(Level.INFO, "OK!");
    }

    /**
     * Attempts to shut down the {@link JDA} instance.
     */
    public void shutdown() {
        LOGGER.log(Level.WARNING, "Received shutdown command!");

        jda.shutdown();
        try {
            MiscUtil.await(() -> jda.getStatus() == JDA.Status.SHUTDOWN, 10, TimeUnit.SECONDS);
        } catch (TimeoutException | InterruptedException e) {
            LOGGER.log(Level.WARNING, "Failed to await status.");
        }

        if (jda.getStatus() != JDA.Status.SHUTDOWN) {
            LOGGER.log(Level.INFO, "Attempting to force shutdown...");
            jda.shutdownNow();

            try {
                MiscUtil.await(() -> jda.getStatus() == JDA.Status.SHUTDOWN, 2, TimeUnit.SECONDS);
            } catch (TimeoutException | InterruptedException e) {
                LOGGER.log(Level.WARNING, "Failed to await status.");
            } finally {
                LOGGER.log(Level.INFO, "JDA is now shut down.");
            }
        } else {
            LOGGER.log(Level.INFO, "JDA is now shut down");
        }

        jda = null;
    }

    /**
     * Provides the wrapped {@link JDA} instance. While this method is technically nullable the JDA is built when
     * constructing this wrapper. If building the JDA fails an exception is thrown.
     * @return JDA instance.
     */
    public JDA getJDA() {
        return jda;
    }

    private static String getDiscordToken() throws IOException {
        Properties properties = new Properties();

        File file = new File(DiscordTurtleCrawler.DIR, "token.properties");
        if (file.createNewFile()) {
            properties.setProperty("apiToken", "PLEASE PUT YOUR TOKEN HERE");
            properties.store(new FileWriter(file), null);
            return null;
        }

        properties.load(new FileReader(file));
        return properties.getProperty("apiToken", null);
    }
}
