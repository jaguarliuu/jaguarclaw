package com.jaguarliu.ai.session;

import com.jaguarliu.ai.storage.entity.SessionFileEntity;
import com.jaguarliu.ai.storage.repository.SessionFileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionFileService tests")
class SessionFileServiceTest {

    @Mock
    private SessionFileRepository sessionFileRepository;

    @InjectMocks
    private SessionFileService sessionFileService;

    @Test
    @DisplayName("record should persist mime type")
    void recordShouldPersistMimeType() {
        when(sessionFileRepository.save(any(SessionFileEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SessionFileEntity entity = sessionFileService.record(
                "session-1",
                null,
                "uploads/demo.png",
                "demo.png",
                123L,
                "image/png"
        );

        assertNotNull(entity.getId());
        assertEquals("image/png", entity.getMimeType());

        ArgumentCaptor<SessionFileEntity> captor = ArgumentCaptor.forClass(SessionFileEntity.class);
        verify(sessionFileRepository).save(captor.capture());
        assertEquals("image/png", captor.getValue().getMimeType());
    }
}
