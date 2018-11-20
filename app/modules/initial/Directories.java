package modules.initial;

import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.Configuration;

import javax.inject.Inject;
import java.io.File;

public class Directories implements Initiable {
    private static final Logger logger = LoggerFactory.getLogger("initial.Directories");
    private static final String ROOT_KEY = "app.dirs";

    /**
     * Create directories
     */
    @Inject
    public Directories(Config config) {
        Config tmpDirs = config.getConfig(ROOT_KEY);
        tmpDirs.entrySet().forEach(entry -> {
            File dir = new File(tmpDirs.getString(entry.getKey()));
            if (dir.mkdirs()) {
                logger.info("Created temporary directory {}", dir.getAbsolutePath());
            }
        });
    }
}
