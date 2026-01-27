package it.eng.dcp.holder.service;

import it.eng.dcp.common.model.PresentationResponseMessage;
import it.eng.dcp.holder.model.ValidationReport;

import java.util.List;

public interface PresentationValidationService {

    /**
     * Validate a presentation response message against required credential types and a token context.
     * Returns a ValidationReport containing any errors and an accepted set of credential types.
     * @param rsp The presentation response message to validate.
     * @param requiredCredentialTypes List of credential types that must be present in the presentation.
     * @param tokenCtx Context extracted from validated tokens to use during validation.
     * @return ValidationReport with validation results.
     */
    ValidationReport validate(PresentationResponseMessage rsp, List<String> requiredCredentialTypes, TokenContext tokenCtx);
}
