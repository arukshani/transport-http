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

/**
 * Represents future contents of the message.
 */
public class MessageFuture {

    private static final Logger log = LoggerFactory.getLogger(MessageFuture.class);
    private MessageListener messageListener;
    private final HTTPCarbonMessage httpCarbonMessage;
    private ChannelHandlerContext sourceContext;

    public MessageFuture(HTTPCarbonMessage httpCarbonMessage) {
        this.httpCarbonMessage = httpCarbonMessage;
    }

    public void setMessageListener(MessageListener messageListener) {

        this.messageListener = messageListener;

        if (this.sourceContext != null) {
            Integer maxEventsHeld = this.sourceContext.channel().attr(Constants.MAX_EVENTS_HELD).get();
            Queue<HTTPCarbonMessage> responseQueue = this.sourceContext.channel().attr(Constants.RESPONSE_QUEUE)
                    .get();
            Integer nextSequenceNumber = this.sourceContext.channel().attr(Constants.NEXT_SEQUENCE_NUMBER).get();
            log.info("MaxEventsHeld : " + maxEventsHeld + " size-response-queue : " + responseQueue.size() +
                    " next-sequence-number: " + nextSequenceNumber + " -Channel ID:" + sourceContext.channel()
                    .id());

            if (responseQueue.size() < maxEventsHeld) {
                responseQueue.add(httpCarbonMessage);
                while (!responseQueue.isEmpty()) {
                    final HTTPCarbonMessage queuedPipelinedResponse = responseQueue.peek();
                    int currentSequenceNumber = queuedPipelinedResponse.getSequenceId();
                    if (currentSequenceNumber != nextSequenceNumber) {
                        break;
                    }
                    responseQueue.remove();
                    synchronized (queuedPipelinedResponse) {
                        while (!queuedPipelinedResponse.isEmpty()) {
                            HttpContent httpContent = queuedPipelinedResponse.getHttpContent();
                            //Notify the correct listener related to currently executing message
                            //notifyMessageListener(httpContent);
                            queuedPipelinedResponse.getMessageFuture().notifyMessageListener(httpContent);
                            if (httpContent instanceof LastHttpContent) {
                                nextSequenceNumber++;
                                this.sourceContext.channel().attr(Constants.NEXT_SEQUENCE_NUMBER)
                                        .set(nextSequenceNumber);
                                queuedPipelinedResponse.removeMessageFuture();
                            }
                        }
                    }
                }
            } else {
                this.sourceContext.close();
            }
        } else {
            synchronized (httpCarbonMessage) {
                while (!httpCarbonMessage.isEmpty()) {
                    HttpContent httpContent = httpCarbonMessage.getHttpContent();
                    notifyMessageListener(httpContent);
                    if (httpContent instanceof LastHttpContent) {
                        httpCarbonMessage.removeMessageFuture();
                        return;
                    }
                }
            }
        }

    }

    void notifyMessageListener(HttpContent httpContent) {
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

    //IMPORTANT: Only set this when you want the requests to be pipelined. Not to be used with HTTP2.
    public void setSourceContext(ChannelHandlerContext sourceContext) {
        this.sourceContext = sourceContext;
    }
}
