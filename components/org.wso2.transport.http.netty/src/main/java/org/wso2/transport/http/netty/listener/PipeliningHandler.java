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
import org.wso2.transport.http.netty.contractimpl.HttpOutboundRespListener;
import org.wso2.transport.http.netty.message.HttpCarbonMessage;
import org.wso2.transport.http.netty.message.MessageFuture;

import java.util.Queue;

import static org.wso2.transport.http.netty.common.Constants.RESPONSE_QUEUING_NOT_NEEDED;

/**
 * Implement HTTP 1.1 pipelining, adhering to RFC2616.
 */
public class PipeliningHandler {

    private static final boolean KEEP_ALIVE_TRUE = true;

    /**
     * Ensure responses are served in the same order as their corresponding requests.
     *
     * @param sourceContext     Represents netty channel handler context which belongs to source handler
     * @param respListener      Represents the outbound response listener
     * @param httpCarbonMessage HTTP response that is ready to be sent out
     */
    public static void pipelineResponse(ChannelHandlerContext sourceContext, HttpOutboundRespListener respListener,
                                        HttpCarbonMessage httpCarbonMessage) {

        Queue<HttpCarbonMessage> responseQueue = sourceContext.channel().attr(Constants.RESPONSE_QUEUE).get();

       /* if (sourceContext == null) {
            respListener.sendResponse(httpCarbonMessage, KEEP_ALIVE_TRUE);
            return;
        }

        if (responseQueue == null) {
            respListener.sendResponse(httpCarbonMessage, KEEP_ALIVE_TRUE);
            return;
        }*/

        Integer maxQueuedResponses = sourceContext.channel().attr(Constants.MAX_RESPONSES_ALLOWED_TO_BE_QUEUED).get();
        if (responseQueue.size() > maxQueuedResponses) {
            //Cannot queue up indefinitely which might cause out of memory issues, so closing the connection
            sourceContext.close();
            return;
        }
        if (!responseQueue.contains(httpCarbonMessage)) {
            responseQueue.add(httpCarbonMessage);
        }
        //handleQueuedResponses(sourceContext, respListener, responseQueue);
    }

    /**
     * Check response order. If the current response does not match with the expected response, defer sending it out.
     *
     * @param sourceContext Represents netty channel handler context which belongs to source handler
     * @param respListener  Represents the outbound response listener
     */
    public static void handleQueuedResponses(ChannelHandlerContext sourceContext,
                                             HttpOutboundRespListener respListener,
                                             HttpCarbonMessage httpCarbonMessage) {
        Queue<HttpCarbonMessage> responseQueue = sourceContext.channel().attr(Constants.RESPONSE_QUEUE).get();
        if (!responseQueue.contains(httpCarbonMessage)) {
            responseQueue.add(httpCarbonMessage);
        }
        while (!responseQueue.isEmpty()) {
            Integer nextSequenceNumber = sourceContext.channel().attr(Constants.NEXT_SEQUENCE_NUMBER).get();
            final HttpCarbonMessage queuedPipelinedResponse = responseQueue.peek();
            int currentSequenceNumber = queuedPipelinedResponse.getSequenceId();
            if (currentSequenceNumber != RESPONSE_QUEUING_NOT_NEEDED) {
                if (currentSequenceNumber != nextSequenceNumber) {
                    break;
                }

                //Remove the piped response from the queue even if not all the content has been received. When there are
                //delayed contents pipeline listener will trigger this method again with delayed content as the next
                // runnable
                // task queued added to the same IO thread. We do not have to worry about other responses getting
                // executed before the
                //delayed content because the nextSequence number will get updated only when the last http content of
                // the delayed message has been received.
                responseQueue.remove();
                while (!queuedPipelinedResponse.isEmpty()) {
                    sendQueuedResponse(sourceContext, nextSequenceNumber, queuedPipelinedResponse, responseQueue,
                            respListener);
                }

            } else { //No queuing needed since this has not come from source handler
                responseQueue.remove();
                respListener.sendResponse(queuedPipelinedResponse, KEEP_ALIVE_TRUE);
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
    private static void sendQueuedResponse(ChannelHandlerContext sourceContext, Integer nextSequenceNumber,
                                           HttpCarbonMessage queuedPipelinedResponse, Queue<HttpCarbonMessage>
                                                   responseQueue, HttpOutboundRespListener respListener) {

        //MessageFuture messageFuture = queuedPipelinedResponse.getMessageFuture();
        /*if (messageFuture != null && messageFuture.isMessageListenerSet()) {
            HttpContent httpContent = queuedPipelinedResponse.getHttpContent();
            //Notify the correct listener related to currently executing message
            messageFuture.notifyMessageListener(httpContent);
            if (httpContent instanceof LastHttpContent) {
                nextSequenceNumber++;
                sourceContext.channel().attr(Constants.NEXT_SEQUENCE_NUMBER).set(nextSequenceNumber);
                queuedPipelinedResponse.removeMessageFuture();
            }
        }*/

        MessageFuture writeFuture = queuedPipelinedResponse.getWriteFuture();

        if (writeFuture != null && writeFuture.isContentWriteListenerSet()) {
            HttpContent httpContent = queuedPipelinedResponse.getHttpContent();
            //Notify the correct listener related to currently executing message
            writeFuture.notifyContentWriteListener(httpContent);
            if (httpContent instanceof LastHttpContent) {
                /*Integer nextSequenceNumber = sourceContext.channel().attr(Constants.NEXT_SEQUENCE_NUMBER).get();
                nextSequenceNumber++;
                sourceContext.channel().attr(Constants.NEXT_SEQUENCE_NUMBER).set(nextSequenceNumber);
                Queue<HttpCarbonMessage> responseQueue = sourceContext.channel().attr(Constants.RESPONSE_QUEUE).get();
                responseQueue.remove();*/
                if (!respListener.isContinueRequest()) {
                    nextSequenceNumber++;
                }
                sourceContext.channel().attr(Constants.NEXT_SEQUENCE_NUMBER).set(nextSequenceNumber);
                queuedPipelinedResponse.removeMessageFuture();
            }
        }
    }
}
