package it.eng.connector.integration.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.tomakehurst.wiremock.WireMockServer;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.Artifact;
import it.eng.tools.model.ArtifactType;
import it.eng.tools.repository.ArtifactRepository;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.serializer.ToolsSerializer;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MvcResult;
import org.wiremock.spring.InjectWireMock;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ArtifactAPIIT extends BaseIntegrationTest {

    private static final String USER_TENANT = "engineering-a";
    private static final String OTHER_TENANT = "engineering-b";

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private DatasetRepository datasetRepository;

    @InjectWireMock
    private WireMockServer wiremock;

    @AfterEach
    public void cleanup() {
        datasetRepository.deleteAll();
        artifactRepository.deleteAll();
    }

    private Dataset datasetWithArtifact(Artifact artifact, String tenantId) {
        return Dataset.Builder.newInstance()
                .title(CatalogMockObjectUtil.TITLE)
                .hasPolicy(Collections.singleton(CatalogMockObjectUtil.OFFER))
                .artifact(artifact)
                .tenantId(tenantId)
                .build();
    }

    @Test
    @DisplayName("Artifact API - get")
    @WithUserDetails(TestUtil.API_USER)
    public void getArtifact() throws Exception {
        Artifact artifactExternal = Artifact.Builder.newInstance()
                .artifactType(ArtifactType.EXTERNAL)
                .createdBy(CatalogMockObjectUtil.CREATOR)
                .created(CatalogMockObjectUtil.NOW)
                .lastModifiedDate(CatalogMockObjectUtil.NOW)
                .lastModifiedBy(CatalogMockObjectUtil.CREATOR)
                .value("https://example.com/employees")
                .build();

        Artifact artifactFile = Artifact.Builder.newInstance()
                .artifactType(ArtifactType.FILE)
                .contentType(MediaType.APPLICATION_JSON.getType())
                .createdBy(CatalogMockObjectUtil.CREATOR)
                .created(CatalogMockObjectUtil.NOW)
                .lastModifiedDate(CatalogMockObjectUtil.NOW)
                .lastModifiedBy(CatalogMockObjectUtil.CREATOR)
                .filename("Employees.txt")
                .value(new ObjectId().toHexString())
                .build();

        Artifact artifactOtherTenant = Artifact.Builder.newInstance()
                .artifactType(ArtifactType.EXTERNAL)
                .createdBy(CatalogMockObjectUtil.CREATOR)
                .created(CatalogMockObjectUtil.NOW)
                .lastModifiedDate(CatalogMockObjectUtil.NOW)
                .lastModifiedBy(CatalogMockObjectUtil.CREATOR)
                .value("https://example.com/other-tenant")
                .build();

        artifactRepository.save(artifactFile);
        artifactRepository.save(artifactExternal);
        artifactRepository.save(artifactOtherTenant);
        datasetRepository.save(datasetWithArtifact(artifactFile, USER_TENANT));
        datasetRepository.save(datasetWithArtifact(artifactExternal, USER_TENANT));
        datasetRepository.save(datasetWithArtifact(artifactOtherTenant, OTHER_TENANT));

        TypeReference<GenericApiResponse<List<Artifact>>> typeRef = new TypeReference<GenericApiResponse<List<Artifact>>>() {
        };

        MvcResult resultList = mockMvc.perform(
                        get(ApiEndpoints.CATALOG_ARTIFACT_V1))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String jsonList = resultList.getResponse().getContentAsString();
        GenericApiResponse<List<Artifact>> apiRespList = ToolsSerializer.deserializePlain(jsonList, typeRef);

        assertNotNull(apiRespList.getData());
        // only the artifacts of the user's tenant datasets are listed
        assertEquals(2, apiRespList.getData().size());
        assertTrue(apiRespList.getData().stream().noneMatch(a -> artifactOtherTenant.getId().equals(a.getId())));

        MvcResult resultSingle = mockMvc.perform(
                        get(ApiEndpoints.CATALOG_ARTIFACT_V1 + "/" + artifactFile.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String jsonSingle = resultSingle.getResponse().getContentAsString();
        GenericApiResponse<List<Artifact>> apiRespSingle = ToolsSerializer.deserializePlain(jsonSingle, typeRef);

        assertNotNull(apiRespSingle);
        assertTrue(apiRespSingle.isSuccess());
        assertEquals(1, apiRespSingle.getData().size());
        // Object equals won't work because the db Artifact has version and the static Artifact doesn't
        assertEquals(artifactFile.getId(), apiRespSingle.getData().get(0).getId());

        // artifact of another tenant is not found
        mockMvc.perform(
                        get(ApiEndpoints.CATALOG_ARTIFACT_V1 + "/" + artifactOtherTenant.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        //fail scenario

        MvcResult resultFail = mockMvc.perform(
                        get(ApiEndpoints.CATALOG_ARTIFACT_V1 + "/" + "1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andReturn();

        TypeReference<GenericApiResponse<String>> typeRefFail = new TypeReference<GenericApiResponse<String>>() {
        };

        String jsonFail = resultFail.getResponse().getContentAsString();
        GenericApiResponse<String> apiRespFail = ToolsSerializer.deserializePlain(jsonFail, typeRefFail);

        assertNotNull(apiRespFail);
        assertNull(apiRespFail.getData());
        assertFalse(apiRespFail.isSuccess());
        assertNotNull(apiRespFail.getMessage());
    }
}
