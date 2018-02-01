/*
*  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*/

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
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.messaging.exceptions.ServerConnectorException;
import org.wso2.transport.http.netty.config.ChunkConfig;
import org.wso2.transport.http.netty.contractimpl.HttpWsServerConnectorFuture;
import org.wso2.transport.http.netty.listener.SourceHandler;
import org.wso2.transport.http.netty.message.HttpBodyPart;
import org.wso2.transport.http.netty.multipartdecoder.MultipartContentListener;
import org.wso2.transport.http.netty.multipartdecoder.MultipartTestUtil;

import java.util.List;

/**
 * Test cases for multipart handling.
 */
public class MultipartTestCase {
    private static final String jsonContent = "{key:value, key2:value2}";
    private final HttpDataFactory dataFactory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);
    private EmbeddedChannel channel;
    private HttpWsServerConnectorFuture httpWsServerConnectorFuture = new HttpWsServerConnectorFuture();
    private MultipartContentListener listener;

    @BeforeClass
    public void setup() throws Exception {
        channel = new EmbeddedChannel();
        channel.pipeline().addLast(new HttpResponseDecoder());
        channel.pipeline().addLast(new HttpRequestEncoder());
        channel.pipeline().addLast(new SourceHandler(httpWsServerConnectorFuture, null, ChunkConfig.ALWAYS));
        listener = new MultipartContentListener();
        httpWsServerConnectorFuture.setHttpConnectorListener(listener);
    }

    @Test(description = "Test whether the received multipart request can be decoded properly")
    public void testMultipartRequest() throws Exception {
        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
        HttpPostRequestEncoder encoder = new HttpPostRequestEncoder(dataFactory, request, true);
        request.headers().add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.MULTIPART_FORM_DATA);
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

    @Test(description = "Test sending a bogus request posing as a multipart without an actual multipart body")
    public void testMultipartRequestWithoutBody() throws Exception {
        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
        HttpPostRequestEncoder encoder = new HttpPostRequestEncoder(dataFactory, request, true);
        request = encoder.finalizeRequest();
        request.headers().add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.MULTIPART_FORM_DATA);
        sendMultipartRequest(request, encoder);

        boolean isMultipart = listener.isMultipart();
        Assert.assertEquals(isMultipart, false);
        List<HttpBodyPart> httpBodyParts = listener.getMultiparts();
        Assert.assertNull(httpBodyParts, "Received http body parts are null");
        listener.clearBodyParts();
    }

    @Test(description = "Test whether the received multipart request can be decoded properly")
    public void testMixed() throws Exception {
        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
        HttpPostRequestEncoder encoder = new HttpPostRequestEncoder(dataFactory, request, true);
        request.headers().add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.MULTIPART_MIXED);
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

    /**
     * Write multipart request to inbound channel.
     *
     * @param request Represent a HttpRequest
     * @param encoder Represent netty HttpPostRequestEncoder
     * @throws Exception
     */
    private void sendMultipartRequest(HttpRequest request, HttpPostRequestEncoder encoder) throws Exception {
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
