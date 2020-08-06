package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.rp.r4.AuditEventResourceProvider;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import com.github.dnault.xmlpatch.repackaged.org.apache.commons.io.IOUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.AuditEvent;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Provider that adds operations for retrieving XES
 *
 * @author Oliver Krauss on 06.08.2020
 */
public class XESProvider implements IResourceProvider {

  /** A registry of data access objects used to talk to the back end data store */
  @Autowired
  private DaoRegistry dao;

  /**
   * Returns a bundle with the given request no clue yet how that works without a XES resource
   * @throws IOException
   */
  @Operation(name="$bundleXes", idempotent=true)
  public Bundle patientTypeOperation() {

    Bundle retVal = new Bundle();
    // Populate bundle with matching resources
    return retVal;
  }

  /**
   * Returns XES with the given request
   * @param theServletRequest request from client
   * @param theServletResponse XES data as response
   * @throws IOException
   */
  @Operation(name="$xes", manualResponse=true, manualRequest=true, idempotent = true)
  public void xesTypeOperation(HttpServletRequest theServletRequest, HttpServletResponse theServletResponse) throws IOException {
    String contentType = theServletRequest.getContentType();
    byte[] bytes = IOUtils.toByteArray(theServletRequest.getInputStream());

    theServletResponse.setContentType("text/plain");
    theServletResponse.getWriter().write("It's a XES world");
    theServletResponse.getWriter().close();
  }

  @Override
  public Class<? extends IBaseResource> getResourceType() {
    return AuditEvent.class;
  }
}
