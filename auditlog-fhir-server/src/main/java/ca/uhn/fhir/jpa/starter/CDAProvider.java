package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.IdType;

import javax.servlet.http.HttpServletResponse;

/**
 * Provider that adds a dummy operation to retrieve CDA from FHIR
 */
public class CDAProvider implements IResourceProvider {

  private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(CDAProvider.class);


  @Operation(name = "$fhirToCDA", idempotent = true, manualResponse = true)
  public void fhirToCDAOperation(@IdParam IdType theDiagnosticResourceId, HttpServletResponse theServletResponse) throws Exception {
    //do nothing. this is just for show.
    theServletResponse.setStatus(200);
    theServletResponse.setContentType("text/plain");
    theServletResponse.getWriter().write("<CDA/>");
    theServletResponse.getWriter().close();
    ourLog.info("Called the Operation $fhirToCDA for the DiagnosticReport resource with the id " + theDiagnosticResourceId.getValue());
  }

  @Override
  public Class<? extends IBaseResource> getResourceType() {
    return DiagnosticReport.class;
  }
}
