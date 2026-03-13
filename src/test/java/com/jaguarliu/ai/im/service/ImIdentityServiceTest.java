package com.jaguarliu.ai.im.service;

import com.jaguarliu.ai.im.crypto.ImCryptoService;
import com.jaguarliu.ai.im.entity.ImIdentityEntity;
import com.jaguarliu.ai.im.repository.ImIdentityRepository;
import org.junit.jupiter.api.*;
import org.mockito.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("ImIdentityService")
class ImIdentityServiceTest {

    @InjectMocks ImIdentityService service;
    @Mock ImIdentityRepository repo;
    @Mock ImCryptoService crypto;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("getOrInit creates identity when none exists")
    void createsOnFirstCall() throws Exception {
        when(repo.findAll()).thenReturn(java.util.List.of());
        var ed25519Pair = new ImCryptoService().generateEd25519KeyPair();
        var x25519Pair  = new ImCryptoService().generateX25519KeyPair();
        when(crypto.generateEd25519KeyPair()).thenReturn(ed25519Pair);
        when(crypto.generateX25519KeyPair()).thenReturn(x25519Pair);
        String expectedNodeId = new ImCryptoService().deriveNodeId(ed25519Pair.getPublic());
        when(crypto.deriveNodeId(any())).thenReturn(expectedNodeId);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ImIdentityEntity result = service.getOrInit();

        assertNotNull(result);
        assertEquals(expectedNodeId, result.getNodeId());
        assertEquals("Me", result.getDisplayName());
        verify(repo).save(any());
    }

    @Test
    @DisplayName("getOrInit returns existing identity when present")
    void returnsExisting() {
        ImIdentityEntity existing = ImIdentityEntity.builder()
            .nodeId("abc123")
            .displayName("Alice")
            .publicKeyEd25519("...")
            .publicKeyX25519("...")
            .privateKeyEd25519("...")
            .privateKeyX25519("...")
            .createdAt(java.time.LocalDateTime.now())
            .build();
        when(repo.findAll()).thenReturn(java.util.List.of(existing));

        ImIdentityEntity result = service.getOrInit();

        assertEquals("abc123", result.getNodeId());
        verify(repo, never()).save(any());
    }
}
