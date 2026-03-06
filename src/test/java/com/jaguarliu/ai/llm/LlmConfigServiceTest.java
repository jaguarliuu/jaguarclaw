package com.jaguarliu.ai.llm;

import com.jaguarliu.ai.llm.model.LlmProviderConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("LlmConfigService tests")
class LlmConfigServiceTest {

    @Test
    @DisplayName("getMultiConfig should include vision-only models in returned model list")
    void getMultiConfigShouldIncludeVisionOnlyModels() throws Exception {
        LlmProperties properties = new LlmProperties();
        properties.setProviders(new ArrayList<>(List.of(
                LlmProviderConfig.builder()
                        .id("qwen")
                        .name("Qwen")
                        .endpoint("http://example.com/v1")
                        .apiKey("test")
                        .models(List.of("qwen-plus"))
                        .visionModels(List.of("qwen-image-2.0-pro"))
                        .build()
        )));
        properties.setDefaultModel("qwen:qwen-plus");

        LlmConfigService service = new LlmConfigService(properties, mock(OpenAiCompatibleLlmClient.class), new LlmCapabilityService(properties));
        setConfigDir(properties, service);

        @SuppressWarnings("unchecked")
        Map<String, Object> config = service.getMultiConfig();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> providers = (List<Map<String, Object>>) config.get("providers");
        @SuppressWarnings("unchecked")
        List<String> models = (List<String>) providers.get(0).get("models");
        @SuppressWarnings("unchecked")
        List<String> visionModels = (List<String>) providers.get(0).get("visionModels");

        assertEquals(List.of("qwen-plus", "qwen-image-2.0-pro"), models);
        assertTrue(visionModels.contains("qwen-image-2.0-pro"));
    }

    @Test
    @DisplayName("updateProvider should persist explicit vision models")
    void updateProviderShouldPersistExplicitVisionModels() throws Exception {
        LlmProperties properties = new LlmProperties();
        properties.setProviders(new ArrayList<>(List.of(
                LlmProviderConfig.builder()
                        .id("qwen")
                        .name("Qwen")
                        .endpoint("http://example.com/v1")
                        .apiKey("test")
                        .models(new ArrayList<>(List.of("qwen-plus")))
                        .visionModels(new ArrayList<>())
                        .build()
        )));
        properties.setDefaultModel("qwen:qwen-plus");

        LlmConfigService service = new LlmConfigService(properties, mock(OpenAiCompatibleLlmClient.class), new LlmCapabilityService(properties));
        setConfigDir(properties, service);

        service.updateProvider("qwen", null, null, null, List.of("qwen-plus"), List.of("qwen-image-2.0-pro"));

        LlmProviderConfig provider = properties.getProvider("qwen");
        assertTrue(provider.getModels().contains("qwen-image-2.0-pro"));
        assertEquals(List.of("qwen-image-2.0-pro"), provider.getVisionModels());
    }

    private void setConfigDir(LlmProperties properties, LlmConfigService service) throws Exception {
        String dir = Files.createTempDirectory("jc-llm-config-test").toString();

        Field serviceField = LlmConfigService.class.getDeclaredField("configDir");
        serviceField.setAccessible(true);
        serviceField.set(service, dir);

        Field propertiesField = LlmProperties.class.getDeclaredField("configDir");
        propertiesField.setAccessible(true);
        propertiesField.set(properties, dir);
    }
}
