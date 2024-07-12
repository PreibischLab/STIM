package util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

public class LoggerUtil {
    private static final Logger logger = LogManager.getLogger("STIM");
    private static final String LOG_LEVEL_ENV_VAR = "STIM_LOG_LEVEL";
    private static final Level DEFAULT_LOG_LEVEL = Level.INFO;

    static {
        initializeLogLevel();
    }

    private LoggerUtil() {}

    private static void initializeLogLevel() {
        String logLevelStr = System.getenv(LOG_LEVEL_ENV_VAR);
        Level logLevel = parseLogLevel(logLevelStr);
        Configurator.setRootLevel(logLevel);
        logger.info("Log level set to: {}", logLevel);
    }

    private static Level parseLogLevel(String logLevelStr) {
        if (logLevelStr == null || logLevelStr.isEmpty()) {
            return DEFAULT_LOG_LEVEL;
        }

        try {
            return Level.valueOf(logLevelStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid log level '{}' specified. Using default level: {}", logLevelStr, DEFAULT_LOG_LEVEL);
            return DEFAULT_LOG_LEVEL;
        }
    }

    public static Logger getLogger() {
        return logger;
    }
}