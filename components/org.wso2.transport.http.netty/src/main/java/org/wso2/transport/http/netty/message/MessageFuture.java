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
import org.wso2.transport.http.netty.listener.PipeliningHandler;

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
            this.messageListener = messageListener;
            PipeliningHandler.pipelineResponse(sourceContext, this, httpCarbonMessage);
        } else {
            synchronized (this.httpCarbonMessage) {
                this.messageListener = messageListener;
                writeHttpContent(this.httpCarbonMessage);
            }
        }
    }

    public void sendCurrentMessage() {
        synchronized (this.httpCarbonMessage) {
            writeHttpContent(this.httpCarbonMessage);
        }
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
}
