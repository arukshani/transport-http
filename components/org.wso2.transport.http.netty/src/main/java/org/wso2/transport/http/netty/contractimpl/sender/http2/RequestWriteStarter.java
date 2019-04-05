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

package org.wso2.transport.http.netty.contractimpl.sender.http2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.transport.http.netty.message.DefaultBackPressureListener;
import org.wso2.transport.http.netty.message.Http2BackPressureListener;

/**
 * Starts writing HTTP/2 request content.
 */
public class RequestWriteStarter {
    private static final Logger LOG = LoggerFactory.getLogger(RequestWriteStarter.class);

    private final OutboundMsgHolder outboundMsgHolder;
    private final Http2ClientChannel http2ClientChannel;

    public RequestWriteStarter(OutboundMsgHolder outboundMsgHolder, Http2ClientChannel http2ClientChannel) {
        this.outboundMsgHolder = outboundMsgHolder;
        this.http2ClientChannel = http2ClientChannel;
    }

    public void startWritingContent() {
        setBackPressureListener();
        outboundMsgHolder.setFirstContentWritten(false);
        outboundMsgHolder.getRequest().getHttpContentAsync().setMessageListener(httpContent -> {
            checkStreamUnwritability();
            http2ClientChannel.getChannel().eventLoop().execute(() -> {
                Http2Content http2Content = new Http2Content(httpContent, outboundMsgHolder);
                http2ClientChannel.getChannel().write(http2Content);
            });
        });
    }

    private void setBackPressureListener() {
        if (outboundMsgHolder.getRequest().isPassthrough()) {
            outboundMsgHolder.getBackPressureObservable().setListener(new Http2BackPressureListener());
        } else {
            outboundMsgHolder.getBackPressureObservable().setListener(new DefaultBackPressureListener());
        }
    }

    private void checkStreamUnwritability() {
        if (!outboundMsgHolder.isStreamWritable()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("In thread {}. Stream is not writable.", Thread.currentThread().getName());
            }
            outboundMsgHolder.getBackPressureObservable().notifyUnWritable();
        }
    }
}