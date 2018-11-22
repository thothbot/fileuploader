package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.data.DynamicForm;
import play.data.FormFactory;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.S3Client;
import utils.ImagePngTastic;
import utils.Tools;

import javax.inject.Inject;
import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Api extends Controller {

    private static final Logger logger = LoggerFactory.getLogger("Api");

    @Inject
    private Config config;

    @Inject
    private FormFactory formFactory;

    @Inject
    private S3Client s3Client;

    /**
     * Upload files and save to storage folder
     */
    public Result upload()
    {
        Http.MultipartFormData<File> multipartFormData = request().body().asMultipartFormData();
        JsonNode json = request().body().asJson();
        DynamicForm requestData = formFactory.form().bindFromRequest();
        String link = requestData.get("link");

        try {
            if (json != null)
                return uploadJson(json);
            else if (link != null)
                return uploadLink(link);
            else
                return uploadMultipart(multipartFormData);
        } catch (IOException ex) {
            return internalServerError();
        }
    }

    private Result uploadMultipart(Http.MultipartFormData<File> multipartFormData) throws IOException {
        List<Http.MultipartFormData.FilePart<File>> uploads = multipartFormData.getFiles();
        if(uploads.size() == 0) {
            logger.error("Missing file");
            return badRequest();
        }

        ObjectNode response = Tools.getMapper().createObjectNode();

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
    }

    private Result uploadJson(JsonNode json) throws IOException  {
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

        String[] strings = base64.split(",");
        String ext;
        switch (strings[0]) {//check image's extension
            case "data:image/jpeg;base64":
                ext = "jpeg";
                break;
            case "data:image/png;base64":
                ext = "png";
                break;
            default://should write cases for more images types
                ext = "jpg";
                break;
        }

        File uploadTmpFile = File.createTempFile("upload", "."+ ext);
        byte[] data = DatatypeConverter.parseBase64Binary(strings[1]);
        OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(uploadTmpFile));
        outputStream.write(data);
        outputStream.close();
        String hash = upload(uploadTmpFile);

        response.put("success", true);
        response.set("items", Json.toJson(Collections.singletonList(hash)));

        return ok(response);
    }

    private Result uploadLink(String link) throws IOException {
        String ext = Tools.getFileExtension(link);

        File uploadTmpFile = File.createTempFile("upload", "." + ext);

        ObjectNode response = Tools.getMapper().createObjectNode();


        BufferedInputStream in = new BufferedInputStream(new URL(link).openStream());
        FileOutputStream fileOutputStream  = new FileOutputStream(uploadTmpFile);
        byte dataBuffer[] = new byte[1024];
        int bytesRead;
        while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
            fileOutputStream.write(dataBuffer, 0, bytesRead);
        }

        fileOutputStream.close();
        String hash = upload(uploadTmpFile);

        response.put("success", true);
        response.set("items", Json.toJson(Collections.singletonList(hash)));

        return ok(response);
    }

    /**
     * Save file to storage folder
     */
    private String upload(File uploadTmpFile) throws IOException {
        String ext = Tools.getFileExtension(uploadTmpFile.getName());
        String hash = Tools.getRandomHash();

        File previewTmpFile = File.createTempFile(hash, ".png");

        logger.debug("Upload new file: " + uploadTmpFile.getAbsolutePath());

        new ImagePngTastic(uploadTmpFile)
                .<ImagePngTastic>getResizedToSquare(100, 0)
                .saveForWeb(previewTmpFile);

        s3Client.putObject(config.getString("app.s3.bucket.upload"), hash + "." + ext, uploadTmpFile);
        s3Client.putObject(config.getString("app.s3.bucket.preview"), hash + ".png", previewTmpFile);

        uploadTmpFile.delete();
        previewTmpFile.delete();

        return String.format(config.getString("app.cdn.preview"), hash);
    }
}
