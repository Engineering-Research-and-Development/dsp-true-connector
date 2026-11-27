package it.eng.dcp.common.config;

import it.eng.dcp.common.participant.FixedParticipantContextResolver;
import it.eng.dcp.common.participant.ParticipantAdminAuthorization;
import it.eng.dcp.common.participant.ParticipantContext;
import it.eng.dcp.common.participant.ParticipantContextProvider;
import it.eng.dcp.common.participant.ParticipantContextResolver;
import it.eng.dcp.common.participant.ParticipantKeyProvider;
import it.eng.dcp.common.participant.PathParticipantContextResolver;
import it.eng.dcp.common.participant.SingleParticipantContextProvider;
import it.eng.dcp.common.service.KeyMetadataService;
import it.eng.dcp.common.service.KeyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class ParticipantContextConfigurationTest {

    @Configuration
    @EnableConfigurationProperties(DcpProperties.class)
    static class Infrastructure {
        @Bean
        DidDocumentConfig didDocumentConfig() {
            return DidDocumentConfig.builder().did("did:web:example.com").baseUrl("https://example.com")
                    .serviceEntries(List.of()).build();
        }

        @Bean
        KeyService keyService() {
            return mock(KeyService.class);
        }

        @Bean
        KeyMetadataService keyMetadataService() {
            return mock(KeyMetadataService.class);
        }
    }

    @Configuration
    static class HostBeans {
        static final ParticipantContextProvider PROVIDER = id -> Optional.empty();

        @Bean
        ParticipantContextProvider hostProvider() {
            return PROVIDER;
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Infrastructure.class)
            .withPropertyValues("dcp.connector-did=did:web:example.com");

    @Test
    @DisplayName("with today's properties there is one participant 'default' and the fixed resolver")
    void defaults() {
        runner.withUserConfiguration(ParticipantContextConfiguration.class).run(context -> {
            assertInstanceOf(SingleParticipantContextProvider.class, context.getBean(ParticipantContextProvider.class));
            assertInstanceOf(FixedParticipantContextResolver.class, context.getBean(ParticipantContextResolver.class));
            assertInstanceOf(ParticipantKeyProvider.class, context.getBean(ParticipantKeyProvider.class));
            assertInstanceOf(ParticipantAdminAuthorization.class, context.getBean(ParticipantAdminAuthorization.class));
            assertEquals("default", context.getBean(ParticipantContextProvider.class)
                    .find("default").map(ParticipantContext::id).orElseThrow());
        });
    }

    @Test
    @DisplayName("dcp.participant.resolver=path selects the path resolver")
    void pathResolver() {
        runner.withUserConfiguration(ParticipantContextConfiguration.class)
                .withPropertyValues("dcp.participant.resolver=path")
                .run(context -> assertInstanceOf(PathParticipantContextResolver.class,
                        context.getBean(ParticipantContextResolver.class)));
    }

    @Test
    @DisplayName("a host bean replaces the default")
    void hostBeanWins() {
        runner.withUserConfiguration(HostBeans.class, ParticipantContextConfiguration.class).run(context -> {
            assertEquals(1, context.getBeansOfType(ParticipantContextProvider.class).size());
            assertSame(HostBeans.PROVIDER, context.getBean(ParticipantContextProvider.class));
        });
    }
}
