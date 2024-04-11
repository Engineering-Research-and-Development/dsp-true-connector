package it.eng.connector.integration;

import static org.hamcrest.CoreMatchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.CatalogRequestMessage;
import it.eng.catalog.model.Serializer;
import it.eng.tools.model.DSpaceConstants;
import lombok.extern.java.Log;

@SpringBootTest
@AutoConfigureMockMvc
@Log
class CatalogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    private CatalogRequestMessage catalogRequestMessage = CatalogRequestMessage.Builder.newInstance().filter(List.of("some-filter")).build();
   
    @Test
    @WithUserDetails("milisav@mail.com")
    public void getCatalogSuccessfulTest() throws Exception {
    	log.info("Fetch catalog success test...");
    	JsonNode jsonNode = Serializer.serializeProtocolJsonNode(catalogRequestMessage);
    	final ResultActions result =
    			mockMvc.perform(
    					post("/catalog/request")
    					.content(jsonNode.toPrettyString())
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isOk())
    	.andExpect(content().contentType(MediaType.APPLICATION_JSON))
    	.andExpect(jsonPath("['"+DSpaceConstants.TYPE+"']", is(DSpaceConstants.DCAT + Catalog.class.getSimpleName())));
    }

//    @Test
//    public void addNewCourseTest() throws Exception {
//        //build request body
//        Course course = Course.builder()
//                .name("test-course")
//                .price(100)
//                .duration("0 month")
//                .build();
//        //call controller endpoints
//        mockMvc.perform(MockMvcRequestBuilders
//                        .post("/courses")
//                        .contentType("application/json")
//                        .content(asJsonString(course))
//                        .accept("application/json"))
//                .andExpect(status().isOk())
//                .andExpect(MockMvcResultMatchers.jsonPath("$.id").exists());
//    }


//    @Test
//    public void getAllTheCoursesTest() throws Exception {
//        mockMvc.perform(MockMvcRequestBuilders
//                        .get("/courses")
//                        .accept("application/json")
//                        .contentType("application/json"))
//                .andExpect(status().isOk())
//                .andExpect(MockMvcResultMatchers.jsonPath("$.*").exists())
//                .andExpect(MockMvcResultMatchers.jsonPath("$.[0].id").value(1));
//    }
//
//    private String asJsonString(Object object) {
//        try {
//            return new ObjectMapper().writeValueAsString(object);
//        } catch (JsonProcessingException e) {
//            e.printStackTrace();
//        }
//        return null;
//    }
    


}
