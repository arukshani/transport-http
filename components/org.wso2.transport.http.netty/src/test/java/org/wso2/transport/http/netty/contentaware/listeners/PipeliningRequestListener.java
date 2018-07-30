package org.wso2.transport.http.netty.contentaware.listeners;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.transport.http.netty.contract.HttpConnectorListener;
import org.wso2.transport.http.netty.contract.ServerConnectorException;
import org.wso2.transport.http.netty.message.HttpCarbonMessage;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Request listener that send responses to pipelined requests.
 */
public class PipeliningRequestListener implements HttpConnectorListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(EchoMessageListener.class);

    private ExecutorService executorService = Executors.newFixedThreadPool(5);
    private Map<String, CountDownLatch> waitingResponses = new ConcurrentHashMap<>();

    @Override
    public void onMessage(HttpCarbonMessage httpRequest) {

       /* executorService.execute(() -> {
            try {
                latch.await(2, TimeUnit.SECONDS);
                httpRequest.respond(httpCarbonMessage);
            } catch (ServerConnectorException | InterruptedException e) {
                LOGGER.error("Error occurred during message notification: " + e.getMessage());
            }
        });*/

        executorService.submit(() -> {
            try {
                final String messageId = "/messageid" + httpRequest.getSequenceId();
                ByteBuf content = Unpooled.copiedBuffer(messageId, StandardCharsets.UTF_8);

                final DefaultFullHttpResponse nettyResponse = new DefaultFullHttpResponse(HTTP_1_1, OK, content);
                nettyResponse.headers().add(CONTENT_LENGTH, content.readableBytes());
                HttpCarbonMessage httpCarbonMessage = new HttpCarbonMessage(nettyResponse);
                httpCarbonMessage.setSequenceId(httpRequest.getSequenceId());

                final CountDownLatch latch = new CountDownLatch(1);
                waitingResponses.put(messageId, latch);
                latch.await();
                httpRequest.respond(httpCarbonMessage);
            } catch (InterruptedException e) {
                LOGGER.error("dd");
            } catch (ServerConnectorException e) {
                LOGGER.error("ff");
            }
        });

        if (waitingResponses.size() == 5) {
           /* try {
                shutdownExecutorService();
            } catch (InterruptedException e) {

            }*/
            // Finish request processing in random manner
            List<String> urls = new ArrayList<>(getWaitingResponses().keySet());
            Collections.shuffle(urls);
            for (String url : urls) {
                finishResponse(url);
            }
        }
    }

    @Override
    public void onError(Throwable throwable) {

    }

    public void finishResponse(String messageId) {
        waitingResponses.get(messageId).countDown();
    }

    public Map<String, CountDownLatch> getWaitingResponses() {
        return waitingResponses;
    }

    private void shutdownExecutorService() throws InterruptedException {
        if (!executorService.isShutdown()) {
            executorService.shutdown();
            executorService.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
