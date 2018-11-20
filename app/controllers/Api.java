package controllers;

import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.data.DynamicForm;
import play.data.FormFactory;
import play.http.HttpEntity;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.ResponseHeader;
import play.mvc.Result;
import utils.ImagePngTastic;
import utils.Tools;

import javax.inject.Inject;
import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class Api extends Controller {

    private static final Logger logger = LoggerFactory.getLogger("Api");

    @Inject
    private Config config;

    @Inject
    private FormFactory formFactory;

    /**
     * Upload files and save to storage folder
     */
    public Result upload()
    {
        Http.MultipartFormData<File> multipartFormData = request().body().asMultipartFormData();
        JsonNode json = request().body().asJson();
        DynamicForm requestData = formFactory.form().bindFromRequest();
        String link = requestData.get("link");

        if(json != null)
           return  uploadJson(json);
        else if(link != null)
            return uploadLink(link);
        else
            return uploadMultipart(multipartFormData);
    }

    public Result preview(String hash) {
        File previewFolder = new File(config.getString("app.dirs.tmp.preview"));
        File output100 = new File(previewFolder, hash + ".png");

        java.nio.file.Path path = output100.toPath();
        Source<ByteString, ?> source = FileIO.fromPath(path);

        return new Result(
                new ResponseHeader(200, Collections.emptyMap()),
                new HttpEntity.Streamed(source, Optional.empty(), Optional.of("image/png"))
        );
    }

    private Result uploadMultipart(Http.MultipartFormData<File> multipartFormData) {
        List<Http.MultipartFormData.FilePart<File>> uploads = multipartFormData.getFiles();
        if(uploads.size() == 0) {
            logger.error("Missing file");
            return badRequest();
        }

        ObjectNode response = Tools.getMapper().createObjectNode();

        try {
            List<String> files = new ArrayList<>();
            for (Http.MultipartFormData.FilePart<File> upload : uploads) {
                File file = upload.getFile();
                if (file.length() == 0)
                    continue;

                files.add(this.upload(upload.getFile()));
            }

            response.put("success", true);
            response.set("items", Json.toJson(files));

            logger.debug("[Upload]: " + response.toString());
            return ok(response);

        } catch (Exception ex) {
            response.put("success", false);
            response.put("message", ex.getMessage());
            return internalServerError(response);
        }
    }

    private Result uploadJson(JsonNode json) {
        if(!json.has("img")) {
            logger.error("Can't find tag img");
            return badRequest();
        }

        String base64 = json.get("img").asText();
        if(Strings.emptyToNull( base64 ) == null) {
            logger.error("img is empty");
            return badRequest();
        }

        ObjectNode response = Tools.getMapper().createObjectNode();

        try {
            String[] strings = base64.split(",");
            String extension;
            switch (strings[0]) {//check image's extension
                case "data:image/jpeg;base64":
                    extension = "jpeg";
                    break;
                case "data:image/png;base64":
                    extension = "png";
                    break;
                default://should write cases for more images types
                    extension = "jpg";
                    break;
            }

            File storageFolder = new File(config.getString("app.dirs.tmp.root"));

            byte[] data = DatatypeConverter.parseBase64Binary(strings[1]);
            File file = new File(storageFolder, "img." + extension);
            OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file));
            outputStream.write(data);
            outputStream.close();
            String hash = upload(file);

            response.put("success", true);
            response.set("items", Json.toJson(Collections.singletonList(hash)));

        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            return internalServerError();
        }

        return ok(response);
    }

    private Result uploadLink(String link) {

        String ext = Tools.getFileExtension(link);
        File storageFolder = new File(config.getString("app.dirs.tmp.root"));
        File file = new File(storageFolder, "img." + ext);

        ObjectNode response = Tools.getMapper().createObjectNode();

        try (BufferedInputStream in = new BufferedInputStream(new URL(link).openStream());

            FileOutputStream fileOutputStream  = new FileOutputStream(file)) {
            byte dataBuffer[] = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }

            fileOutputStream.close();
            String hash = upload(file);

            response.put("success", true);
            response.set("items", Json.toJson(Collections.singletonList(hash)));

        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            return internalServerError();
        }

        return ok(response);
    }

    /**
     * Save file to storage folder
     */
    private String upload(File tmpFile) throws IOException {
        String ext = Tools.getFileExtension(tmpFile.getName());
        String hash = Tools.getRandomHash();

        File storageFolder = new File(config.getString("app.dirs.tmp.upload"));
        File previewFolder = new File(config.getString("app.dirs.tmp.preview"));

        File newFile = new File(storageFolder, hash + "." + ext);
        File output100 = new File(previewFolder, hash + ".png");

        logger.debug("Upload new file: " + newFile.getAbsolutePath());

        Files.move(tmpFile.toPath(), newFile.toPath());

        new ImagePngTastic(newFile)
                .<ImagePngTastic>getResizedToSquare(100, 0)
                .saveForWeb(output100);

        return hash;
    }
}
