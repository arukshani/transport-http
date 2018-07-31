/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.transport.http.netty.message;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.transport.http.netty.common.Constants;

import java.util.Queue;

import static org.wso2.transport.http.netty.common.Constants.RESPONSE_QUEUING_NOT_NEEDED;

/**
 * Represents future contents of the message.
 */
public class MessageFuture {

    private static final Logger log = LoggerFactory.getLogger(MessageFuture.class);
    private MessageListener messageListener;
    private final HttpCarbonMessage httpCarbonMessage;
    private ChannelHandlerContext sourceContext;

    public MessageFuture(HttpCarbonMessage httpCarbonMessage) {
        this.httpCarbonMessage = httpCarbonMessage;
    }

    public void setMessageListener(MessageListener messageListener) {
        synchronized (httpCarbonMessage) {
            this.messageListener = messageListener;
            writeHttpContent(this.httpCarbonMessage);
        }
    }

    //IMPORTANT: This should only be called from HttpOutboundRespListener and do not synchronize this whole method
    // with the current carbon message because the actual message that will be sent might or might not be the
    // current message.
    public void setResponseMessageListener(MessageListener messageListener, boolean keepAlive) {
        if (keepAlive && httpCarbonMessage.getSequenceId() != RESPONSE_QUEUING_NOT_NEEDED) {
           /* this.messageListener = messageListener;
            PipeliningHandler.pipelineResponse(sourceContext, this, httpCarbonMessage);*/
            /*synchronized (this.httpCarbonMessage) {
                this.messageListener = messageListener;
                writeHttpContent(this.httpCarbonMessage);
            }*/
            this.messageListener = messageListener;
            pipelineResponse(this.httpCarbonMessage);
        } else {
            synchronized (this.httpCarbonMessage) {
                this.messageListener = messageListener;
                writeHttpContent(this.httpCarbonMessage);
            }
        }
    }

    public void sendCurrentMessage() {
        writeHttpContent(this.httpCarbonMessage);
    }

    private void writeHttpContent(HttpCarbonMessage httpCarbonMessage) {
        while (!httpCarbonMessage.isEmpty()) {
            HttpContent httpContent = httpCarbonMessage.getHttpContent();
            notifyMessageListener(httpContent);
            if (httpContent instanceof LastHttpContent) {
                httpCarbonMessage.removeMessageFuture();
                return;
            }
        }
    }

    public void notifyMessageListener(HttpContent httpContent) {
        if (this.messageListener != null) {
            this.messageListener.onMessage(httpContent);
        } else {
            log.error("The message chunk will be lost because the MessageListener is not set.");
        }
    }

    public boolean isMessageListenerSet() {
        return messageListener != null;
    }

    public synchronized HttpContent sync() {
        return this.httpCarbonMessage.getBlockingEntityCollector().getHttpContent();
    }

    //IMPORTANT: Only set this when you want the responses to be sent out in order. Not to be used with HTTP2.
    public void setSourceContext(ChannelHandlerContext sourceContext) {
        this.sourceContext = sourceContext;
    }


    /**
     * Ensure responses are served in the same order as their corresponding requests.
     *
     * @param httpCarbonMessage HTTP response that is ready to be sent out
     */
    public void pipelineResponse(HttpCarbonMessage httpCarbonMessage) {

        if (sourceContext == null) {
            sendCurrentMessage();
            return;
        }

        Queue<HttpCarbonMessage> responseQueue = sourceContext.channel().attr(Constants.RESPONSE_QUEUE).get();
        if (responseQueue == null) {
            sendCurrentMessage();
            return;
        }

        Integer maxQueuedResponses = sourceContext.channel().attr(Constants.MAX_RESPONSES_ALLOWED_TO_BE_QUEUED).get();
        if (responseQueue.size() > maxQueuedResponses) {
            //Cannot queue up indefinitely which might cause out of memory issues, so closing the connection
            sourceContext.close();
            return;
        }

        responseQueue.add(httpCarbonMessage);
        handleQueuedResponses(responseQueue);
    }

    /**
     * Check response order. If the current response does not match with the expected response, defer sending it out.
     *
     * @param responseQueue Response queue that maintains the response order
     */
    private void handleQueuedResponses(Queue<HttpCarbonMessage> responseQueue) {
        while (!responseQueue.isEmpty()) {
            Integer nextSequenceNumber = sourceContext.channel().attr(Constants.NEXT_SEQUENCE_NUMBER).get();
            final HttpCarbonMessage queuedPipelinedResponse = responseQueue.peek();
            int currentSequenceNumber = queuedPipelinedResponse.getSequenceId();
            if (currentSequenceNumber != RESPONSE_QUEUING_NOT_NEEDED) {
                if (currentSequenceNumber != nextSequenceNumber) {
                    break;
                }
                responseQueue.remove();
                while (!queuedPipelinedResponse.isEmpty()) {
                    sendQueuedResponse(nextSequenceNumber, queuedPipelinedResponse);
                }

            } else { //No queuing needed since this has not come from source handler
                responseQueue.remove();
                sendCurrentMessage();
            }
        }
    }

    /**
     * Send the queued response to its destination.
     *
     * @param nextSequenceNumber      Identify the next expected response
     * @param queuedPipelinedResponse Queued response that needs to be sent out
     */
    private void sendQueuedResponse(Integer nextSequenceNumber,
                                    HttpCarbonMessage queuedPipelinedResponse) {
        //IMPORTANT: MessageFuture will return null, if the intrinsic lock of the message is already acquired.
        MessageFuture messageFuture = queuedPipelinedResponse.getMessageFuture();
        if (messageFuture != null && messageFuture.isMessageListenerSet()) {
            HttpContent httpContent = queuedPipelinedResponse.getHttpContent();
            //Notify the correct listener related to currently executing message
            messageFuture.notifyMessageListener(httpContent);
            if (httpContent instanceof LastHttpContent) {
                nextSequenceNumber++;
                sourceContext.channel().attr(Constants.NEXT_SEQUENCE_NUMBER).set(nextSequenceNumber);
                queuedPipelinedResponse.removeMessageFuture();
            }
        }
    }
}
