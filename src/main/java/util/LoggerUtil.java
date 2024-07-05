package util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

public class LoggerUtil {
    private static final Logger logger = LogManager.getLogger("STIM");

    private LoggerUtil() {}

    public static void setLogLevel(String logLevel) {
        Level level = Level.toLevel(logLevel, Level.INFO);
        Configurator.setRootLevel(level);
    }

    public static Logger getLogger() {
        return logger;
    }
}