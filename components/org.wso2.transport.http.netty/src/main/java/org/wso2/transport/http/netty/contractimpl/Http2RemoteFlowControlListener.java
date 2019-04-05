/*
 *  Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.transport.http.netty.contractimpl;

import io.netty.handler.codec.http2.Http2RemoteFlowController;
import io.netty.handler.codec.http2.Http2Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.transport.http.netty.contractimpl.sender.http2.Http2ClientChannel;
import org.wso2.transport.http.netty.contractimpl.sender.http2.OutboundMsgHolder;

/**
 * Http/2 remote flow control listener.
 */
public final class Http2RemoteFlowControlListener implements Http2RemoteFlowController.Listener {
    private static final Logger LOG = LoggerFactory.getLogger(Http2RemoteFlowControlListener.class);
    private Http2ClientChannel http2ClientChannel;

    public Http2RemoteFlowControlListener(Http2ClientChannel http2ClientChannel) {
        this.http2ClientChannel = http2ClientChannel;
    }

    @Override
    public void writabilityChanged(Http2Stream stream) {
        OutboundMsgHolder outboundMsgHolder = http2ClientChannel.getInFlightMessage(stream.id());
        if (outboundMsgHolder == null) {
            return;
        }
        if (http2ClientChannel.getConnection().remote().flowController().isWritable(stream)) {

            if (LOG.isDebugEnabled()) {
                LOG.debug("In thread {}. Stream {} is writable. State {} ", Thread.currentThread().getName(),
                          stream.id(), stream.state());
            }
            outboundMsgHolder.setStreamWritable(true);
            outboundMsgHolder.getBackPressureObservable().notifyWritable();
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("In thread {}. Stream {} is not writable. State {}. ", Thread.currentThread().getName(),
                          stream.id(), stream.state());
            }
            outboundMsgHolder.setStreamWritable(false);
        }
    }
}