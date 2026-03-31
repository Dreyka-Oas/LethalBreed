package oas.work.lethalbreed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModLogger {
    public static final Logger LOGGER = LoggerFactory.getLogger(ModConstants.MOD_ID);

    public static void info(String message) {
        LOGGER.info(message);
    }

    public static void warn(String message) {
        LOGGER.warn(message);
    }

    public static void error(String message) {
        LOGGER.error(message);
    }
}
