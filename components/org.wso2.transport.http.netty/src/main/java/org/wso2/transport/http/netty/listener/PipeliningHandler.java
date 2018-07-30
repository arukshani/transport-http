/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.wso2.transport.http.netty.listener;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import org.wso2.transport.http.netty.common.Constants;
import org.wso2.transport.http.netty.message.HttpCarbonMessage;
import org.wso2.transport.http.netty.message.MessageFuture;

import java.util.Queue;

import static org.wso2.transport.http.netty.common.Constants.RESPONSE_QUEUING_NOT_NEEDED;

/**
 * Implement HTTP 1.1 pipelining, adhering to RFC2616.
 */
public class PipeliningHandler {

    /**
     * Ensure responses are served in the same order as their corresponding requests.
     *
     * @param sourceContext     Represents netty channel handler context which belongs to source handler
     * @param messageFuture     Represents future contents of the message
     * @param httpCarbonMessage HTTP response that is ready to be sent out
     */
    public void pipelineResponse(ChannelHandlerContext sourceContext, MessageFuture messageFuture,
                                 HttpCarbonMessage httpCarbonMessage) {

        if (sourceContext == null) {
            messageFuture.sendMessageContent(httpCarbonMessage);
            return;
        }

        Queue<HttpCarbonMessage> responseQueue = sourceContext.channel().attr(Constants.RESPONSE_QUEUE).get();
        if (responseQueue == null) {
            messageFuture.sendMessageContent(httpCarbonMessage);
            return;
        }

        Integer maxQueuedResponses = sourceContext.channel().attr(Constants.MAX_RESPONSES_ALLOWED_TO_BE_QUEUED).get();
        if (responseQueue.size() > maxQueuedResponses) {
            //Cannot queue up indefinitely which might cause out of memory issues, so closing the connection
            sourceContext.close();
            return;
        }

        responseQueue.add(httpCarbonMessage);
        handleQueuedResponses(sourceContext, messageFuture, httpCarbonMessage, responseQueue);
    }

    /**
     * Check response order. If the current response does not match with the expected response, defer sending it out.
     *
     * @param sourceContext     Represents netty channel handler context which belongs to source handler
     * @param messageFuture     Represents future contents of the message
     * @param httpCarbonMessage HTTP response that is ready to be sent out
     * @param responseQueue     Response queue that maintains the response order
     */
    private void handleQueuedResponses(ChannelHandlerContext sourceContext, MessageFuture messageFuture,
                                       HttpCarbonMessage httpCarbonMessage, Queue<HttpCarbonMessage> responseQueue) {
        Integer nextSequenceNumber = sourceContext.channel().attr(Constants.NEXT_SEQUENCE_NUMBER).get();
        while (!responseQueue.isEmpty()) {
            final HttpCarbonMessage queuedPipelinedResponse = responseQueue.peek();
            int currentSequenceNumber = queuedPipelinedResponse.getSequenceId();
            if (currentSequenceNumber != RESPONSE_QUEUING_NOT_NEEDED) {
                if (currentSequenceNumber != nextSequenceNumber) {
                    break;
                }
                responseQueue.remove();
                while (!queuedPipelinedResponse.isEmpty()) {
                    sendQueuedResponse(sourceContext, nextSequenceNumber, queuedPipelinedResponse);
                }
            } else { //No queuing needed since this has not come from source handler
                responseQueue.remove();
                messageFuture.sendMessageContent(httpCarbonMessage);
            }
        }
    }

    /**
     * Send the queued response to its destination.
     *
     * @param sourceContext           Represents netty channel handler context which belongs to source handler
     * @param nextSequenceNumber      Identify the next expected response
     * @param queuedPipelinedResponse Queued response that needs to be sent out
     */
    private void sendQueuedResponse(ChannelHandlerContext sourceContext, Integer nextSequenceNumber,
                                    HttpCarbonMessage queuedPipelinedResponse) {
        if (queuedPipelinedResponse.getMessageFuture() != null &&
                queuedPipelinedResponse.getMessageFuture().isMessageListenerSet()) {
            HttpContent httpContent = queuedPipelinedResponse.getHttpContent();
            //Notify the correct listener related to currently executing message
            queuedPipelinedResponse.getMessageFuture().notifyMessageListener(httpContent);
            if (httpContent instanceof LastHttpContent) {
                nextSequenceNumber++;
                sourceContext.channel().attr(Constants.NEXT_SEQUENCE_NUMBER).set(nextSequenceNumber);
                queuedPipelinedResponse.removeMessageFuture();
            }
        }
    }
}
