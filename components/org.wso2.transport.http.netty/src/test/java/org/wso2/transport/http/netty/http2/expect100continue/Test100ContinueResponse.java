package org.wso2.transport.http.netty.http2.expect100continue;


import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.transport.http.netty.contract.Constants;
import org.wso2.transport.http.netty.contract.HttpWsConnectorFactory;
import org.wso2.transport.http.netty.contract.ServerConnector;
import org.wso2.transport.http.netty.contract.ServerConnectorFuture;
import org.wso2.transport.http.netty.contract.config.ListenerConfiguration;
import org.wso2.transport.http.netty.contract.exceptions.ServerConnectorException;
import org.wso2.transport.http.netty.contractimpl.DefaultHttpWsConnectorFactory;
import org.wso2.transport.http.netty.util.TestUtil;
import org.wso2.transport.http.netty.util.client.http2.nettyclient.Http2CompleteResponse;
import org.wso2.transport.http.netty.util.client.http2.nettyclient.Http2NettyClientWithUpgrade;
import org.wso2.transport.http.netty.util.client.http2.nettyclient.Http2ResponseHandler;
import org.wso2.transport.http.netty.util.server.listeners.Continue100Listener;

import static org.testng.AssertJUnit.assertEquals;

public class Test100ContinueResponse {

    private static final Logger LOG = LoggerFactory.getLogger(Test100ContinueResponse.class);

    private ServerConnector serverConnector;
    private HttpWsConnectorFactory httpWsConnectorFactory;
    private Http2NettyClientWithUpgrade http2NettyClient;
    private static final String PAYLOAD = "Test Http2 Message";

    @BeforeClass
    public void setup() throws Exception {
        httpWsConnectorFactory = new DefaultHttpWsConnectorFactory();
        ListenerConfiguration listenerConfiguration = new ListenerConfiguration();
        listenerConfiguration.setPort(TestUtil.HTTP_SERVER_PORT);
        listenerConfiguration.setScheme(Constants.HTTP_SCHEME);
        listenerConfiguration.setVersion(Constants.HTTP_2_0);
        serverConnector = httpWsConnectorFactory
                .createServerConnector(TestUtil.getDefaultServerBootstrapConfig(), listenerConfiguration);
        ServerConnectorFuture future = serverConnector.start();
        future.setHttpConnectorListener(new Continue100Listener());
        future.sync();
        http2NettyClient = new Http2NettyClientWithUpgrade();
        http2NettyClient.startClient(TestUtil.HTTP_SERVER_PORT, true);
    }

    @Test
    public void test100ContinuePositive() throws Exception {
        Http2ResponseHandler responseHandler = http2NettyClient.sendExpect100ContinueRequest(PAYLOAD, 3);
        Http2CompleteResponse completeResponse = responseHandler.getCompleteResponse(3);
        assertEquals(completeResponse.getContinueResponse().status().code(), HttpResponseStatus.CONTINUE.code());
        assertEquals(completeResponse.getFinalResponse().status().code(), HttpResponseStatus.OK.code());
        assertEquals(PAYLOAD, completeResponse.getFinalResponsePayload());
    }

    @AfterClass
    public void cleanUp() throws ServerConnectorException {
        serverConnector.stop();
        try {
            httpWsConnectorFactory.shutdown();
        } catch (InterruptedException e) {
            LOG.error("Interrupted while waiting for HttpWsFactory to shutdown", e);
        }
    }
}
