package com.jaguarliu.ai.nodeconsole;

import com.jcraft.jsch.JSchException;
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

    @Test
    @DisplayName("algorithm negotiation failure maps to VALIDATION_ERROR with helpful message")
    void algoNegoFailureMapsToValidationError() {
        SshConnector connector = new SshConnector(new NodeConsoleProperties());

        JSchException e = new JSchException(
                "Algorithm negotiation fail: kex: client [curve25519-sha256] server [diffie-hellman-group1-sha1]");

        ExecResult.ErrorType errorType = connector.mapJSchException(e);

        assertEquals(ExecResult.ErrorType.VALIDATION_ERROR, errorType,
                "Algorithm negotiation failure should be VALIDATION_ERROR, not UNKNOWN");
    }

    @Test
    @DisplayName("algorithm negotiation failure produces actionable user message")
    void algoNegoFailureProducesActionableMessage() {
        SshConnector connector = new SshConnector(new NodeConsoleProperties());

        NodeEntity node = new NodeEntity();
        node.setAlias("legacy-node");
        node.setHost("203.0.113.10");
        node.setPort(22);
        node.setUsername("ubuntu");
        node.setAuthType("password");

        String message = connector.mapJSchMessage(
                ExecResult.ErrorType.VALIDATION_ERROR,
                "Algorithm negotiation fail: kex: client [curve25519] server [diffie-hellman-group1-sha1]",
                "password");

        assertTrue(message.toLowerCase().contains("algorithm"),
                "User message should mention 'algorithm' for negotiation failures, got: " + message);
    }
}

