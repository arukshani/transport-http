package org.wso2.transport.http.netty.multipartdecoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.util.CharsetUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class MultipartTestUtil {
    /**
     * Create a json body part.
     *
     * @param request Represent a HttpRequest
     * @return InterfaceHttpData which includes the data object that needs to be decoded
     * @throws IOException
     */
    public static InterfaceHttpData createJSONAttribute(HttpRequest request, String jsonContent, HttpDataFactory dataFactory) throws IOException {
        ByteBuf content = Unpooled.buffer();
        ByteBufOutputStream byteBufOutputStream = new ByteBufOutputStream(content);
        byteBufOutputStream.writeBytes(jsonContent);
        return dataFactory.createAttribute(request, "json", content.toString(CharsetUtil.UTF_8));
    }

    /**
     * Include a file as a body part.
     *
     * @param request Represent a HttpRequest
     * @return InterfaceHttpData which includes the data object that needs to be decoded
     * @throws IOException
     */
    public static InterfaceHttpData createFileUpload(HttpRequest request, HttpDataFactory dataFactory) throws IOException {
        File file = File.createTempFile("upload", ".txt");
        file.deleteOnExit();
        BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file));
        bufferedWriter.write("Example file to be posted");
        bufferedWriter.close();
        FileUpload fileUpload = dataFactory
                .createFileUpload(request, "file", file.getName(), "plain/text", "7bit", null, file.length());
        fileUpload.setContent(file);
        return fileUpload;
    }

    public static File getFileToUpload() throws IOException {
        File file = File.createTempFile("upload", ".txt");
        file.deleteOnExit();
        BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file));
        bufferedWriter.write("Example file to be posted");
        bufferedWriter.close();
        return file;
    }
}
