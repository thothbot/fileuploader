package services;

import com.typesafe.config.Config;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;

@Singleton
public class S3Client extends MinioClient {

    private static final Logger logger = LoggerFactory.getLogger("services.S3Client");

    @Inject
    public S3Client(Config config) throws Exception {
        super(config.getString("app.s3.host"),
                config.getString("app.s3.key"),
                config.getString("app.s3.secret"));

        logger.info("Connected to S3.");
    }

    public void putObject(String bucket, String key, File file) {

        try {
            putObject(bucket,key, new FileInputStream(file), null);
            logger.debug("Saved to S3 in bucket {}, file: {}", bucket, key);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }
}
