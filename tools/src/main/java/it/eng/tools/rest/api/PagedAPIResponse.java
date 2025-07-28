package it.eng.tools.rest.api;

import it.eng.tools.response.GenericApiResponse;
import lombok.NoArgsConstructor;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;

@NoArgsConstructor
public class PagedAPIResponse {

    private GenericApiResponse<PagedModel<EntityModel<Object>>> response;

    private PagedAPIResponse(PagedModel<EntityModel<Object>> data, String message) {
        this.response = GenericApiResponse.success(data, message);
    }

    public static PagedAPIResponse of(PagedModel<EntityModel<Object>> data, String message) {
        return new PagedAPIResponse(data, message);
    }

    public GenericApiResponse<PagedModel<EntityModel<Object>>> getResponse() {
        return response;
    }
}
