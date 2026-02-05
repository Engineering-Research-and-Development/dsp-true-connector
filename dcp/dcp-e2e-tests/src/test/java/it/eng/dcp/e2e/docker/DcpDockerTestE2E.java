package it.eng.dcp.e2e.docker;

import it.eng.dcp.e2e.common.DcpTestEnvironment;
import it.eng.dcp.e2e.common.DockerTestEnvironment;
import it.eng.dcp.e2e.tests.AbstractDcpE2ETest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Runtime-agnostic DCP tests running against Docker containers.
 *
 * <p>This test class runs all tests defined in {@link AbstractDcpE2ETest}
 * against applications started as Docker containers via Testcontainers.
 *
 * <p>Infrastructure setup is handled by {@link BaseDcpE2ETest} which
 * builds Docker images, starts containers, and provides REST clients.
 */
public class DcpDockerTestE2E extends BaseDcpE2ETest {

    private final TestDelegate testDelegate = new TestDelegate();

    @BeforeEach
    void setupTestEnvironment() {
        // Create environment wrapper using containers from BaseDcpE2ETest
        DockerTestEnvironment environment = new DockerTestEnvironment(
            issuerContainer,
            holderVerifierContainer
        );
        testDelegate.setEnvironment(environment);
    }

    /**
     * Inner class that extends AbstractDcpE2ETest to reuse all test logic.
     * This uses composition to bridge the two class hierarchies.
     */
    private static class TestDelegate extends AbstractDcpE2ETest {
        private DcpTestEnvironment environment;

        void setEnvironment(DcpTestEnvironment environment) {
            this.environment = environment;
        }

        @Override
        protected DcpTestEnvironment getEnvironment() {
            return environment;
        }
    }

    // Delegate all test methods to the inner class

    @Test
    void testIssuerDidDocumentIsAccessible() {
        testDelegate.testIssuerDidDocumentIsAccessible();
    }

    @Test
    void testHolderDidDocumentIsAccessible() {
        testDelegate.testHolderDidDocumentIsAccessible();
    }

    @Test
    void testVerifierDidDocumentIsAccessible() {
        testDelegate.testVerifierDidDocumentIsAccessible();
    }

    @Test
    void testAllDidsAreUnique() {
        testDelegate.testAllDidsAreUnique();
    }
}

