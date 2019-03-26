package org.wso2.transport.http.netty.contractimpl.sender.http2;

import io.netty.handler.codec.http.HttpContent;
import org.wso2.transport.http.netty.contract.HttpResponseFuture;
import org.wso2.transport.http.netty.contractimpl.common.states.Http2MessageStateContext;

public class Http2Content {

    private HttpContent httpContent;
    private Http2MessageStateContext http2MessageStateContext;
    private HttpResponseFuture httpResponseFuture;

    public Http2Content(HttpContent httpContent, Http2MessageStateContext http2MessageStateContext,
                        HttpResponseFuture httpResponseFuture) {
        this.httpContent = httpContent;
        this.http2MessageStateContext = http2MessageStateContext;
        this.httpResponseFuture = httpResponseFuture;
    }

    public HttpContent getHttpContent() {
        return httpContent;
    }

    public Http2MessageStateContext getHttp2MessageStateContext() {
        return http2MessageStateContext;
    }

    public HttpResponseFuture getHttpResponseFuture() {
        return httpResponseFuture;
    }
}
