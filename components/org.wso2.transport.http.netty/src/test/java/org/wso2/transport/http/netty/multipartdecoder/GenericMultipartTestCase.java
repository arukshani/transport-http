package org.wso2.transport.http.netty.multipartdecoder;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.messaging.exceptions.ServerConnectorException;
import org.wso2.transport.http.netty.config.ChunkConfig;
import org.wso2.transport.http.netty.contractimpl.HttpWsServerConnectorFuture;
import org.wso2.transport.http.netty.listener.SourceHandler;
import org.wso2.transport.http.netty.message.HttpBodyPart;
import org.wso2.transport.http.netty.message.multipart.GenericMultipartEncoder;

import java.util.List;

public class GenericMultipartTestCase {

    private static final String jsonContent = "{key:value, key2:value2}";
    private final HttpDataFactory dataFactory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);
    private EmbeddedChannel channel;
    private HttpWsServerConnectorFuture httpWsServerConnectorFuture = new HttpWsServerConnectorFuture();
    private GenericMultipartContentListener listener;

    @BeforeClass
    public void setup() throws Exception {
        channel = new EmbeddedChannel();
        channel.pipeline().addLast(new HttpResponseDecoder());
        channel.pipeline().addLast(new HttpRequestEncoder());
        channel.pipeline().addLast(new SourceHandler(httpWsServerConnectorFuture, null, ChunkConfig.ALWAYS));
        listener = new GenericMultipartContentListener();
        httpWsServerConnectorFuture.setHttpConnectorListener(listener);
    }


    /*@Test(description = "Test whether multipart/mixed can be decoded properly")
    public void testMultipartMixed() throws Exception {
        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
        request.headers().add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.MULTIPART_MIXED);
        GenericMultipartEncoder encoder = new GenericMultipartEncoder(dataFactory, request, true);
        encoder.addBodyHttpData(MultipartTestUtil.createJSONAttribute(request, jsonContent, dataFactory));
        encoder.addBodyHttpData(MultipartTestUtil.createFileUpload(request, dataFactory));
        request = encoder.finalizeRequest();
        sendMultipartRequest(request, encoder);

        boolean isMultipart = listener.isMultipart();
        Assert.assertEquals(isMultipart, true);
        List<HttpBodyPart> httpBodyParts = listener.getMultiparts();
        Assert.assertNotNull(httpBodyParts, "Received http body parts are null");
        String jsonPart = new String(httpBodyParts.get(0).getContent());
        Assert.assertEquals(jsonPart, jsonContent, "Received body Part value doesn't match with the sent value.");
        Assert.assertEquals(httpBodyParts.get(1).getContentType(), "plain/text", "Incorrect content type received");
        Assert.assertEquals(httpBodyParts.get(1).getPartName(), "file", "Incorrect part name.");
        listener.clearBodyParts();
    }*/

   /* @Test(description = "Test whether multipart/form-data can be decoded properly")
    public void testMultipartFormData() throws Exception {
        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
        request.headers().add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.MULTIPART_FORM_DATA);
        GenericMultipartEncoder encoder = new GenericMultipartEncoder(dataFactory, request, true);
        encoder.addBodyHttpData(MultipartTestUtil.createJSONAttribute(request, jsonContent, dataFactory));
        encoder.addBodyHttpData(MultipartTestUtil.createFileUpload(request, dataFactory));
        request = encoder.finalizeRequest();
        sendMultipartRequest(request, encoder);

        boolean isMultipart = listener.isMultipart();
        Assert.assertEquals(isMultipart, true);
        List<HttpBodyPart> httpBodyParts = listener.getMultiparts();
        Assert.assertNotNull(httpBodyParts, "Received http body parts are null");
        String jsonPart = new String(httpBodyParts.get(0).getContent());
        Assert.assertEquals(jsonPart, jsonContent, "Received body Part value doesn't match with the sent value.");
        Assert.assertEquals(httpBodyParts.get(1).getContentType(), "plain/text", "Incorrect content type received");
        Assert.assertEquals(httpBodyParts.get(1).getPartName(), "file", "Incorrect part name.");
        listener.clearBodyParts();
    }


   @Test(description = "Test whether multipart/form-data can be decoded properly")
   public void highLevelTestForMultipartFormData() throws Exception {
       HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
       request.headers().add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.MULTIPART_FORM_DATA);
       GenericMultipartEncoder encoder = new GenericMultipartEncoder(request, true);
       encoder.addBodyAttribute("foo", jsonContent);
       encoder.addBodyFileUpload("file1", MultipartTestUtil.getFileToUpload(), "text/plain", false);
       encoder.addBodyFileUpload("file2", MultipartTestUtil.getFileToUpload(), "text/plain", false);
       encoder.addBodyFileUpload("file2", MultipartTestUtil.getFileToUpload(), "text/plain", false);
       request = encoder.finalizeRequest();
       sendMultipartRequest(request, encoder);

       boolean isMultipart = listener.isMultipart();
       Assert.assertEquals(isMultipart, true);
       List<HttpBodyPart> httpBodyParts = listener.getMultiparts();
       Assert.assertNotNull(httpBodyParts, "Received http body parts are null");
       String jsonPart = new String(httpBodyParts.get(0).getContent());
       Assert.assertEquals(jsonPart, jsonContent, "Received body Part value doesn't match with the sent value.");
       Assert.assertEquals(httpBodyParts.get(1).getContentType(), "text/plain", "Incorrect content type received");
       Assert.assertEquals(httpBodyParts.get(1).getPartName(), "file1", "Incorrect part name.");
       listener.clearBodyParts();
   }*/

    @Test(description = "Test whether multipart/mixed can be decoded properly")
    public void highLevelTestForMultipartMixed() throws Exception {
        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
        request.headers().add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.MULTIPART_MIXED);
        GenericMultipartEncoder encoder = new GenericMultipartEncoder(request, true);
        encoder.addGenericBodyAttribute("foo", "bar");
        encoder.addGenericBodyFileUpload("file", MultipartTestUtil.getFileToUpload(), "text/plain", false);
        encoder.addGenericBodyFileUpload("file1", MultipartTestUtil.getFileToUpload(), "text/plain", false);
        request = encoder.finalizeRequest();
        sendMultipartRequest(request, encoder);

        boolean isMultipart = listener.isMultipart();
        Assert.assertEquals(isMultipart, true);
        List<HttpBodyPart> httpBodyParts = listener.getMultiparts();
        Assert.assertNotNull(httpBodyParts, "Received http body parts are null");
        String jsonPart = new String(httpBodyParts.get(0).getContent());
        Assert.assertEquals(jsonPart, "bar", "Received body Part value doesn't match with the sent value.");
        Assert.assertEquals(httpBodyParts.get(1).getContentType(), "text/plain", "Incorrect content type received");
        Assert.assertEquals(httpBodyParts.get(1).getPartName(), "file", "Incorrect part name.");
        listener.clearBodyParts();
    }

    private void sendMultipartRequest(HttpRequest request, GenericMultipartEncoder encoder) throws Exception {
        channel.writeInbound(request);
        if (!channel.isOpen()) {
            encoder.cleanFiles();
            return;
        }
        HttpContent content;
        while (!encoder.isEndOfInput()) {
            content = encoder.readChunk(ByteBufAllocator.DEFAULT);
            channel.writeInbound(content);
        }
        channel.flush();
        encoder.cleanFiles();
    }

    @AfterClass
    public void cleanUp() throws ServerConnectorException {
        channel.close();
    }
}
