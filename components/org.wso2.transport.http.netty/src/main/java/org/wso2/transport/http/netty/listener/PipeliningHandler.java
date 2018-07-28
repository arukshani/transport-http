package org.wso2.transport.http.netty.listener;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.transport.http.netty.common.Constants;
import org.wso2.transport.http.netty.message.HttpCarbonMessage;
import org.wso2.transport.http.netty.message.MessageFuture;

import java.util.Queue;

/**
 * Handle responses to pipeline requests.
 */
public class PipeliningHandler {
    private static final Logger log = LoggerFactory.getLogger(PipeliningHandler.class);

    private ChannelHandlerContext sourceContext;
    private MessageFuture messageFuture;
    private HttpCarbonMessage httpCarbonMessage;

    public PipeliningHandler(ChannelHandlerContext sourceContext, MessageFuture messageFuture,
                             HttpCarbonMessage httpCarbonMessage) {
        this.sourceContext = sourceContext;
        this.messageFuture = messageFuture;
        this.httpCarbonMessage = httpCarbonMessage;
    }

    public void pipelineResponse() {
        if (this.sourceContext != null) {
            Integer maxEventsHeld = this.sourceContext.channel().attr(Constants.MAX_EVENTS_HELD).get();
            Queue<HttpCarbonMessage> responseQueue = this.sourceContext.channel().attr(Constants.RESPONSE_QUEUE)
                    .get();
            if (responseQueue != null) {
                Integer nextSequenceNumber = this.sourceContext.channel().attr(Constants.NEXT_SEQUENCE_NUMBER).get();
                /*log.info("MaxEventsHeld : " + maxEventsHeld + " size-response-queue : " + responseQueue.size() +
                        " next-sequence-number: " + nextSequenceNumber + " -Channel ID:" + sourceContext.channel()
                        .id());*/

                if (responseQueue.size() < maxEventsHeld) {
                    responseQueue.add(httpCarbonMessage);
                    while (!responseQueue.isEmpty()) {
                        final HttpCarbonMessage queuedPipelinedResponse = responseQueue.peek();
                        int currentSequenceNumber = queuedPipelinedResponse.getSequenceId();
                        if (currentSequenceNumber != 0) {
                            if (currentSequenceNumber != nextSequenceNumber) {
                                break;
                            }
                            responseQueue.remove();
                            while (!queuedPipelinedResponse.isEmpty()) {
                                //Notify the correct listener related to currently executing message
                                //notifyMessageListener(httpContent);
                                if (queuedPipelinedResponse.getMessageFuture() != null &&
                                        queuedPipelinedResponse.getMessageFuture().isMessageListenerSet()) {
                                    HttpContent httpContent = queuedPipelinedResponse.getHttpContent();
                                    queuedPipelinedResponse.getMessageFuture().notifyMessageListener(httpContent);
                                    if (httpContent instanceof LastHttpContent) {
                                        nextSequenceNumber++;
                                        this.sourceContext.channel().attr(Constants.NEXT_SEQUENCE_NUMBER)
                                                .set(nextSequenceNumber);
                                        queuedPipelinedResponse.removeMessageFuture();
                                    }
                                }
                            }
                        } else { //No queuing needed since this has not come from source handler
                            responseQueue.remove();
                            this.messageFuture.sendMessageContent(httpCarbonMessage);
                        }
                    }
                } else {
                    //Cannot queue up indefinitely which might cause out of memory issues, so closing the connection
                    this.sourceContext.close();
                }
            } else {
                this.messageFuture.sendMessageContent(httpCarbonMessage);
            }
        } else {
            this.messageFuture.sendMessageContent(httpCarbonMessage);
        }
    }
}
