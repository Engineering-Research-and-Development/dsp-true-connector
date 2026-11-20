package it.eng.dcp.holder.rest.api;

import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TokenGeneratorAPIControllerConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean("selfIssuedIdTokenService", SelfIssuedIdTokenService.class, () -> mock(SelfIssuedIdTokenService.class))
            .withBean(DidDocumentConfig.class, () -> mock(DidDocumentConfig.class))
            .withUserConfiguration(TokenGeneratorAPIController.class);

    @Test
    @DisplayName("Controller is not registered when the property is absent")
    void propertyAbsent_controllerNotRegistered() {
        runner.run(ctx -> assertTrue(ctx.getBeansOfType(TokenGeneratorAPIController.class).isEmpty()));
    }

    @Test
    @DisplayName("Controller is not registered when the property is false")
    void propertyFalse_controllerNotRegistered() {
        runner.withPropertyValues("dcp.dev.token-generator.enabled=false")
                .run(ctx -> assertTrue(ctx.getBeansOfType(TokenGeneratorAPIController.class).isEmpty()));
    }

    @Test
    @DisplayName("Controller is registered when the property is true")
    void propertyTrue_controllerRegistered() {
        runner.withPropertyValues("dcp.dev.token-generator.enabled=true")
                .run(ctx -> assertTrue(ctx.getBeansOfType(TokenGeneratorAPIController.class).size() == 1));
    }
}
