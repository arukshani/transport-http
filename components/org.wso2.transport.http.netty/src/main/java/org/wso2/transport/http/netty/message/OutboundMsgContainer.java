package org.wso2.transport.http.netty.message;

import io.netty.handler.codec.http.HttpContent;
import org.wso2.transport.http.netty.contractimpl.HttpOutboundRespListener;
import org.wso2.transport.http.netty.contractimpl.common.states.MessageStateContext;

/**
 * Created by rukshani on 10/12/18.
 */
public class OutboundMsgContainer {

    private MessageStateContext messageStateContext;
    private HttpOutboundRespListener outboundRespListener;
    private HttpCarbonMessage outboundMsg;
    private HttpContent httpContent;

    public OutboundMsgContainer(MessageStateContext messageStateContext, HttpOutboundRespListener outboundRespListener,
                                HttpCarbonMessage inboundRequestMsg, HttpContent httpContent) {
        this.messageStateContext = messageStateContext;
        this.outboundRespListener = outboundRespListener;
        this.outboundMsg = inboundRequestMsg;
        this.httpContent = httpContent;
    }

    public MessageStateContext getMessageStateContext() {
        return messageStateContext;
    }

    public HttpOutboundRespListener getOutboundRespListener() {
        return outboundRespListener;
    }

    public HttpCarbonMessage getOutboundMsg() {
        return outboundMsg;
    }

    public HttpContent getHttpContent() {
        return httpContent;
    }
}
