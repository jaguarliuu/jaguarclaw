package com.jaguarliu.ai.nodeconsole;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SshConnector Error Message Tests")
class SshConnectorErrorMessageTest {

    @Test
    @DisplayName("returns actionable message when private key content is invalid")
    void returnsActionableMessageForInvalidPrivateKey() {
        SshConnector connector = new SshConnector(new NodeConsoleProperties());
        NodeEntity node = new NodeEntity();
        node.setAlias("bad-key-node");
        node.setHost("203.0.113.10");
        node.setPort(22);
        node.setUsername("ubuntu");
        node.setAuthType("key");

        ConnectionTestOutcome outcome = connector.testConnectionWithDetails("not-a-private-key", node);

        assertFalse(outcome.success());
        assertEquals(ExecResult.ErrorType.VALIDATION_ERROR, outcome.errorType());
        assertNotNull(outcome.message());
        assertTrue(outcome.message().toLowerCase().contains("private key"));
    }
}

